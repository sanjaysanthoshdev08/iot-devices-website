import { TestBed } from '@angular/core/testing';
import {
  HttpErrorResponse,
  provideHttpClient,
} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import {
  DeviceTelemetryService,
  TelemetryReading,
} from './device-telemetry.service';

describe('DeviceTelemetryService', () => {
  let service: DeviceTelemetryService;
  let http: HttpTestingController;

  const base = 'http://localhost:8080/api/v1/devices';

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        DeviceTelemetryService,
      ],
    });

    service = TestBed.inject(DeviceTelemetryService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('keeps NO_DATA empty rather than inventing measurements', () => {
    let received = false;

    service.latest(1).subscribe(response => {
      received = true;
      expect(response.data.status).toBe('NO_DATA');
      expect(response.data.reading).toBeNull();
      expect(response.data.staleAfterSeconds).toBe(30);
    });

    const request = http.expectOne(`${base}/1/telemetry/latest`);
    expect(request.request.method).toBe('GET');

    request.flush({
      success: true,
      message: 'OK',
      data: {
        status: 'NO_DATA',
        serverTime: '2026-11-02T10:00:00Z',
        staleAfterSeconds: 30,
        reading: null,
      },
    });

    expect(received).toBe(true);
  });

  it('preserves signed values, zero, missing values and simulation status', () => {
    const reading: TelemetryReading = {
      readingId: 10,
      deviceId: 1,
      schemaVersion: 1,
      bootId: 'd40189cd-e749-433a-881d-362d78196750',
      sequence: 2,
      measuredAt: null,
      receivedAt: '2026-11-02T10:00:00Z',
      uptimeMs: 10000,
      simulated: true,
      operatingMode: 'BATTERY',
      battery: { voltageV: 48, currentA: -10, socPercent: 0 },
      grid: {
        present: false,
        voltageV: 0,
        frequencyHz: null,
        inputPowerW: -100,
      },
      load: {
        voltageV: 230,
        currentA: null,
        outputPowerW: 1300,
        loadPercent: 130,
      },
    };

    let received: TelemetryReading | null = null;
    service.latest(1).subscribe(response => {
      received = response.data.reading;
    });

    http.expectOne(`${base}/1/telemetry/latest`).flush({
      success: true,
      message: 'OK',
      data: {
        status: 'ONLINE',
        serverTime: '2026-11-02T10:00:01Z',
        staleAfterSeconds: 30,
        reading,
      },
    });

    expect(received).toEqual(reading);
  });

  it.each([401, 403, 404, 500])(
    'propagates HTTP %s instead of returning fake readings',
    status => {
      let actualStatus: number | undefined;
      let emitted = false;

      service.latest(1).subscribe({
        next: () => { emitted = true; },
        error: (error: HttpErrorResponse) => {
          actualStatus = error.status;
        },
      });

      http.expectOne(`${base}/1/telemetry/latest`).flush(
        { success: false },
        { status, statusText: 'Request failed' }
      );

      expect(emitted).toBe(false);
      expect(actualStatus).toBe(status);
    }
  );

  it('requests the selected device history with explicit pagination', () => {
    let received = false;
    service.history(7, 2, 10).subscribe(response => {
      received = true;
      expect(response.data.number).toBe(2);
      expect(response.data.content).toEqual([]);
    });

    const request = http.expectOne(req =>
      req.url === `${base}/7/telemetry/history`
    );

    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('size')).toBe('10');

    request.flush({
      success: true,
      message: 'OK',
      data: {
        content: [],
        number: 2,
        size: 10,
        totalElements: 0,
        totalPages: 0,
        first: false,
        last: true,
      },
    });

    expect(received).toBe(true);
  });

  it('rejects invalid IDs and page bounds without making HTTP requests', () => {
    let errors = 0;
    const rejected = () => { errors++; };

    service.latest(0).subscribe({ error: rejected });
    service.latest(Number.NaN).subscribe({ error: rejected });
    service.history(1, -1, 20).subscribe({ error: rejected });
    service.history(1, 0, 101).subscribe({ error: rejected });

    expect(errors).toBe(4);
    http.expectNone(req => req.url.startsWith(base));
  });
});