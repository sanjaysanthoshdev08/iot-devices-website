import {
  Component, EventEmitter, Input, OnChanges, OnDestroy,
  Output, PLATFORM_ID, inject, signal,
} from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription, timeout } from 'rxjs';

import {
  DeviceTelemetryService,
  TelemetryHistory,
} from '../../services/device-telemetry.service';

@Component({
  selector: 'app-telemetry-history',
  standalone: true,
  imports: [CommonModule],
  template: `
    <section class="history-panel" aria-labelledby="telemetry-history-title">
      <div class="history-heading">
        <div>
          <h3 id="telemetry-history-title">Reading history</h3>
          <p>Stored measurements. Refresh to retrieve the newest samples.</p>
        </div>
        <button type="button" class="history-refresh"
          [disabled]="loading()" (click)="loadPage(0)">
          Refresh
        </button>
      </div>

      <p *ngIf="loading()" role="status">Loading reading history...</p>

      <div *ngIf="error()" role="alert" class="history-error">
        <p>{{ error() }}</p>
        <button *ngIf="!accessBlocked()" type="button"
          (click)="loadPage(requestedPage())">Try again</button>
      </div>

      <ng-container *ngIf="history() as page">
        <p *ngIf="page.content.length === 0" role="status">
          No stored readings on this page.
        </p>

        <div class="history-scroll" tabindex="0"
          role="region" aria-label="Stored telemetry measurements"
          *ngIf="page.content.length > 0">
          <table>
            <caption>Stored telemetry samples</caption>
            <thead>
              <tr>
                <th scope="col">Received</th>
                <th scope="col">Mode</th>
                <th scope="col">Battery V</th>
                <th scope="col">Battery A</th>
                <th scope="col">Charge %</th>
                <th scope="col">Grid V</th>
                <th scope="col">Grid Hz</th>
                <th scope="col">Input W</th>
                <th scope="col">Load V</th>
                <th scope="col">Load A</th>
                <th scope="col">Output W</th>
                <th scope="col">Load %</th>
                <th scope="col">Source</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let row of page.content">
                <td>{{ row.receivedAt | date:'medium' }}</td>
                <td>{{ row.operatingMode }}</td>
                <td>{{ value(row.battery.voltageV) }}</td>
                <td>{{ value(row.battery.currentA) }}</td>
                <td>{{ value(row.battery.socPercent) }}</td>
                <td>{{ value(row.grid.voltageV) }}</td>
                <td>{{ value(row.grid.frequencyHz) }}</td>
                <td>{{ value(row.grid.inputPowerW) }}</td>
                <td>{{ value(row.load.voltageV) }}</td>
                <td>{{ value(row.load.currentA) }}</td>
                <td>{{ value(row.load.outputPowerW) }}</td>
                <td>{{ value(row.load.loadPercent) }}</td>
                <td>{{ row.simulated ? 'Simulated data' : 'Device readings' }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="history-pagination">
          <button type="button" class="history-previous"
            [disabled]="loading() || page.first || page.number === 0"
            (click)="loadPage(page.number - 1)">
            Previous
          </button>
          <span>
            {{ page.totalPages > 0 ? 'Page ' + (page.number + 1) + ' of ' + page.totalPages : 'No pages' }}
            · {{ page.totalElements }} samples
          </span>
          <button type="button" class="history-next"
            [disabled]="loading() || page.last"
            (click)="loadPage(page.number + 1)">
            Next
          </button>
        </div>
      </ng-container>

      <p class="history-note">
        — means unavailable. Times show backend reception.
        Simulation status is recorded separately for each sample.
      </p>
    </section>
  `,
  styles: [`
    :host { display:block; margin-top:24px; }
    .history-panel {
      background:#141820; border:1px solid #303743;
      border-radius:16px; padding:20px; color:#e5e7eb;
    }
    .history-heading {
      display:flex; justify-content:space-between;
      align-items:center; gap:16px; flex-wrap:wrap;
    }
    h3 { margin:0; font-size:18px; font-weight:600; }
    p { font-size:13px; line-height:1.6; color:#aeb8c7; }
    button {
      padding:8px 14px; border:1px solid #475569;
      background:#202938; color:#edf2f7;
      border-radius:8px; cursor:pointer; font:inherit; font-size:13px;
    }
    button:disabled { opacity:.45; cursor:default; }
    button:focus-visible, .history-scroll:focus-visible {
      outline:2px solid #60a5fa; outline-offset:3px;
    }
    .history-scroll { overflow-x:auto; margin-top:12px; }
    table { width:100%; border-collapse:collapse; font-size:12px; }
    caption { text-align:left; color:#aeb8c7; padding:8px 0; }
    th, td {
      text-align:left; padding:11px 12px;
      border-bottom:1px solid #303743; white-space:nowrap;
    }
    th { color:#b8c4d6; font-weight:500; }
    td { font-variant-numeric:tabular-nums; }
    .history-pagination {
      display:flex; align-items:center; justify-content:space-between;
      flex-wrap:wrap; gap:12px; margin-top:16px; font-size:13px;
    }
    .history-error p { color:#fca5a5; }
    .history-note { margin-bottom:0; }
  `],
})
export class TelemetryHistoryComponent implements OnChanges, OnDestroy {
  @Input({ required: true }) deviceId!: number;
  @Output() readonly accessDenied = new EventEmitter<number>();

