import { Injectable, computed, signal, inject } from '@angular/core';
import { DeviceAccessService, DeviceResponse, DevicePageResponse } from './device-access.service';
import { Observable } from 'rxjs';
import { ApiResponse } from './auth.service';

export type SystemStatus = 'online' | 'warning' | 'offline';

@Injectable({
  providedIn: 'root'
})
export class SystemRecordsService {
  private readonly deviceAccessService = inject(DeviceAccessService);

  // Core system records state - strictly initialized as empty (no eager loading, no localStorage)
  readonly devices = signal<DeviceResponse[]>([]);
  readonly currentPage = signal<number>(0);
  readonly pageSize = signal<number>(10);
  readonly totalElements = signal<number>(0);
  readonly totalPages = signal<number>(0);
  readonly isFirst = signal<boolean>(true);
  readonly isLast = signal<boolean>(true);

  readonly isLoading = signal<boolean>(false);
  readonly errorMessage = signal<string>('');

  // Filtering & Local Search state ("Search this page")
  readonly statusFilter = signal<'all' | SystemStatus>('all');
  readonly searchQuery = signal<string>('');

  // Summary Metrics
  // "All" reflects backend totalElements count.
  // Real-time telemetry (Online/Warning/Offline) is unavailable until live telemetry streaming exists;
  // displayed as "—", not fabricated zero or fake active counts.
  readonly totalCount = computed(() => this.totalElements());
  readonly onlineCount = computed(() => '—');
  readonly warningCount = computed(() => '—');
  readonly offlineCount = computed(() => '—');

  // Filtered dataset for the table view on the current page
  readonly filteredSystems = computed(() => {
    let list = this.devices();
    const query = this.searchQuery().trim().toLowerCase();

    if (query) {
      list = list.filter(item => 
        (item.systemName && item.systemName.toLowerCase().includes(query)) ||
        (item.deviceCode && item.deviceCode.toLowerCase().includes(query)) ||
        (item.serialNumber && item.serialNumber.toLowerCase().includes(query)) ||
        (item.model && item.model.toLowerCase().includes(query)) ||
        (item.ownerUserId !== null && String(item.ownerUserId).includes(query))
      );
    }

    return list;
  });

  constructor() {
    // Eager constructor loading removed as per Step 9 Part 3:
    // Protected records load only after backend-authorized dashboard entry.
  }

  /**
   * Request device page from DeviceAccessService (GET /api/v1/devices?page={page}&size={size})
   */
  loadSystems(page: number = 0, size: number = 10): Observable<ApiResponse<DevicePageResponse>> {
    this.isLoading.set(true);
    this.errorMessage.set('');
    return this.deviceAccessService.listDevices(page, size);
  }

  /**
   * Apply verified backend page response to in-memory state
   */
  setPageData(pageData: DevicePageResponse) {
    this.devices.set(pageData.content || []);
    this.currentPage.set(pageData.number);
    this.pageSize.set(pageData.size);
    this.totalElements.set(pageData.totalElements);
    this.totalPages.set(pageData.totalPages);
    this.isFirst.set(pageData.first);
    this.isLast.set(pageData.last);
    this.isLoading.set(false);
    this.errorMessage.set('');
  }

  /**
   * Set error state on failure (never falls back to localStorage or mock records)
   */
  setError(message: string) {
    this.devices.set([]);
    this.errorMessage.set(message);
    this.isLoading.set(false);
  }

  /**
   * Clear all records and pagination state on logout/new login/auth clear
   */
  clear() {
    this.devices.set([]);
    this.currentPage.set(0);
    this.totalElements.set(0);
    this.totalPages.set(0);
    this.isFirst.set(true);
    this.isLast.set(true);
    this.isLoading.set(false);
    this.errorMessage.set('');
    this.searchQuery.set('');
    this.statusFilter.set('all');
  }

  setStatusFilter(status: 'all' | SystemStatus) {
    this.statusFilter.set(status);
  }

  setSearchQuery(query: string) {
    this.searchQuery.set(query);
  }

  clearFilters() {
    this.statusFilter.set('all');
    this.searchQuery.set('');
  }
}
