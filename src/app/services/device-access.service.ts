
import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ApiResponse } from './auth.service';

// ─── Request DTOs ─────────────────────────────────────────────────────────────
// Mirrors backend ClaimDeviceRequest.java

export interface ClaimDeviceRequest {
  deviceCode: string;
  serialNumber: string;
}

// Mirrors backend DistributorAccessRequest.java

export interface DistributorAccessRequest {
  distributorCode: string;
  branchCode: string;
  location: string;
}

// ─── Response DTOs ────────────────────────────────────────────────────────────
// Mirrors backend DeviceResponse.java
// Nullable fields use `| null` to match the Java record where branch / owner
// may be absent.  `createdAt` is serialised as an ISO-8601 string by Jackson.

export interface DeviceResponse {
  id: number;
  deviceCode: string;
  serialNumber: string;
  systemName: string;
  model: string;
  branchId: number | null;
  distributorId: number | null;
  ownerUserId: number | null;
  status: string;
  createdAt: string;
}

// Mirrors backend DistributorAccessResponse.java

export interface DistributorAccessResponse {
  distributorId: number;
  distributorCode: string;
  distributorName: string;
  branchId: number;
  branchCode: string;
  branchName: string;
  location: string;
}

// Mirrors backend DevicePageResponse.java (Spring Page wrapper)

export interface DevicePageResponse {
  content: DeviceResponse[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

// ─── API base URLs ───────────────────────────────────────────────────────────

const DEVICE_CLAIM_URL = 'http://localhost:8080/api/v1/user/devices/claim';
const DISTRIBUTOR_VERIFY_URL = 'http://localhost:8080/api/v1/distributor/access/verify';
const DEVICES_URL = 'http://localhost:8080/api/v1/devices';

// ─── Service ─────────────────────────────────────────────────────────────────

@Injectable({ providedIn: 'root' })
export class DeviceAccessService {
  private readonly http = inject(HttpClient);

  /**
   * Verify (claim) a device for the current USER session.
   *
   * POST /api/v1/user/devices/claim
   *
   * The existing auth interceptor attaches the access token automatically.
   * HTTP errors (400 validation, 401 unauthenticated, 403 access denied,
   * 404 device not found, 429 rate-limited) propagate unchanged.
   */
  verifyDevice(
    request: ClaimDeviceRequest
  ): Observable<ApiResponse<DeviceResponse>> {
    return this.http.post<ApiResponse<DeviceResponse>>(
      DEVICE_CLAIM_URL,
      request
    );
  }

  /**
   * Verify distributor access for the current DISTRIBUTOR_ADMIN session.
   *
   * POST /api/v1/distributor/access/verify
   */
  verifyDistributor(
    request: DistributorAccessRequest
  ): Observable<ApiResponse<DistributorAccessResponse>> {
    return this.http.post<ApiResponse<DistributorAccessResponse>>(
      DISTRIBUTOR_VERIFY_URL,
      request
    );
  }

  /**
   * List devices visible to the authenticated user.
   *
   * GET /api/v1/devices?page={page}&size={size}
   *
   * Requires a verified session (SessionAccessPolicyService enforces this
   * on the backend).
   */
  listDevices(
    page: number = 0,
    size: number = 20
  ): Observable<ApiResponse<DevicePageResponse>> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    return this.http.get<ApiResponse<DevicePageResponse>>(
      DEVICES_URL,
      { params }
    );
  }

  /**
   * Retrieve a single device by its numeric ID.
   *
   * GET /api/v1/devices/{deviceId}
   */
  getDevice(
    deviceId: number
  ): Observable<ApiResponse<DeviceResponse>> {
    return this.http.get<ApiResponse<DeviceResponse>>(
      `${DEVICES_URL}/${deviceId}`
    );
  }
}
