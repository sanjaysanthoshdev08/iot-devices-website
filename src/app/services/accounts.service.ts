import { Injectable, computed, signal } from '@angular/core';

// ─── Domain types ─────────────────────────────────────────────────────────────

export type AccountUserType     = 'Admin' | 'Operator' | 'Viewer' | 'Owner';
export type AccountActiveStatus = 'Active' | 'Inactive';
export type AccountStatus       = 'Enabled' | 'Disabled' | 'Suspended';

export interface AccountRecord {
  id: string;
  username: string;
  phone: string;
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

// ─── Service ─────────────────────────────────────────────────────────────────
// Accounts listing and management are handled via backend provisioning APIs in Step 9 Part 4.
// Fake local creation, fake storage persistence, and unsupported /api/v1/accounts calls are removed.

@Injectable({ providedIn: 'root' })
export class AccountsService {
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
    // Eager loading of fake storage and unsupported /api/v1/accounts endpoints disabled
  }

  setSearch(q: string): void                           { this.searchQuery.set(q); }
  setUserType(v: AccountUserType | 'all'): void        { this.userTypeFilter.set(v); }
  setActiveStatus(v: AccountActiveStatus | 'all'): void  { this.activeFilter.set(v); }
  setAccountStatus(v: AccountStatus | 'all'): void     { this.statusFilter.set(v); }
  clearFilters(): void {
    this.searchQuery.set(''); this.userTypeFilter.set('all');
    this.activeFilter.set('all'); this.statusFilter.set('all');
  }
}