  private readonly api = inject(DeviceTelemetryService);
  private readonly platformId = inject(PLATFORM_ID);
  private request: Subscription | null = null;
  private generation = 0;
  private destroyed = false;

  readonly history = signal<TelemetryHistory | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly accessBlocked = signal(false);
  readonly requestedPage = signal(0);

  ngOnChanges(): void {
    this.loadPage(0);
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    this.generation++;
    this.request?.unsubscribe();
    this.history.set(null);
  }

  value(value: number | null | undefined): string {
    return typeof value === 'number' && Number.isFinite(value)
      ? value.toLocaleString('en-IN', { maximumFractionDigits: 2 })
      : '—';
  }

  loadPage(page: number): void {
    const generation = ++this.generation;
    this.request?.unsubscribe();
    this.history.set(null);
    this.error.set(null);
    this.accessBlocked.set(false);
    this.loading.set(false);

    if (this.destroyed || !isPlatformBrowser(this.platformId)) return;

    if (!Number.isSafeInteger(this.deviceId) || this.deviceId <= 0
        || !Number.isSafeInteger(page) || page < 0) {
      this.error.set('Invalid history request.');
      return;
    }

    const deviceId = this.deviceId;
    this.requestedPage.set(page);
    this.loading.set(true);

    const current = () => !this.destroyed
      && generation === this.generation && deviceId === this.deviceId;

    this.request = this.api.history(deviceId, page, 10).pipe(
      timeout({ first: 10000 })
    ).subscribe({
      next: response => {
        if (!current()) return;
        const data = response.data;

        if (!response.success || !data || !Array.isArray(data.content)
            || data.number !== page || data.size !== 10
            || !Number.isSafeInteger(data.totalElements)
            || data.totalElements < 0
            || !Number.isSafeInteger(data.totalPages)
            || data.totalPages < 0
            || !data.content.every(row =>
              row && row.deviceId === deviceId
              && Number.isFinite(Date.parse(row.receivedAt))
              && row.battery && row.grid && row.load)) {
          this.loading.set(false);
          this.error.set('Unable to display the history response.');
          return;
        }

        this.history.set(data);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        if (!current()) return;
        this.history.set(null);
        this.loading.set(false);

        const status = error instanceof HttpErrorResponse ? error.status : 0;
        if ([401, 403, 404].includes(status)) {
          this.accessBlocked.set(true);
          this.error.set('History access is no longer available.');
          this.accessDenied.emit(status);
        } else {
          this.error.set('Unable to load history. Please try again.');
        }
      },
    });
  }
}