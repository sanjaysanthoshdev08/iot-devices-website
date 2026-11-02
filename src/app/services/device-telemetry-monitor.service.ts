import { Injectable, OnDestroy, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription, timeout } from 'rxjs';

import {
  DeviceTelemetryService,
  TelemetryLatest,
} from './device-telemetry.service';

@Injectable({ providedIn: 'root' })
export class DeviceTelemetryMonitor implements OnDestroy {
  private readonly api = inject(DeviceTelemetryService);
  private readonly responseState = signal<TelemetryLatest | null>(null);
  private readonly clock = signal(0);

  private deviceId: number | null = null;
  private generation = 0;
  private receivedLocallyAt = 0;
  private request: Subscription | null = null;
  private pollTimer: ReturnType<typeof setTimeout> | null = null;
  private ageTimer: ReturnType<typeof setInterval> | null = null;

  readonly data = this.responseState.asReadonly();
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly fatalStatus = signal<number | null>(null);
  readonly reading = computed(() => this.data()?.reading ?? null);

  readonly connectionStatus = computed(() => {
    if (this.error()) return 'ERROR';
    const data = this.data();

    if (!data) return this.loading() ? 'LOADING' : 'NO_DATA';
    if (data.status === 'NO_DATA' || !data.reading) return 'NO_DATA';
    if (data.status !== 'ONLINE') return 'OFFLINE';

    // Advance the backend's freshness calculation between responses.
    const initialAge =
      Date.parse(data.serverTime) - Date.parse(data.reading.receivedAt);
    const elapsed = Math.max(0, this.clock() - this.receivedLocallyAt);
    const age = initialAge + elapsed;

    return age >= 0 && age <= data.staleAfterSeconds * 1000
      ? 'ONLINE'
      : 'OFFLINE';
  });

  start(deviceId: number): void {
    if (this.deviceId === deviceId) return;
    this.stop();

    if (!Number.isSafeInteger(deviceId) || deviceId <= 0) {
      this.error.set('Invalid device ID.');
      return;
    }

    this.deviceId = deviceId;
    this.clock.set(Date.now());
    this.ageTimer = setInterval(() => this.clock.set(Date.now()), 1000);
    this.fetch(this.generation);
  }

  stop(): void {
    this.cancelWork();
    this.responseState.set(null);
    this.loading.set(false);
    this.error.set(null);
    this.fatalStatus.set(null);
  }

  ngOnDestroy(): void {
    this.stop();
  }

  private cancelWork(): void {
    this.generation++;
    this.deviceId = null;

    if (this.pollTimer !== null) clearTimeout(this.pollTimer);
    if (this.ageTimer !== null) clearInterval(this.ageTimer);

    this.pollTimer = null;
    this.ageTimer = null;
    this.request?.unsubscribe();
    this.request = null;
  }

  private current(generation: number): boolean {
    return generation === this.generation && this.deviceId !== null;
  }

  private schedule(generation: number): void {
    if (!this.current(generation)) return;
    this.pollTimer = setTimeout(() => {
      this.pollTimer = null;
      this.fetch(generation);
    }, 5000);
  }

  private fetch(generation: number): void {
    if (!this.current(generation)) return;
    const deviceId = this.deviceId!;
    this.loading.set(true);

    this.request = this.api.latest(deviceId).pipe(
      timeout({ first: 10000 })
    ).subscribe({
      next: response => {
        if (!this.current(generation)) return;

        const data = response.data;
        if (!response.success || !data
            || !['NO_DATA', 'ONLINE', 'OFFLINE'].includes(data.status)
            || !Number.isFinite(Date.parse(data.serverTime))
            || !Number.isFinite(data.staleAfterSeconds)
            || data.staleAfterSeconds <= 0
            || (data.status === 'NO_DATA' && data.reading !== null)
            || (data.status !== 'NO_DATA' && !data.reading)
            || (data.reading && (
              data.reading.deviceId !== deviceId
              || !Number.isFinite(Date.parse(data.reading.receivedAt))
            ))) {
          this.failed(new Error('Invalid telemetry response.'), generation);
          return;
        }

        this.receivedLocallyAt = Date.now();
        this.clock.set(this.receivedLocallyAt);
        this.responseState.set(data);
        this.loading.set(false);
        this.error.set(null);
        this.schedule(generation);
      },
      error: error => this.failed(error, generation),
    });
  }

  private failed(error: unknown, generation: number): void {
    if (!this.current(generation)) return;

    this.responseState.set(null);
    this.loading.set(false);

    const status = error instanceof HttpErrorResponse ? error.status : 0;

    if ([401, 403, 404].includes(status)) {
      this.cancelWork();
      this.fatalStatus.set(status);
      this.error.set(
        status === 404
          ? 'Device telemetry is unavailable.'
          : 'Telemetry access is no longer available.'
      );
      return;
    }

    this.error.set('Unable to retrieve telemetry. Retrying automatically.');
    this.schedule(generation);
  }
}