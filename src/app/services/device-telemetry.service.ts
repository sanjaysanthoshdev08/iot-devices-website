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

  private validDeviceId(value: number): boolean {
    return Number.isSafeInteger(value) && value > 0;
  }
}