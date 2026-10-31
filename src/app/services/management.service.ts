import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse } from './auth.service';
import { DeviceResponse } from './device-access.service';

// ─── Management & Admin Request DTOs ──────────────────────────────────────────

export interface RegisterDeviceManagementRequest {
  deviceCode: string;
  serialNumber: string;
  systemName: string;
  model?: string | null;
  branchId?: number | null;
}

export interface RecordDevicePurchaseRequest {
  deviceId: number;
  customerUserId: number;
  purchaseReference: string;
}

export interface CreateDistributorAdminRequest {
  name: string;
  email: string;
  password: string;
  confirmPassword: string;
}

export interface CreateDistributorRequest {
  distributorCode: string;
  name: string;
  administratorUserId: number;
  branchCode: string;
  branchName: string;
  location: string;
}

// ─── Management & Admin Response DTOs ─────────────────────────────────────────

export interface DevicePurchaseResponse {
  id: number;
  deviceId: number;
  deviceCode: string;
  customerUserId: number;
  recordedByUserId: number;
  purchaseReference: string;
  status: string;
  claimedAt: string | null;
  createdAt: string;
}

export interface DistributorAdminResponse {
  id: number;
  name: string;
  email: string;
  role: string;
  status: string;
  createdAt: string;
}

export interface DistributorResponse {
  id: number;
  distributorCode: string;
  name: string;
  administratorUserId: number;
  status: string;
  branchId: number;
  branchCode: string;
  branchName: string;
  location: string;
  branchStatus: string;
}

export interface AuthorizeCustomerRequest {
  customerEmail: string;
}

export interface DirectoryAuthorizedCustomer {
  userId: number;
  email: string;
  isPrimaryOwner: boolean;
}

export interface DirectoryDevice {
  id: number;
  deviceCode: string;
  serialNumber: string;
  model: string;
  status: string;
  customerEmails?: string[];
  authorizedCustomers?: DirectoryAuthorizedCustomer[];
}

export interface DirectoryBranch {
  id: number;
  branchCode: string;
  name: string;
  location: string;
  status: string;
  devices: DirectoryDevice[];
}

export interface DirectoryDistributorAdmin {
  id: number;
  name: string;
  email: string;
  role: string;
  status: string;
}

export interface DirectoryDistributor {
  id: number;
  distributorCode: string;
  name: string;
  status: string;
  administrator: DirectoryDistributorAdmin | null;
  branches: DirectoryBranch[];
}

// ─── Endpoints ────────────────────────────────────────────────────────────────

const REGISTER_DEVICE_URL = 'http://localhost:8080/api/v1/management/devices';
const RECORD_PURCHASE_URL = 'http://localhost:8080/api/v1/management/device-purchases';
const CREATE_DIST_ADMIN_URL = 'http://localhost:8080/api/v1/admin/distributor-admins';
const CREATE_DISTRIBUTOR_URL = 'http://localhost:8080/api/v1/admin/distributors';
const DISTRIBUTOR_DIRECTORY_URL = 'http://localhost:8080/api/v1/admin/distributors';

@Injectable({ providedIn: 'root' })
export class ManagementService {
  private readonly http = inject(HttpClient);

  /**
   * Register a new hardware device.
   * Accessible by: HYKON_ADMIN, DISTRIBUTOR_ADMIN (verified session)
   * POST /api/v1/management/devices
   */
  registerDevice(
    request: RegisterDeviceManagementRequest
  ): Observable<ApiResponse<DeviceResponse>> {
    return this.http.post<ApiResponse<DeviceResponse>>(REGISTER_DEVICE_URL, request);
  }

  /**
   * Record customer purchase eligibility for a registered device.
   * Accessible by: HYKON_ADMIN, DISTRIBUTOR_ADMIN (verified session)
   * POST /api/v1/management/device-purchases
   */
  recordDevicePurchase(
    request: RecordDevicePurchaseRequest
  ): Observable<ApiResponse<DevicePurchaseResponse>> {
    return this.http.post<ApiResponse<DevicePurchaseResponse>>(RECORD_PURCHASE_URL, request);
  }

  /**
   * Provision a distributor administrator account.
   * Accessible by: HYKON_ADMIN only
   * POST /api/v1/admin/distributor-admins
   */
  createDistributorAdmin(
    request: CreateDistributorAdminRequest
  ): Observable<ApiResponse<DistributorAdminResponse>> {
    return this.http.post<ApiResponse<DistributorAdminResponse>>(CREATE_DIST_ADMIN_URL, request);
  }

  /**
   * Provision a distributor organization and its initial branch, linked to an administrator account.
   * Accessible by: HYKON_ADMIN only
   * POST /api/v1/admin/distributors
   */
  createDistributor(
    request: CreateDistributorRequest
  ): Observable<ApiResponse<DistributorResponse>> {
    return this.http.post<ApiResponse<DistributorResponse>>(CREATE_DISTRIBUTOR_URL, request);
  }

  /**
   * Retrieve the complete distributor hierarchy directory.
   * Accessible by: HYKON_ADMIN only
   * GET /api/v1/admin/distributors
   * Returns ApiResponse whose data field is a JSON array of DirectoryDistributor objects.
   */
  getDistributorDirectory(): Observable<ApiResponse<DirectoryDistributor[]>> {
    return this.http.get<ApiResponse<DirectoryDistributor[]>>(DISTRIBUTOR_DIRECTORY_URL);
  }

  /**
   * Authorize an additional customer for a device (up to 3 total).
   * Accessible by: HYKON_ADMIN, DISTRIBUTOR_ADMIN (verified session)
   * POST /api/v1/management/devices/{deviceId}/customers
   */
  authorizeDeviceCustomer(
    deviceId: number | string,
    customerEmail: string
  ): Observable<ApiResponse<DeviceResponse>> {
    return this.http.post<ApiResponse<DeviceResponse>>(
      `${REGISTER_DEVICE_URL}/${deviceId}/customers`,
      { customerEmail }
    );
  }

  /**
   * Remove an authorized customer from a device.
   * Primary owner cannot be removed.
   * Accessible by: HYKON_ADMIN, DISTRIBUTOR_ADMIN (verified session)
   * DELETE /api/v1/management/devices/{deviceId}/customers/{customerUserId}
   */
  removeDeviceCustomer(
    deviceId: number | string,
    customerUserId: number | string
  ): Observable<ApiResponse<DeviceResponse>> {
    return this.http.delete<ApiResponse<DeviceResponse>>(
      `${REGISTER_DEVICE_URL}/${deviceId}/customers/${customerUserId}`
    );
  }
}

