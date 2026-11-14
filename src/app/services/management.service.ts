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
  distributorCode?: string;
  branchCode?: string;
  distributorId?: number | null;
}

export interface RegistrationBranchOption {
  id: number;
  name: string;
  branchCode: string;
  location: string;
  address?: string | null;
  pincode?: string | null;
}

export interface RegistrationDistributorOption {
  id: number;
  name: string;
  distributorCode: string;
  state?: string | null;
  district?: string | null;
  branches: RegistrationBranchOption[];
}

export interface RegistrationOptions {
  distributors: RegistrationDistributorOption[];
}

export interface RecordDevicePurchaseRequest {
  deviceId: number;
  customerUserId?: number;
  customerEmail?: string;
  purchaseReference: string;
}

export interface CreateBranchWithAdminRequest {
  branchCode: string;
  branchName: string;
  location: string;
  address?: string;
  distributorCode?: string;
  adminName: string;
  adminEmail: string;
  adminPassword: string;
  confirmPassword: string;
  pincode?: string;
}

export interface CreateDistributorAdminRequest {
  name: string;
  email: string;
  password: string;
  confirmPassword: string;
}

export interface CreateDistributorWithLoginRequest {
  distributorCode: string;
  name: string;
  state: string;
  district: string;
  email: string;
  password: string;
  confirmPassword: string;
}

export interface DistributorAdminOption {
  id: number;
  name: string;
  email: string;
}

export interface CreateDistributorRequest {
  distributorCode: string;
  name: string;
  administratorUserId: number;
  branchCode?: string;
  branchName?: string;
  location?: string;
  address?: string;
  state?: string;
  district?: string;
  pincode?: string;
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
  systemName?: string;
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
  pincode?: string | null;
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
  state?: string | null;
  district?: string | null;
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
   * Create a branch together with its dedicated BRANCH_ADMIN login.
   * HYKON_ADMIN: pass the distributorId.  DISTRIBUTOR_ADMIN: pass null (own distributor).
   * POST /api/v1/admin/distributors/{id}/branch-with-admin  |  POST /api/v1/distributor/branches
   */
  createBranchWithAdmin(
    distributorId: number | null,
    request: CreateBranchWithAdminRequest
  ): Observable<ApiResponse<DirectoryBranch>> {
    const url = distributorId === null
      ? 'http://localhost:8080/api/v1/distributor/branches'
      : `${DISTRIBUTOR_DIRECTORY_URL}/${distributorId}/branch-with-admin`;
    return this.http.post<ApiResponse<DirectoryBranch>>(url, request);
  }

  /**
   * Read-only directory of every registered distributor (Distributor Admin's Accounts page).
   * GET /api/v1/distributor/directory
   */
  getDistributorDirectoryForViewer(): Observable<ApiResponse<DirectoryDistributor[]>> {
    return this.http.get<ApiResponse<DirectoryDistributor[]>>('http://localhost:8080/api/v1/distributor/directory');
  }

  /**
   * Overseeing Distributor Admin: create a distributor organization together with its own login.
   * POST /api/v1/distributor/distributors
   */
  createDistributorWithLogin(
    request: CreateDistributorWithLoginRequest
  ): Observable<ApiResponse<DistributorResponse>> {
    return this.http.post<ApiResponse<DistributorResponse>>(
      'http://localhost:8080/api/v1/distributor/distributors', request);
  }

  /**
   * Distributor admin logins that are not yet assigned to a distributor organization.
   * GET /api/v1/admin/distributor-admins/available  (HYKON_ADMIN only)
   */
  listAvailableDistributorAdmins(): Observable<ApiResponse<DistributorAdminOption[]>> {
    return this.http.get<ApiResponse<DistributorAdminOption[]>>(`${CREATE_DIST_ADMIN_URL}/available`);
  }

  /**
   * Distributors and branches the signed-in admin may pick from (searchable dropdowns).
   * GET /api/v1/management/devices/options
   */
  getRegistrationOptions(): Observable<ApiResponse<RegistrationOptions>> {
    return this.http.get<ApiResponse<RegistrationOptions>>(`${REGISTER_DEVICE_URL}/options`);
  }

  /**
   * HYKON_ADMIN: create a branch + branch admin, identifying the distributor by its code.
   * POST /api/v1/admin/branches
   */
  createBranchByCode(
    request: CreateBranchWithAdminRequest
  ): Observable<ApiResponse<DirectoryBranch>> {
    return this.http.post<ApiResponse<DirectoryBranch>>('http://localhost:8080/api/v1/admin/branches', request);
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

