import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { ApiResponse } from './auth.service';

export type OperatingMode =
  | 'LINE' | 'BATTERY' | 'BYPASS'
  | 'STANDBY' | 'FAULT' | 'UNKNOWN';

export interface TelemetryReading {
  readingId: number;
  deviceId: number;
  schemaVersion: number;
  bootId: string;
  sequence: number;
  measuredAt: string | null;
  receivedAt: string;
  uptimeMs: number;
  simulated: boolean;
  operatingMode: OperatingMode;
  battery: {
    voltageV: number | null;
    currentA: number | null;
    socPercent: number | null;
  };
  grid: {
    present: boolean | null;
    voltageV: number | null;
    frequencyHz: number | null;
    inputPowerW: number | null;
  };
  load: {
    voltageV: number | null;
    currentA: number | null;
    outputPowerW: number | null;
    loadPercent: number | null;
  };
}

export interface TelemetryLatest {
  status: 'NO_DATA' | 'ONLINE' | 'OFFLINE';
  serverTime: string;
  staleAfterSeconds: number;
  reading: TelemetryReading | null;
}

export interface TelemetryHistory {
  content: TelemetryReading[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface TelemetryExtreme {
  value: number;
  receivedAt: string;
  measuredAt: string | null;
  readingId: number;
}

export interface TelemetryMetricSummary {
  metric: string;
  label: string;
  unit: string;
  validSampleCount: number;
  minimum: TelemetryExtreme | null;
  maximum: TelemetryExtreme | null;
}

export interface TelemetryAnalysis {
  deviceId: number;
  from: string;
  to: string;
  simulated: boolean;
  timeBasis: 'RECEIVED_AT';
  sampleCount: number;
  metrics: TelemetryMetricSummary[];
  history: TelemetryHistory;
}

const DEVICES_URL = 'http://localhost:8080/api/v1/devices';

@Injectable({ providedIn: 'root' })
export class DeviceTelemetryService {
  private readonly http = inject(HttpClient);

  latest(deviceId: number): Observable<ApiResponse<TelemetryLatest>> {
    if (!this.validDeviceId(deviceId)) {
      return throwError(() => new Error('Invalid device ID.'));
    }

    return this.http.get<ApiResponse<TelemetryLatest>>(
      `${DEVICES_URL}/${deviceId}/telemetry/latest`
    );
  }

  history(
    deviceId: number,
    page = 0,
    size = 20
  ): Observable<ApiResponse<TelemetryHistory>> {
    if (!this.validDeviceId(deviceId)
        || !Number.isSafeInteger(page) || page < 0
        || !Number.isSafeInteger(size) || size < 1 || size > 100) {
      return throwError(() => new Error('Invalid telemetry page request.'));
    }

    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    return this.http.get<ApiResponse<TelemetryHistory>>(
      `${DEVICES_URL}/${deviceId}/telemetry/history`,
      { params }
    );
  }

  analysis(
    deviceId: number,
    from: string,
    to: string,
    simulated: boolean,
    page = 0,
    size = 10
  ): Observable<ApiResponse<TelemetryAnalysis>> {
    const start = Date.parse(from);
    const end = Date.parse(to);

    if (!this.validDeviceId(deviceId)
        || !Number.isFinite(start) || !Number.isFinite(end)
        || start >= end || end - start > 366 * 86400000
        || typeof simulated !== 'boolean'
        || !Number.isSafeInteger(page) || page < 0
        || !Number.isSafeInteger(size) || size < 1 || size > 100) {
      return throwError(() => new Error('Invalid analysis request.'));
    }

    const params = new HttpParams()
      .set('from', new Date(start).toISOString())
      .set('to', new Date(end).toISOString())
      .set('simulated', String(simulated))
      .set('page', String(page))
      .set('size', String(size));

    return this.http.get<ApiResponse<TelemetryAnalysis>>(
      `${DEVICES_URL}/${deviceId}/telemetry/analysis`,
      { params }
    );
  }

  private validDeviceId(value: number): boolean {
    return Number.isSafeInteger(value) && value > 0;
  }
}