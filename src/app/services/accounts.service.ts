import { Injectable, computed, signal, inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { catchError, of } from 'rxjs';

// ─── Domain types ─────────────────────────────────────────────────────────────

export type AccountUserType     = 'Admin' | 'Operator' | 'Viewer' | 'Owner';
export type AccountActiveStatus = 'Active' | 'Inactive';
export type AccountStatus       = 'Enabled' | 'Disabled' | 'Suspended';

export interface AccountRecord {
  id: string;
  username: string;
  phone: string;           // string to preserve leading zeros
  userType: AccountUserType;
  activeStatus: AccountActiveStatus;
  accountStatus: AccountStatus;
  dataloggers: number;
  deviceQuantity: number;
  createdAt: string;
}

export interface AccountGroup {
  id: string;
  name: string;
  memberCount: number;
  createdAt: string;
}

export interface CreateAccountRequest {
  username: string;
  phone: string;
  userType: AccountUserType;
  activeStatus: AccountActiveStatus;
  accountStatus: AccountStatus;
}

// ─── Storage & API ───────────────────────────────────────────────────────────

const ACCOUNTS_STORAGE_KEY = 'hykon_accounts_v1';
const GROUPS_STORAGE_KEY   = 'hykon_groups_v1';
const API_ACCOUNTS_URL     = 'http://localhost:8080/api/v1/accounts';
const API_GROUPS_URL       = 'http://localhost:8080/api/v1/accounts/groups';

// ─── Service ─────────────────────────────────────────────────────────────────

@Injectable({ providedIn: 'root' })
export class AccountsService {
  private readonly platformId = inject(PLATFORM_ID);
  private readonly http       = inject(HttpClient);

  readonly accounts  = signal<AccountRecord[]>([]);
  readonly groups    = signal<AccountGroup[]>([]);
  readonly isLoading = signal<boolean>(false);
  readonly error     = signal<string>('');

  readonly searchQuery    = signal<string>('');
  readonly userTypeFilter = signal<AccountUserType | 'all'>('all');
  readonly activeFilter   = signal<AccountActiveStatus | 'all'>('all');
  readonly statusFilter   = signal<AccountStatus | 'all'>('all');

  readonly filteredAccounts = computed(() => {
    let list = this.accounts();
    const q  = this.searchQuery().trim().toLowerCase();
    const ut = this.userTypeFilter();
    const af = this.activeFilter();
    const sf = this.statusFilter();
    if (q)         list = list.filter(a => a.username.toLowerCase().includes(q) || a.phone.includes(q));
    if (ut !== 'all') list = list.filter(a => a.userType === ut);
    if (af !== 'all') list = list.filter(a => a.activeStatus === af);
    if (sf !== 'all') list = list.filter(a => a.accountStatus === sf);
    return list;
  });

  readonly totalCount = computed(() => this.accounts().length);

  readonly hasActiveFilters = computed(
    () => this.searchQuery() !== '' || this.userTypeFilter() !== 'all' ||
          this.activeFilter() !== 'all' || this.statusFilter() !== 'all'
  );

  constructor() {
    this.loadAccounts();
    this.loadGroups();
  }

  loadAccounts(): void {
    if (!isPlatformBrowser(this.platformId)) return;
    try {
      const raw = localStorage.getItem(ACCOUNTS_STORAGE_KEY);
      if (raw) { const p = JSON.parse(raw); if (Array.isArray(p)) this.accounts.set(p); }
    } catch { /* keep empty */ }

    this.isLoading.set(true);
    this.http.get<AccountRecord[]>(API_ACCOUNTS_URL).pipe(catchError(() => of(null))).subscribe({
      next: data => {
        if (data && Array.isArray(data)) { this.accounts.set(data); this.saveAccounts(data); }
        this.isLoading.set(false);
      },
      error: () => this.isLoading.set(false)
    });
  }

  loadGroups(): void {
    if (!isPlatformBrowser(this.platformId)) return;
    try {
      const raw = localStorage.getItem(GROUPS_STORAGE_KEY);
      if (raw) { const p = JSON.parse(raw); if (Array.isArray(p)) this.groups.set(p); }
    } catch { /* keep empty */ }

    this.http.get<AccountGroup[]>(API_GROUPS_URL).pipe(catchError(() => of(null))).subscribe({
      next: data => {
        if (data && Array.isArray(data)) { this.groups.set(data); this.saveGroups(data); }
      },
      error: () => {}
    });
  }

  createAccount(req: CreateAccountRequest): Promise<AccountRecord> {
    return new Promise(resolve => {
      const record: AccountRecord = {
        ...req,
        id:             'ACC-' + Date.now().toString(36).toUpperCase(),
        phone:          String(req.phone).trim(),
        dataloggers:    0,
        deviceQuantity: 0,
        createdAt:      new Date().toISOString()
      };
      const updated = [record, ...this.accounts()];
      this.accounts.set(updated);
      this.saveAccounts(updated);
      this.http.post<AccountRecord>(API_ACCOUNTS_URL, record).pipe(catchError(() => of(record))).subscribe();
      resolve(record);
    });
  }

  setSearch(q: string): void                          { this.searchQuery.set(q); }
  setUserType(v: AccountUserType | 'all'): void       { this.userTypeFilter.set(v); }
  setActiveStatus(v: AccountActiveStatus | 'all'): void { this.activeFilter.set(v); }
  setAccountStatus(v: AccountStatus | 'all'): void    { this.statusFilter.set(v); }
  clearFilters(): void {
    this.searchQuery.set(''); this.userTypeFilter.set('all');
    this.activeFilter.set('all'); this.statusFilter.set('all');
  }

  private saveAccounts(list: AccountRecord[]): void {
    if (!isPlatformBrowser(this.platformId)) return;
    try { localStorage.setItem(ACCOUNTS_STORAGE_KEY, JSON.stringify(list)); } catch {}
  }

  private saveGroups(list: AccountGroup[]): void {
    if (!isPlatformBrowser(this.platformId)) return;
    try { localStorage.setItem(GROUPS_STORAGE_KEY, JSON.stringify(list)); } catch {}
  }
}
