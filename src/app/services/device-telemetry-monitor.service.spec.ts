import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject } from 'rxjs';
import {
  afterEach, beforeEach, describe, expect, it, vi,
} from 'vitest';

import { DeviceTelemetryService } from './device-telemetry.service';
import { DeviceTelemetryMonitor } from './device-telemetry-monitor.service';

describe('DeviceTelemetryMonitor', () => {
  let monitor: DeviceTelemetryMonitor;
  let requests: Subject<any>[];
  let latest: ReturnType<typeof vi.fn>;

  function sample(deviceId = 1, ageMs = 0) {
    return {
      success: true,
      message: 'OK',
      data: {
        status: 'ONLINE',
        serverTime: new Date(Date.now()).toISOString(),
        staleAfterSeconds: 30,
        reading: {
          readingId: 10,
          deviceId,
          schemaVersion: 1,
          bootId: 'd40189cd-e749-433a-881d-362d78196750',
          sequence: 1,
          measuredAt: null,
          receivedAt: new Date(Date.now() - ageMs).toISOString(),
          uptimeMs: 5000,
          simulated: true,
          operatingMode: 'LINE',
          battery: { voltageV: 51.2, currentA: 2, socPercent: 80 },
          grid: {
            present: true, voltageV: 230,
            frequencyHz: 50, inputPowerW: 750,
          },
          load: {
            voltageV: 230, currentA: null,
            outputPowerW: 600, loadPercent: null,
          },
        },
      },
    };
  }

  function respond(index: number, value = sample()) {
    requests[index].next(value);
    requests[index].complete();
  }

  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-11-02T10:00:00Z'));
    requests = [];

    latest = vi.fn(() => {
      const request = new Subject<any>();
      requests.push(request);
      return request.asObservable();
    });

    TestBed.configureTestingModule({
      providers: [
        DeviceTelemetryMonitor,
        { provide: DeviceTelemetryService, useValue: { latest } },
      ],
    });

    monitor = TestBed.inject(DeviceTelemetryMonitor);
  });

  afterEach(() => {
    monitor.stop();
    vi.useRealTimers();
  });

  it('loads the selected device and preserves missing measurements', () => {
    monitor.start(1);
    expect(latest).toHaveBeenCalledWith(1);
    expect(monitor.loading()).toBe(true);

    respond(0);

    expect(monitor.connectionStatus()).toBe('ONLINE');
    expect(monitor.reading()?.simulated).toBe(true);
    expect(monitor.reading()?.load.currentA).toBeNull();
    expect(monitor.loading()).toBe(false);
  });

  it('waits for a response before scheduling another request', () => {
    monitor.start(1);
    vi.advanceTimersByTime(5000);
    expect(latest).toHaveBeenCalledTimes(1);

    respond(0);
    vi.advanceTimersByTime(4999);
    expect(latest).toHaveBeenCalledTimes(1);

    vi.advanceTimersByTime(1);
    expect(latest).toHaveBeenCalledTimes(2);
  });

  it('cancels the old request and clears readings when switching devices', () => {
    monitor.start(1);
    const oldRequest = requests[0];

    monitor.start(2);
    expect(oldRequest.observed).toBe(false);
    expect(monitor.reading()).toBeNull();

    oldRequest.next(sample(1));
    expect(monitor.reading()).toBeNull();

    respond(1, sample(2));
    expect(monitor.reading()?.deviceId).toBe(2);
  });

  it('stops requests and clears readings when the view closes', () => {
    monitor.start(1);
    respond(0);
    vi.advanceTimersByTime(5000);
    const pending = requests[1];

    monitor.stop();

    expect(pending.observed).toBe(false);
    expect(monitor.reading()).toBeNull();
    expect(monitor.loading()).toBe(false);

    pending.next(sample());
    vi.advanceTimersByTime(30000);
    expect(latest).toHaveBeenCalledTimes(2);
    expect(monitor.reading()).toBeNull();
  });

  it.each([401, 403, 404])(
    'clears protected readings and stops polling after HTTP %s',
    status => {
      monitor.start(1);
      respond(0);
      vi.advanceTimersByTime(5000);

      requests[1].error(new HttpErrorResponse({ status }));

      expect(monitor.reading()).toBeNull();
      expect(monitor.fatalStatus()).toBe(status);
      vi.advanceTimersByTime(30000);
      expect(latest).toHaveBeenCalledTimes(2);
    }
  );

  it('recovers after a temporary backend failure without inventing readings', () => {
    monitor.start(1);
    requests[0].error(new HttpErrorResponse({ status: 500 }));

    expect(monitor.connectionStatus()).toBe('ERROR');
    expect(monitor.reading()).toBeNull();

    vi.advanceTimersByTime(5000);
    respond(1);

    expect(monitor.error()).toBeNull();
    expect(monitor.connectionStatus()).toBe('ONLINE');
  });

  it('marks an aging reading offline before the next poll', () => {
    monitor.start(1);
    respond(0, sample(1, 29500));

    expect(monitor.connectionStatus()).toBe('ONLINE');
    vi.advanceTimersByTime(1000);
    expect(monitor.connectionStatus()).toBe('OFFLINE');
  });

  it('rejects a response containing readings for a different device', () => {
    monitor.start(1);
    respond(0, sample(2));

    expect(monitor.reading()).toBeNull();
    expect(monitor.connectionStatus()).toBe('ERROR');
  });
});