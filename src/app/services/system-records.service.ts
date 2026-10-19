import { Injectable, computed, signal, inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, of, tap } from 'rxjs';

export type SystemStatus = 'online' | 'warning' | 'offline';

export interface SystemRecord {
  id: string;
  name: string;
  serialNumber: string; // Stored as a string so leading zeros are strictly preserved
  status: SystemStatus;
  user: string;
  createdAt: string;
  location?: string;
  model?: string;
}

const STORAGE_KEY = 'hykon_registered_systems_v1';
const API_SYSTEMS_URL = 'http://localhost:8080/api/v1/systems';

@Injectable({
  providedIn: 'root'
})
export class SystemRecordsService {
  private platformId = inject(PLATFORM_ID);
  private http = inject(HttpClient);

  // Core system records state - strictly initialized as empty
  readonly systems = signal<SystemRecord[]>([]);
  readonly isLoading = signal<boolean>(false);
  readonly errorMessage = signal<string>('');

  // Filtering & Search state
  readonly statusFilter = signal<'all' | SystemStatus>('all');
  readonly searchQuery = signal<string>('');

  // Summary Metrics (computed from total accessible dataset, unaffected by table filters)
  readonly totalCount = computed(() => this.systems().length);
  readonly onlineCount = computed(() => this.systems().filter(s => s.status === 'online').length);
  readonly warningCount = computed(() => this.systems().filter(s => s.status === 'warning').length);
  readonly offlineCount = computed(() => this.systems().filter(s => s.status === 'offline').length);

  // Filtered dataset for the table view (combines search and status filter)
  readonly filteredSystems = computed(() => {
    let list = this.systems();
    const filter = this.statusFilter();
    const query = this.searchQuery().trim().toLowerCase();

    if (filter !== 'all') {
      list = list.filter(item => item.status === filter);
    }

    if (query) {
      list = list.filter(item => 
        item.name.toLowerCase().includes(query) || 
        item.serialNumber.toLowerCase().includes(query) ||
        item.user.toLowerCase().includes(query)
      );
    }

    return list;
  });

  constructor() {
    this.loadSystems();
  }

  /**
   * Load systems from backend endpoint, with isolated local fallback
   */
  loadSystems() {
    if (!isPlatformBrowser(this.platformId)) return;

    // Check localStorage cache for registered devices
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      if (stored) {
        const parsed = JSON.parse(stored);
        if (Array.isArray(parsed)) {
          this.systems.set(parsed);
        }
      }
    } catch {
      // Storage parsing failed, keep dataset empty
    }

    // Try syncing with backend API if available
    this.isLoading.set(true);
    this.http.get<SystemRecord[]>(API_SYSTEMS_URL).pipe(
      catchError(() => of(null)) // Backend not running, gracefully retain current local state
    ).subscribe({
      next: (data) => {
        if (data && Array.isArray(data)) {
          this.systems.set(data);
          this.saveToStorage(data);
        }
        this.isLoading.set(false);
      },
      error: () => {
        this.isLoading.set(false);
      }
    });
  }

  /**
   * Register a new system device
   */
  addSystem(record: Omit<SystemRecord, 'id' | 'createdAt'>): Promise<SystemRecord> {
    return new Promise((resolve) => {
      const newRecord: SystemRecord = {
        ...record,
        id: 'SYS-' + Date.now().toString(36).toUpperCase(),
        serialNumber: String(record.serialNumber).trim(), // preserve leading zeros as string
        createdAt: new Date().toISOString()
      };

      const updated = [newRecord, ...this.systems()];
      this.systems.set(updated);
      this.saveToStorage(updated);

      // Attempt backend post in background
      this.http.post<SystemRecord>(API_SYSTEMS_URL, newRecord).pipe(
        catchError(() => of(newRecord))
      ).subscribe();

      resolve(newRecord);
    });
  }

  /**
   * Delete or unregister a system
   */
  deleteSystem(id: string) {
    const updated = this.systems().filter(s => s.id !== id);
    this.systems.set(updated);
    this.saveToStorage(updated);
  }

  /**
   * Update search and filter controls
   */
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

  private saveToStorage(list: SystemRecord[]) {
    if (isPlatformBrowser(this.platformId)) {
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(list));
      } catch {
        // Storage quota exceeded or disabled
      }
    }
  }
}
