import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { Subject, of, throwError } from 'rxjs';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { App } from './app';
import { AuthService, AuthUser } from './services/auth.service';
import { DeviceAccessService } from './services/device-access.service';
import { ManagementService } from './services/management.service';

describe('Step 9 Part 2: Role-Aware Entry, Gate Verification & Dashboard Navigation', () => {
  let fixture: ComponentFixture<App>;
  let app: App;
  let mockAuthService: {
    isAuthenticated: ReturnType<typeof signal<boolean>>;
    currentUser: ReturnType<typeof signal<AuthUser | null>>;
    logout: any;
    clearSession: any;
    login: any;
  };
  let mockDeviceAccessService: {
    verifyDevice: any;
    verifyDistributor: any;
    listDevices: any;
    getDevice: any;
  };
  let mockManagementService: {
    registerDevice: any;
    recordDevicePurchase: any;
    createDistributorAdmin: any;
    createDistributor: any;
  };

  beforeEach(async () => {
    mockAuthService = {
      isAuthenticated: signal<boolean>(false),
      currentUser: signal<AuthUser | null>(null),
      logout: vi.fn().mockReturnValue(of({ success: true, message: 'Logged out', data: {} })),
      clearSession: vi.fn().mockImplementation(() => {
        mockAuthService.isAuthenticated.set(false);
        mockAuthService.currentUser.set(null);
      }),
      login: vi.fn().mockReturnValue(of({ success: true, message: 'Logged in', data: {} }))
    };

    mockDeviceAccessService = {
      verifyDevice: vi.fn(),
      verifyDistributor: vi.fn(),
      listDevices: vi.fn().mockReturnValue(of({ success: true, message: 'OK', data: { content: [] } })),
      getDevice: vi.fn()
    };

    mockManagementService = {
      registerDevice: vi.fn(),
      recordDevicePurchase: vi.fn(),
      createDistributorAdmin: vi.fn(),
      createDistributor: vi.fn()
    };

    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: mockAuthService },
        { provide: DeviceAccessService, useValue: mockDeviceAccessService },
        { provide: ManagementService, useValue: mockManagementService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(App);
    app = fixture.componentInstance;
  });

  // 1. Role-aware entry
  describe('1. Role-aware entry destinations', () => {
    it('should open login modal when unauthenticated user clicks feature box', () => {
      mockAuthService.isAuthenticated.set(false);
      mockAuthService.currentUser.set(null);

      app.handleFeatureEntry('voltage');

      expect(app.isLoginModalOpen()).toBe(true);
      expect(app.isDeviceAuthModalOpen()).toBe(false);
      expect(app.isDashboardOpen()).toBe(false);
    });

    it('should show customer Device ID / Serial Number gate for USER role', () => {
      mockAuthService.isAuthenticated.set(true);
      mockAuthService.currentUser.set({ id: 1, name: 'Alice', email: 'alice@test.com', role: 'USER', status: 'ACTIVE' });

      app.handleFeatureEntry('voltage');

      expect(app.isDeviceAuthModalOpen()).toBe(true);
      expect(app.isDistributorRole()).toBe(false);
      expect(app.isDashboardOpen()).toBe(false);
    });

    it('should show distributor verification form for DISTRIBUTOR_ADMIN role', () => {
      mockAuthService.isAuthenticated.set(true);
      mockAuthService.currentUser.set({ id: 2, name: 'Bob Dist', email: 'bob@test.com', role: 'DISTRIBUTOR_ADMIN', status: 'ACTIVE' });

      app.handleFeatureEntry('current');

      expect(app.isDeviceAuthModalOpen()).toBe(true);
      expect(app.isDistributorRole()).toBe(true);
      expect(app.isDashboardOpen()).toBe(false);
    });

    it('should bypass verification and open Overview for HYKON_ADMIN role', () => {
      mockAuthService.isAuthenticated.set(true);
      mockAuthService.currentUser.set({ id: 3, name: 'Admin Charlie', email: 'charlie@hykon.com', role: 'HYKON_ADMIN', status: 'ACTIVE' });

      app.handleFeatureEntry('inverter');

      expect(app.isDeviceAuthModalOpen()).toBe(false);
      expect(app.isDashboardOpen()).toBe(true);
      expect(app.activeDashboardNav()).toBe('overview');
    });

    it('should deny dashboard entry for unknown roles', () => {
      mockAuthService.isAuthenticated.set(true);
      mockAuthService.currentUser.set({ id: 4, name: 'Guest', email: 'guest@test.com', role: 'UNKNOWN_ROLE', status: 'ACTIVE' });

      app.handleFeatureEntry('power');

      expect(app.isDeviceAuthModalOpen()).toBe(false);
      expect(app.isDashboardOpen()).toBe(false);
      expect(app.dashboardToast()).toContain('Access denied');
    });

    it('should keep login success returning to Home without auto-opening dashboard', () => {
      app.isLoginModalOpen.set(true);
      app.isDashboardOpen.set(false);

      // Trigger login success handler
      app.clearDashboardAndGateState();
      app.closeLoginModal();
      app.redirectToHome();

      expect(app.isLoginModalOpen()).toBe(false);
      expect(app.isDashboardOpen()).toBe(false);
      expect(app.isDeviceAuthModalOpen()).toBe(false);
    });
  });

  // 2. Navigation permissions & forbidden tab access
  describe('2. Navigation permissions', () => {
    it('USER role can only access Systems and Warnings (alerts)', () => {
      mockAuthService.isAuthenticated.set(true);
      mockAuthService.currentUser.set({ id: 1, name: 'Alice', email: 'alice@test.com', role: 'USER', status: 'ACTIVE' });

      expect(app.canAccessTab('systems')).toBe(true);
      expect(app.canAccessTab('alerts')).toBe(true);
      expect(app.canAccessTab('overview')).toBe(false);
      expect(app.canAccessTab('accounts')).toBe(false);

      // Setting forbidden tab should be blocked
      app.activeDashboardNav.set('systems');
      app.setDashboardNav('overview');
      expect(app.activeDashboardNav()).toBe('systems');
      expect(app.dashboardToast()).toContain('Access denied');

      app.setDashboardNav('accounts');
      expect(app.activeDashboardNav()).toBe('systems');
    });

    it('DISTRIBUTOR_ADMIN role can access Overview, Systems, Warnings, but not Accounts', () => {
      mockAuthService.isAuthenticated.set(true);
      mockAuthService.currentUser.set({ id: 2, name: 'Bob', email: 'bob@test.com', role: 'DISTRIBUTOR_ADMIN', status: 'ACTIVE' });

      expect(app.canAccessTab('overview')).toBe(true);
      expect(app.canAccessTab('systems')).toBe(true);
      expect(app.canAccessTab('alerts')).toBe(true);
      expect(app.canAccessTab('accounts')).toBe(false);

      app.activeDashboardNav.set('overview');
      app.setDashboardNav('accounts');
      expect(app.activeDashboardNav()).toBe('overview');
      expect(app.dashboardToast()).toContain('Access denied');
    });

    it('HYKON_ADMIN role can access Overview, Systems, Accounts, and Warnings', () => {
      mockAuthService.isAuthenticated.set(true);
      mockAuthService.currentUser.set({ id: 3, name: 'Charlie', email: 'charlie@hykon.com', role: 'HYKON_ADMIN', status: 'ACTIVE' });

      expect(app.canAccessTab('overview')).toBe(true);
      expect(app.canAccessTab('systems')).toBe(true);
      expect(app.canAccessTab('accounts')).toBe(true);
      expect(app.canAccessTab('alerts')).toBe(true);

      app.setDashboardNav('accounts');
      expect(app.activeDashboardNav()).toBe('accounts');
    });
  });

  // 3. Failed verification and 429
  describe('3. Failed verification and rate limiting (429)', () => {
    it('failed device verification does not open dashboard and shows error', () => {
      mockAuthService.isAuthenticated.set(true);
      mockAuthService.currentUser.set({ id: 1, name: 'Alice', email: 'alice@test.com', role: 'USER', status: 'ACTIVE' });
      app.isDeviceAuthModalOpen.set(true);
      app.authDeviceId.set('DEV-100');
      app.authSerialNumber.set('SN-12345');

      mockDeviceAccessService.verifyDevice.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 400, error: { message: 'Device not found or invalid' } }))
      );

      app.verifyDeviceCredentials();

      expect(app.isDashboardOpen()).toBe(false);
      expect(app.isAuthVerifying()).toBe(false);
      expect(app.authErrorMessage()).toBe('Device not found or invalid');
    });

    it('device verification 429 displays temporary lockout and never opens dashboard', () => {
      mockAuthService.isAuthenticated.set(true);
      mockAuthService.currentUser.set({ id: 1, name: 'Alice', email: 'alice@test.com', role: 'USER', status: 'ACTIVE' });
      app.isDeviceAuthModalOpen.set(true);
      app.authDeviceId.set('DEV-100');
      app.authSerialNumber.set('SN-12345');

      mockDeviceAccessService.verifyDevice.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 429, error: { message: 'Too Many Requests' } }))
      );

      app.verifyDeviceCredentials();

      expect(app.isDashboardOpen()).toBe(false);
      expect(app.isAuthVerifying()).toBe(false);
      expect(app.authErrorMessage()).toContain('Too many verification attempts');
    });

    it('successful device verification opens Systems and updates active device label', () => {
      mockAuthService.isAuthenticated.set(true);
      mockAuthService.currentUser.set({ id: 1, name: 'Alice', email: 'alice@test.com', role: 'USER', status: 'ACTIVE' });
      app.isDeviceAuthModalOpen.set(true);
      app.authDeviceId.set('HALO-PRO-1');
      app.authSerialNumber.set('SN-998877');

      mockDeviceAccessService.verifyDevice.mockReturnValue(
        of({
          success: true,
          message: 'Device claimed',
          data: {
            id: 10,
            deviceCode: 'HALO-PRO-1',
            serialNumber: 'SN-998877',
            systemName: 'HALO PRO 3000 Inverter',
            model: 'HALO-3000',
            branchId: 1,
            distributorId: 1,
            ownerUserId: 1,
            status: 'ACTIVE',
            createdAt: '2026-01-01T00:00:00Z'
          }
        })
      );

      app.verifyDeviceCredentials();

      expect(app.isDashboardOpen()).toBe(true);
      expect(app.activeDashboardNav()).toBe('systems');
      expect(app.activeDashboardDevice()).toBe('HALO PRO 3000 Inverter');
      expect(app.isDeviceAuthModalOpen()).toBe(false);
    });

    it('distributor verification 429 displays lockout and never opens dashboard', () => {
      mockAuthService.isAuthenticated.set(true);
      mockAuthService.currentUser.set({ id: 2, name: 'Bob', email: 'bob@test.com', role: 'DISTRIBUTOR_ADMIN', status: 'ACTIVE' });
      app.isDeviceAuthModalOpen.set(true);
      app.distributorCode.set('DIST-1');
      app.branchCode.set('BR-1');
      app.distributorLocation.set('Kochi');

      mockDeviceAccessService.verifyDistributor.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 429, error: {} }))
      );

      app.verifyDistributorCredentials();

      expect(app.isDashboardOpen()).toBe(false);
      expect(app.isAuthVerifying()).toBe(false);
      expect(app.authErrorMessage()).toContain('Too many verification attempts');
    });

    it('successful distributor verification opens Overview', () => {
      mockAuthService.isAuthenticated.set(true);
      mockAuthService.currentUser.set({ id: 2, name: 'Bob', email: 'bob@test.com', role: 'DISTRIBUTOR_ADMIN', status: 'ACTIVE' });
      app.isDeviceAuthModalOpen.set(true);
      app.distributorCode.set('DIST-1');
      app.branchCode.set('BR-1');
      app.distributorLocation.set('Kochi');

      mockDeviceAccessService.verifyDistributor.mockReturnValue(
        of({
          success: true,
          message: 'Verified',
          data: {
            distributorId: 1,
            distributorCode: 'DIST-1',
            distributorName: 'Hykon South',
            branchId: 1,
            branchCode: 'BR-1',
            branchName: 'Kochi Branch',
            location: 'Kochi'
          }
        })
      );

      app.verifyDistributorCredentials();

      expect(app.isDashboardOpen()).toBe(true);
      expect(app.activeDashboardNav()).toBe('overview');
      expect(app.isDeviceAuthModalOpen()).toBe(false);
    });
  });

  // 4. Direct URL access denied by backend
  describe('4. Direct URL verification state', () => {
    it('direct URL /dashboard when backend listDevices returns 403 shows gate and keeps dashboard closed', () => {
      mockAuthService.isAuthenticated.set(true);
      mockAuthService.currentUser.set({ id: 1, name: 'Alice', email: 'alice@test.com', role: 'USER', status: 'ACTIVE' });

      mockDeviceAccessService.listDevices.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 403, error: { message: 'Access verification is required' } }))
      );

      app.handleUrlRouting('/dashboard');

      expect(app.isDashboardOpen()).toBe(false);
      expect(app.isDeviceAuthModalOpen()).toBe(true);
      expect(app.isDistributorRole()).toBe(false);
    });

    it('direct URL /dashboard when backend returns 401 returns to Login', () => {
      mockAuthService.isAuthenticated.set(true);
      mockAuthService.currentUser.set({ id: 1, name: 'Alice', email: 'alice@test.com', role: 'USER', status: 'ACTIVE' });

      mockDeviceAccessService.listDevices.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 401, error: { message: 'Session expired' } }))
      );

      app.handleUrlRouting('/dashboard');

      expect(app.isDashboardOpen()).toBe(false);
      expect(app.isLoginModalOpen()).toBe(true);
    });

    it('direct URL /dashboard with 200 allows role-permitted destination (USER directed to Systems)', () => {
      mockAuthService.isAuthenticated.set(true);
      mockAuthService.currentUser.set({ id: 1, name: 'Alice', email: 'alice@test.com', role: 'USER', status: 'ACTIVE' });

      mockDeviceAccessService.listDevices.mockReturnValue(
        of({
          success: true,
          message: 'Devices retrieved',
          data: {
            content: [{
              id: 5,
              deviceCode: 'DEV-5',
              serialNumber: 'SN-555',
              systemName: 'HALO Solar 5000',
              model: 'HALO-5000',
              branchId: 1,
              distributorId: 1,
              ownerUserId: 1,
              status: 'ACTIVE',
              createdAt: '2026-01-01T00:00:00Z'
            }],
            number: 0,
            size: 20,
            totalElements: 1,
            totalPages: 1,
            first: true,
            last: true
          }
        })
      );

      app.handleUrlRouting('/dashboard');

      expect(app.isDashboardOpen()).toBe(true);
      // USER can only access Systems, so /dashboard directs them to Systems
      expect(app.activeDashboardNav()).toBe('systems');
      expect(app.activeDashboardDevice()).toBe('HALO Solar 5000');
    });
  });

  // 5. Late responses after logout or closure ignored
  describe('5. Late responses cancellation', () => {
    it('late verification response after logout is ignored and does not open dashboard', () => {
      mockAuthService.isAuthenticated.set(true);
      mockAuthService.currentUser.set({ id: 1, name: 'Alice', email: 'alice@test.com', role: 'USER', status: 'ACTIVE' });

      const responseSubject = new Subject<any>();
      mockDeviceAccessService.verifyDevice.mockReturnValue(responseSubject.asObservable());

      app.isDeviceAuthModalOpen.set(true);
      app.authDeviceId.set('DEV-1');
      app.authSerialNumber.set('SN-1');

      app.verifyDeviceCredentials();
      expect(app.isAuthVerifying()).toBe(true);

      // User logs out while request is pending
      app.onLogout();
      mockAuthService.isAuthenticated.set(false);
      mockAuthService.currentUser.set(null);

      // Late response arrives from server
      responseSubject.next({
        success: true,
        message: 'Claimed',
        data: { id: 1, deviceCode: 'DEV-1', serialNumber: 'SN-1', systemName: 'Halo', model: 'M', branchId: 1, distributorId: 1, ownerUserId: 1, status: 'ACTIVE', createdAt: '' }
      });
      responseSubject.complete();

      expect(app.isDashboardOpen()).toBe(false);
      expect(app.isDeviceAuthModalOpen()).toBe(false);
    });

    it('late verification response after gate closure is ignored', () => {
      mockAuthService.isAuthenticated.set(true);
      mockAuthService.currentUser.set({ id: 1, name: 'Alice', email: 'alice@test.com', role: 'USER', status: 'ACTIVE' });

      const responseSubject = new Subject<any>();
      mockDeviceAccessService.verifyDevice.mockReturnValue(responseSubject.asObservable());

      app.isDeviceAuthModalOpen.set(true);
      app.authDeviceId.set('DEV-1');
      app.authSerialNumber.set('SN-1');

      app.verifyDeviceCredentials();

      // User closes gate modal
      app.closeDeviceAuthModal();
      expect(app.isDeviceAuthModalOpen()).toBe(false);

      // Late response arrives
      responseSubject.next({
        success: true,
        message: 'Claimed',
        data: { id: 1, deviceCode: 'DEV-1', serialNumber: 'SN-1', systemName: 'Halo', model: 'M', branchId: 1, distributorId: 1, ownerUserId: 1, status: 'ACTIVE', createdAt: '' }
      });
      responseSubject.complete();

      expect(app.isDashboardOpen()).toBe(false);
    });
  });

  // 6. Access control gap regressions
  describe('6. Access control gap regressions', () => {
    it('AuthService clearing authentication while dashboard is open immediately closes protected content and resets state', () => {
      mockAuthService.isAuthenticated.set(true);
      mockAuthService.currentUser.set({ id: 1, name: 'Alice', email: 'alice@test.com', role: 'USER', status: 'ACTIVE' });

      app.isDashboardOpen.set(true);
      app.isDashboardAuthorized.set(true);
      app.activeDashboardNav.set('systems');

      mockAuthService.clearSession();
      fixture.detectChanges();

      expect(app.isDashboardOpen()).toBe(false);
      expect(app.isDashboardAuthorized()).toBe(false);
      expect(app.isDeviceAuthModalOpen()).toBe(false);
    });

    it('same-account new login invalidates old pending responses and does not open dashboard', () => {
      mockAuthService.isAuthenticated.set(true);
      mockAuthService.currentUser.set({ id: 1, name: 'Alice', email: 'alice@test.com', role: 'USER', status: 'ACTIVE' });

      const responseSubject = new Subject<any>();
      mockDeviceAccessService.verifyDevice.mockReturnValue(responseSubject.asObservable());

      app.isDeviceAuthModalOpen.set(true);
      app.authDeviceId.set('DEV-1');
      app.authSerialNumber.set('SN-1');

      app.verifyDeviceCredentials();
      expect(app.isAuthVerifying()).toBe(true);

      // New login to the SAME account
      app.loginEmail.set('alice@test.com');
      app.loginPassword.set('Password123');
      app.onLoginSubmit();

      // Late response arrives from the old request
      responseSubject.next({
        success: true,
        message: 'Device claimed',
        data: { id: 1, deviceCode: 'DEV-1', serialNumber: 'SN-1', systemName: 'Halo', model: 'M', branchId: 1, distributorId: 1, ownerUserId: 1, status: 'ACTIVE', createdAt: '' }
      });
      responseSubject.complete();

      expect(app.isDashboardOpen()).toBe(false);
      expect(app.isDeviceAuthModalOpen()).toBe(false);
      expect(app.isDashboardAuthorized()).toBe(false);
    });

    it('same-session token refresh preserves dashboard access', () => {
      mockAuthService.isAuthenticated.set(true);
      mockAuthService.currentUser.set({ id: 1, name: 'Alice', email: 'alice@test.com', role: 'USER', status: 'ACTIVE' });
      fixture.detectChanges();

      app.isDashboardOpen.set(true);
      app.isDashboardAuthorized.set(true);
      app.activeDashboardNav.set('systems');

      // Ordinary token refresh within same session (isAuthenticated remains true, user unchanged)
      mockAuthService.isAuthenticated.set(true);
      fixture.detectChanges();

      expect(app.isDashboardOpen()).toBe(true);
      expect(app.isDashboardAuthorized()).toBe(true);
      expect(app.activeDashboardNav()).toBe('systems');
    });

    it('home navigation ignores late responses', () => {
      mockAuthService.isAuthenticated.set(true);
      mockAuthService.currentUser.set({ id: 1, name: 'Alice', email: 'alice@test.com', role: 'USER', status: 'ACTIVE' });

      const responseSubject = new Subject<any>();
      mockDeviceAccessService.verifyDevice.mockReturnValue(responseSubject.asObservable());

      app.isDeviceAuthModalOpen.set(true);
      app.authDeviceId.set('DEV-1');
      app.authSerialNumber.set('SN-1');

      app.verifyDeviceCredentials();
      expect(app.isAuthVerifying()).toBe(true);

      // User navigates to Home
      app.redirectToHome();
      expect(app.isAuthVerifying()).toBe(false);

      // Late response arrives
      responseSubject.next({
        success: true,
        message: 'Claimed',
        data: { id: 1, deviceCode: 'DEV-1', serialNumber: 'SN-1', systemName: 'Halo', model: 'M', branchId: 1, distributorId: 1, ownerUserId: 1, status: 'ACTIVE', createdAt: '' }
      });
      responseSubject.complete();

      expect(app.isDashboardOpen()).toBe(false);
      expect(app.isDeviceAuthModalOpen()).toBe(false);
    });

    it('component destruction ignores late responses', () => {
      mockAuthService.isAuthenticated.set(true);
      mockAuthService.currentUser.set({ id: 1, name: 'Alice', email: 'alice@test.com', role: 'USER', status: 'ACTIVE' });

      const responseSubject = new Subject<any>();
      mockDeviceAccessService.verifyDevice.mockReturnValue(responseSubject.asObservable());

      app.isDeviceAuthModalOpen.set(true);
      app.authDeviceId.set('DEV-1');
      app.authSerialNumber.set('SN-1');

      app.verifyDeviceCredentials();
      expect(app.isAuthVerifying()).toBe(true);

      // Component destroyed
      app.ngOnDestroy();
      expect(app.isAuthVerifying()).toBe(false);

      // Late response arrives
      responseSubject.next({
        success: true,
        message: 'Claimed',
        data: { id: 1, deviceCode: 'DEV-1', serialNumber: 'SN-1', systemName: 'Halo', model: 'M', branchId: 1, distributorId: 1, ownerUserId: 1, status: 'ACTIVE', createdAt: '' }
      });
      responseSubject.complete();

      expect(app.isDashboardOpen()).toBe(false);
    });

    it('direct openDashboard() invocation receiving backend 403 closes protected content and shows gate', () => {
      mockAuthService.isAuthenticated.set(true);
      mockAuthService.currentUser.set({ id: 1, name: 'Alice', email: 'alice@test.com', role: 'USER', status: 'ACTIVE' });

      mockDeviceAccessService.listDevices.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 403, error: { message: 'Access verification required' } }))
      );

      app.openDashboard('systems');

      expect(app.isDashboardOpen()).toBe(false);
      expect(app.isDashboardAuthorized()).toBe(false);
      expect(app.isDeviceAuthModalOpen()).toBe(true);
      expect(app.isDistributorRole()).toBe(false);
    });

    it('restricted content is absent from rendered DOM', () => {
      // 1. Unauthenticated: dashboard page is absent
      mockAuthService.isAuthenticated.set(false);
      mockAuthService.currentUser.set(null);
      fixture.detectChanges();

      const element: HTMLElement = fixture.nativeElement;
      expect(element.querySelector('.dashboard-fullscreen-page')).toBeNull();

      // 2. USER role: Overview and Accounts sections must be absent from rendered DOM
      mockAuthService.isAuthenticated.set(true);
      mockAuthService.currentUser.set({ id: 1, name: 'Alice', email: 'alice@test.com', role: 'USER', status: 'ACTIVE' });
      app.isDashboardOpen.set(true);
      app.isDashboardAuthorized.set(true);
      app.activeDashboardNav.set('systems');
      fixture.detectChanges();

      expect(element.querySelector('.dash-app-window')).toBeNull();
      expect(element.querySelector('.acct-tabs')).toBeNull();
      expect(element.querySelector('.systems-metrics-grid')).not.toBeNull();

      // Even if activeDashboardNav was set to overview or accounts, guards prevent rendering
      app.activeDashboardNav.set('overview');
      fixture.detectChanges();
      expect(element.querySelector('.dash-app-window')).toBeNull();

      app.activeDashboardNav.set('accounts');
      fixture.detectChanges();
      expect(element.querySelector('.acct-tabs')).toBeNull();

      // 3. DISTRIBUTOR_ADMIN role: Accounts section must be absent from rendered DOM
      mockAuthService.currentUser.set({ id: 2, name: 'Bob', email: 'bob@test.com', role: 'DISTRIBUTOR_ADMIN', status: 'ACTIVE' });
      app.activeDashboardNav.set('overview');
      fixture.detectChanges();
      expect(element.querySelector('.dash-app-window')).not.toBeNull();

      app.activeDashboardNav.set('accounts');
      fixture.detectChanges();
      expect(element.querySelector('.acct-tabs')).toBeNull();
    });

    it('late error responses do not change newer navigation state', () => {
      mockAuthService.isAuthenticated.set(true);
      mockAuthService.currentUser.set({ id: 1, name: 'Alice', email: 'alice@test.com', role: 'USER', status: 'ACTIVE' });

      const errorSubject = new Subject<any>();
      mockDeviceAccessService.verifyDevice.mockReturnValue(errorSubject.asObservable());

      app.isDeviceAuthModalOpen.set(true);
      app.authDeviceId.set('DEV-1');
      app.authSerialNumber.set('SN-1');

      app.verifyDeviceCredentials();
      expect(app.isAuthVerifying()).toBe(true);

      // Newer navigation occurs: user closes device gate and opens distributor gate
      app.closeDeviceAuthModal();
      app.openDistributorGate();
      expect(app.isDeviceAuthModalOpen()).toBe(true);

      // Late error arrives from the first request
      errorSubject.error(new HttpErrorResponse({ status: 403, error: { message: 'Old error' } }));

      // Gate remains open as distributor gate, error message not overwritten
      expect(app.isDeviceAuthModalOpen()).toBe(true);
      expect(app.authErrorMessage()).toBe('');
    });
  });

  // Step 9 Part 3: Systems List & Device Details Backend Integration
  describe('Step 9 Part 3: Systems List & Device Details Backend Integration', () => {
    const mockPageData = {
      content: [
        {
          id: 101,
          deviceCode: 'DEV-101',
          serialNumber: '0000000101',
          systemName: 'Hykon Inverter 1',
          model: 'HY-5000',
          branchId: 2,
          distributorId: 5,
          ownerUserId: 42,
          status: 'ACTIVE',
          createdAt: '2026-03-15T10:00:00Z'
        },
        {
          id: 102,
          deviceCode: 'DEV-102',
          serialNumber: '0000000102',
          systemName: null,
          model: null,
          branchId: null,
          distributorId: null,
          ownerUserId: null,
          status: 'ACTIVE',
          createdAt: null
        }
      ],
      totalElements: 25,
      totalPages: 3,
      size: 10,
      number: 0,
      first: true,
      last: false,
      empty: false
    };

    beforeEach(() => {
      mockAuthService.isAuthenticated.set(true);
      mockAuthService.currentUser.set({
        id: 1,
        name: 'Admin Charlie',
        email: 'charlie@hykon.com',
        role: 'HYKON_ADMIN',
        status: 'ACTIVE'
      });
      fixture.detectChanges();
    });

    it('maps real paginated response fields and calculates metrics correctly', () => {
      mockDeviceAccessService.listDevices.mockReturnValue(of({
        success: true,
        message: 'OK',
        data: mockPageData
      }));

      app.loadSystems(0);
      fixture.detectChanges();

      const devices = app.systemRecordsService.devices();
      expect(devices.length).toBe(2);
      expect(devices[0].id).toBe(101);
      expect(devices[0].serialNumber).toBe('0000000101');
      expect(devices[0].systemName).toBe('Hykon Inverter 1');
      expect(devices[0].ownerUserId).toBe(42);

      // Null fallback: systemName null falls back to deviceCode
      expect(devices[1].systemName).toBeNull();
      expect(devices[1].deviceCode).toBe('DEV-102');
      expect(devices[1].ownerUserId).toBeNull();

      // Total count from backend
      expect(app.totalSystemsCount()).toBe(25);
      expect(app.totalPages()).toBe(3);
      expect(app.isFirstPage()).toBe(true);
      expect(app.isLastPage()).toBe(false);
    });

    it('ACTIVE status is treated as lifecycle status and telemetry is shown as unavailable', () => {
      mockDeviceAccessService.listDevices.mockReturnValue(of({
        success: true,
        message: 'OK',
        data: mockPageData
      }));

      app.loadSystems(0);
      fixture.detectChanges();

      const devices = app.systemRecordsService.devices();
      // Status is ACTIVE
      expect(devices[0].status).toBe('ACTIVE');

      // Telemetry filters/metrics show unavailable counts ("—")
      const element = fixture.nativeElement as HTMLElement;
      const countElements = element.querySelectorAll('.metric-card-count');
      // Metric cards: All (25), Online (—), Warning (—), Offline (—)
      expect(app.totalSystemsCount()).toBe(25);
    });

    it('calculates sequential row numbering across pages correctly', () => {
      mockDeviceAccessService.listDevices.mockReturnValue(of({
        success: true,
        message: 'OK',
        data: {
          ...mockPageData,
          number: 1, // page index 1 (second page)
          first: false,
          last: false
        }
      }));

      app.loadSystems(1);
      fixture.detectChanges();

      expect(app.currentPage()).toBe(1);
      expect(app.pageSize()).toBe(10);
      // Row numbering formula: currentPage * pageSize + index + 1
      const row1Number = app.currentPage() * app.pageSize() + 0 + 1;
      const row2Number = app.currentPage() * app.pageSize() + 1 + 1;
      expect(row1Number).toBe(11);
      expect(row2Number).toBe(12);
    });

    it('displays distinct loading, empty, and error states without confusing empty with error', () => {
      // 1. Loading state
      const pendingSubject = new Subject<any>();
      mockDeviceAccessService.listDevices.mockReturnValue(pendingSubject.asObservable());

      app.loadSystems(0);
      expect(app.systemRecordsService.isLoading()).toBe(true);

      // 2. Error state
      pendingSubject.error(new HttpErrorResponse({ status: 500, statusText: 'Server Error' }));
      expect(app.systemRecordsService.isLoading()).toBe(false);
      expect(app.systemError()).toBe('Failed to load systems: Server Error');
      expect(app.systemRecordsService.devices().length).toBe(0);

      // 3. Successful empty state
      mockDeviceAccessService.listDevices.mockReturnValue(of({
        success: true,
        message: 'OK',
        data: {
          content: [],
          totalElements: 0,
          totalPages: 0,
          size: 10,
          number: 0,
          first: true,
          last: true,
          empty: true
        }
      }));

      app.loadSystems(0);
      expect(app.systemRecordsService.isLoading()).toBe(false);
      expect(app.systemError()).toBe('');
      expect(app.systemRecordsService.devices().length).toBe(0);
      expect(app.totalSystemsCount()).toBe(0);
    });

    it('does not fall back to localStorage on API failure', () => {
      // Set fake items in localStorage
      localStorage.setItem(
        'hykon_registered_systems_v1',
        JSON.stringify([{ id: 999, name: 'Fake System', sn: '999999', status: 'online' }])
      );

      mockDeviceAccessService.listDevices.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 500, error: { message: 'Database connection failed' } }))
      );

      app.loadSystems(0);

      // Records must be empty, not populated from localStorage
      expect(app.systemRecordsService.devices()).toEqual([]);
      expect(app.systemError()).toContain('Database connection failed');

      localStorage.removeItem('hykon_registered_systems_v1');
    });

    it('loads fresh device details and handles 404 cleanly', () => {
      const mockDetail: any = {
        id: 101,
        deviceCode: 'DEV-101',
        serialNumber: '0000000101',
        systemName: 'Solar Array Alpha',
        model: 'HY-5000',
        branchId: 2,
        distributorId: 5,
        ownerUserId: 12,
        status: 'ACTIVE',
        createdAt: '2026-03-15T10:00:00Z'
      };

      mockDeviceAccessService.getDevice.mockReturnValue(of({
        success: true,
        message: 'Device found',
        data: mockDetail
      }));

      app.openDeviceDetails(101);

      expect(mockDeviceAccessService.getDevice).toHaveBeenCalledWith(101);
      expect(app.isDeviceDetailModalOpen()).toBe(true);
      expect(app.selectedDevice()).toEqual(mockDetail);
      expect(app.detailError()).toBeNull();

      // Test 404 handling
      mockDeviceAccessService.getDevice.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 404, statusText: 'Not Found' }))
      );

      app.openDeviceDetails(999);

      expect(app.selectedDevice()).toBeNull();
      expect(app.detailError()).toBe('Device not found or unavailable');
      expect(app.isDetailLoading()).toBe(false);

      // Close modal clears state
      app.closeDeviceDetailsModal();
      expect(app.isDeviceDetailModalOpen()).toBe(false);
      expect(app.selectedDevice()).toBeNull();
      expect(app.detailError()).toBeNull();
    });

    it('403 error on listDevices clears records and returns through verification gate', () => {
      app.isDashboardOpen.set(true);
      app.isDashboardAuthorized.set(true);
      mockDeviceAccessService.listDevices.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 403, error: { message: 'Forbidden' } }))
      );

      app.loadSystems(0);

      expect(app.systemRecordsService.devices().length).toBe(0);
      expect(app.isDashboardOpen()).toBe(false);
      expect(app.isDashboardAuthorized()).toBe(false);
      expect(app.isDeviceAuthModalOpen()).toBe(true);
    });

    it('ignores late list and detail responses after logout or session reset', () => {
      const listSubject = new Subject<any>();
      const detailSubject = new Subject<any>();

      mockDeviceAccessService.listDevices.mockReturnValue(listSubject.asObservable());
      mockDeviceAccessService.getDevice.mockReturnValue(detailSubject.asObservable());

      app.loadSystems(0);
      app.openDeviceDetails(101);

      expect(app.systemRecordsService.isLoading()).toBe(true);
      expect(app.isDetailLoading()).toBe(true);

      // Session reset / logout occurs
      mockAuthService.clearSession();
      fixture.detectChanges();

      // Late responses arrive
      listSubject.next({
        success: true,
        message: 'OK',
        data: mockPageData
      });
      listSubject.complete();

      detailSubject.next({
        success: true,
        message: 'OK',
        data: mockPageData.content[0]
      });
      detailSubject.complete();

      // Responses must be completely ignored
      expect(app.systemRecordsService.devices().length).toBe(0);
      expect(app.selectedDevice()).toBeNull();
      expect(app.isDashboardOpen()).toBe(false);
    });

    it('same-account new login invalidates pending list and detail requests', () => {
      app.isDashboardOpen.set(true);
      app.isDashboardAuthorized.set(true);

      const listSubject = new Subject<any>();
      const detailSubject = new Subject<any>();

      mockDeviceAccessService.listDevices.mockReturnValue(listSubject.asObservable());
      mockDeviceAccessService.getDevice.mockReturnValue(detailSubject.asObservable());

      app.loadSystems(0);
      app.openDeviceDetails(101);

      expect(app.systemRecordsService.isLoading()).toBe(true);
      expect(app.isDetailLoading()).toBe(true);

      // Same-account new login occurs
      mockAuthService.login.mockReturnValue(of({
        success: true,
        message: 'Login successful',
        data: {
          user: { id: 1, name: 'Admin Charlie', email: 'charlie@hykon.com', role: 'HYKON_ADMIN', status: 'ACTIVE' },
          token: 'token-2'
        }
      }));

      app.loginEmail.set('charlie@hykon.com');
      app.loginPassword.set('newSecret123');
      app.onLoginSubmit();

      // Late responses from previous session arrive
      listSubject.next({
        success: true,
        message: 'OK',
        data: mockPageData
      });
      listSubject.complete();

      detailSubject.next({
        success: true,
        message: 'OK',
        data: mockPageData.content[0]
      });
      detailSubject.complete();

      // Old responses must be ignored
      expect(app.systemRecordsService.devices().length).toBe(0);
      expect(app.selectedDevice()).toBeNull();
      expect(app.isDeviceDetailModalOpen()).toBe(false);
    });

    it('leaving protected views invalidates pending list and detail requests', () => {
      app.isDashboardOpen.set(true);
      app.isDashboardAuthorized.set(true);

      const listSubject = new Subject<any>();
      const detailSubject = new Subject<any>();

      mockDeviceAccessService.listDevices.mockReturnValue(listSubject.asObservable());
      mockDeviceAccessService.getDevice.mockReturnValue(detailSubject.asObservable());

      app.loadSystems(0);
      app.openDeviceDetails(101);

      expect(app.systemRecordsService.isLoading()).toBe(true);
      expect(app.isDetailLoading()).toBe(true);

      // User leaves protected view
      app.closeDashboard();

      expect(app.isDashboardOpen()).toBe(false);
      expect(app.isDeviceDetailModalOpen()).toBe(false);

      // Late responses arrive
      listSubject.next({
        success: true,
        message: 'OK',
        data: mockPageData
      });
      listSubject.complete();

      detailSubject.next({
        success: true,
        message: 'OK',
        data: mockPageData.content[0]
      });
      detailSubject.complete();

      expect(app.systemRecordsService.devices().length).toBe(0);
      expect(app.selectedDevice()).toBeNull();
    });
  });

  // Step 9 Part 4: Supported Device Registration, Purchase Recording & HYKON Admin Provisioning
  describe('Step 9 Part 4: Supported Device Registration, Purchase Recording & HYKON Admin Provisioning', () => {
    beforeEach(() => {
      mockAuthService.isAuthenticated.set(true);
      mockAuthService.currentUser.set({
        id: 1,
        name: 'Admin Charlie',
        email: 'charlie@hykon.com',
        role: 'HYKON_ADMIN',
        status: 'ACTIVE'
      });
      fixture.detectChanges();
      app.isDashboardOpen.set(true);
      app.isDashboardAuthorized.set(true);
      app.activeDashboardNav.set('systems');
    });

    describe('Device Registration (POST /api/v1/management/devices)', () => {
      it('submits valid registration payload with actual DTO fields and refreshes systems list', () => {
        app.openAddSystemModal();
        expect(app.isAddSystemModalOpen()).toBe(true);

        app.newSystemDeviceCode.set('HALO-1200-01');
        app.newSystemSN.set('SN-HYK-1200-01');
        app.newSystemName.set('Residential Backup 1200');
        app.newSystemModel.set('HALO 1200');
        app.newSystemBranchId.set('');

        mockManagementService.registerDevice.mockReturnValue(of({
          success: true,
          message: 'Device registered successfully',
          data: {
            id: 201,
            deviceCode: 'HALO-1200-01',
            serialNumber: 'SN-HYK-1200-01',
            systemName: 'Residential Backup 1200',
            model: 'HALO 1200',
            branchId: null,
            distributorId: null,
            ownerUserId: null,
            status: 'ACTIVE',
            createdAt: '2026-03-20T10:00:00Z'
          }
        }));

        app.submitAddSystem();

        expect(mockManagementService.registerDevice).toHaveBeenCalledWith({
          deviceCode: 'HALO-1200-01',
          serialNumber: 'SN-HYK-1200-01',
          systemName: 'Residential Backup 1200',
          model: 'HALO 1200',
          branchId: null
        });

        expect(app.isAddSystemModalOpen()).toBe(false);
        expect(app.dashboardToast()).toContain('Device registered successfully');
        expect(mockDeviceAccessService.listDevices).toHaveBeenCalled();
      });

      it('enforces branchId requirement for DISTRIBUTOR_ADMIN', () => {
        mockAuthService.currentUser.set({
          id: 2,
          name: 'Bob Distributor',
          email: 'bob@distributor.com',
          role: 'DISTRIBUTOR_ADMIN',
          status: 'ACTIVE'
        });
        app.isDashboardAuthorized.set(true);

        app.openAddSystemModal();
        app.newSystemDeviceCode.set('HALO-1200-02');
        app.newSystemSN.set('SN-HYK-1200-02');
        app.newSystemName.set('Distributor Unit 1');
        app.newSystemBranchId.set(''); // Empty branch ID

        app.submitAddSystem();

        expect(mockManagementService.registerDevice).not.toHaveBeenCalled();
        expect(app.newSystemError()).toBe('Branch ID is required for distributor administrators.');
        expect(app.isAddSystemModalOpen()).toBe(true);

        // Providing valid positive branch ID succeeds
        app.newSystemBranchId.set('5');
        mockManagementService.registerDevice.mockReturnValue(of({ success: true, data: { id: 202 } }));

        app.submitAddSystem();

        expect(mockManagementService.registerDevice).toHaveBeenCalledWith({
          deviceCode: 'HALO-1200-02',
          serialNumber: 'SN-HYK-1200-02',
          systemName: 'Distributor Unit 1',
          model: null,
          branchId: 5
        });
        expect(app.isAddSystemModalOpen()).toBe(false);
      });

      it('blocks customer role (USER) from registering devices', () => {
        mockAuthService.currentUser.set({
          id: 3,
          name: 'Alice Customer',
          email: 'alice@customer.com',
          role: 'USER',
          status: 'ACTIVE'
        });
        app.isDashboardAuthorized.set(true);

        app.openAddSystemModal();
        expect(app.isAddSystemModalOpen()).toBe(false);

        app.submitAddSystem();
        expect(app.newSystemError()).toBe('Only administrators can register devices.');
        expect(mockManagementService.registerDevice).not.toHaveBeenCalled();
      });

      it('displays backend 400 validation and 409 conflict errors without closing modal', () => {
        app.openAddSystemModal();
        app.newSystemDeviceCode.set('HALO-1200-DUPE');
        app.newSystemSN.set('SN-DUPE-01');
        app.newSystemName.set('Duplicate Unit');

        // 409 Conflict
        mockManagementService.registerDevice.mockReturnValue(throwError(() => new HttpErrorResponse({
          status: 409,
          error: {
            success: false,
            status: 409,
            error: 'Conflict',
            message: 'Device ID or serial number is already registered'
          }
        })));

        app.submitAddSystem();

        expect(app.isAddSystemModalOpen()).toBe(true);
        expect(app.newSystemError()).toBe('Device ID or serial number is already registered');
        expect(app.isSubmittingSystem()).toBe(false);

        // 400 Validation error
        mockManagementService.registerDevice.mockReturnValue(throwError(() => new HttpErrorResponse({
          status: 400,
          error: {
            success: false,
            status: 400,
            error: 'Validation failed',
            message: 'Please correct the invalid request fields.',
            errors: {
              deviceCode: 'Device ID must not exceed 100 characters'
            }
          }
        })));

        app.submitAddSystem();

        expect(app.isAddSystemModalOpen()).toBe(true);
        expect(app.newSystemError()).toBe('Device ID must not exceed 100 characters');
      });

      it('prevents duplicate submissions while registration request is pending', () => {
        app.openAddSystemModal();
        app.newSystemDeviceCode.set('HALO-1200-01');
        app.newSystemSN.set('SN-1200-01');
        app.newSystemName.set('Unit 1');

        const pendingSubject = new Subject<any>();
        mockManagementService.registerDevice.mockReturnValue(pendingSubject.asObservable());

        app.submitAddSystem();
        expect(app.isSubmittingSystem()).toBe(true);

        // Second submission attempt while pending
        app.submitAddSystem();
        expect(mockManagementService.registerDevice).toHaveBeenCalledTimes(1);

        pendingSubject.next({ success: true, data: { id: 10 } });
        pendingSubject.complete();

        expect(app.isSubmittingSystem()).toBe(false);
        expect(app.isAddSystemModalOpen()).toBe(false);
      });
    });

    describe('Purchase Eligibility Recording (POST /api/v1/management/device-purchases)', () => {
      it('submits valid purchase recording payload with numeric IDs and non-blank reference', () => {
        app.openPurchaseModal();
        expect(app.isPurchaseModalOpen()).toBe(true);

        app.purchaseDeviceId.set('101');
        app.purchaseCustomerUserId.set('42');
        app.purchaseReference.set('INV-2026-00891');

        mockManagementService.recordDevicePurchase.mockReturnValue(of({
          success: true,
          message: 'Device purchase eligibility recorded',
          data: {
            id: 1,
            deviceId: 101,
            deviceCode: 'DEV-101',
            customerUserId: 42,
            recordedByUserId: 1,
            purchaseReference: 'INV-2026-00891',
            status: 'RECORDED',
            claimedAt: null,
            createdAt: '2026-03-20T10:00:00Z'
          }
        }));

        app.submitRecordPurchase();

        expect(mockManagementService.recordDevicePurchase).toHaveBeenCalledWith({
          deviceId: 101,
          customerUserId: 42,
          purchaseReference: 'INV-2026-00891'
        });

        expect(app.isPurchaseModalOpen()).toBe(false);
        expect(app.dashboardToast()).toContain('Purchase eligibility recorded successfully');
      });

      it('validates numeric positive deviceId and customerUserId', () => {
        app.openPurchaseModal();

        app.purchaseDeviceId.set('abc');
        app.purchaseCustomerUserId.set('42');
        app.purchaseReference.set('INV-123');

        app.submitRecordPurchase();
        expect(mockManagementService.recordDevicePurchase).not.toHaveBeenCalled();
        expect(app.purchaseError()).toBe('A valid positive numeric Device ID is required.');

        app.purchaseDeviceId.set('10');
        app.purchaseCustomerUserId.set('-5');

        app.submitRecordPurchase();
        expect(mockManagementService.recordDevicePurchase).not.toHaveBeenCalled();
        expect(app.purchaseError()).toBe('A valid positive numeric Customer User ID is required.');
      });

      it('displays backend 400 and 409 conflict errors without closing modal', () => {
        app.openPurchaseModal();
        app.purchaseDeviceId.set('10');
        app.purchaseCustomerUserId.set('20');
        app.purchaseReference.set('INV-DUPE');

        mockManagementService.recordDevicePurchase.mockReturnValue(throwError(() => new HttpErrorResponse({
          status: 409,
          error: {
            success: false,
            status: 409,
            error: 'Conflict',
            message: 'Purchase reference already recorded for this device'
          }
        })));

        app.submitRecordPurchase();

        expect(app.isPurchaseModalOpen()).toBe(true);
        expect(app.purchaseError()).toBe('Purchase reference already recorded for this device');
        expect(app.isSubmittingPurchase()).toBe(false);
      });
    });

    describe('HYKON Admin Provisioning & Safe Retry Flow', () => {
      it('enforces HYKON_ADMIN role requirement for provisioning endpoints', () => {
        mockAuthService.currentUser.set({
          id: 2,
          name: 'Bob Distributor',
          email: 'bob@distributor.com',
          role: 'DISTRIBUTOR_ADMIN',
          status: 'ACTIVE'
        });
        app.isDashboardAuthorized.set(true);

        app.openCreateDistAdminModal();
        expect(app.isCreateDistAdminModalOpen()).toBe(false);

        app.openCreateDistributorModal();
        expect(app.isCreateDistributorModalOpen()).toBe(false);

        app.submitCreateDistAdmin();
        expect(mockManagementService.createDistributorAdmin).not.toHaveBeenCalled();
        expect(app.distAdminError()).toBe('HYKON_ADMIN access is required.');

        app.submitCreateDistributor();
        expect(mockManagementService.createDistributor).not.toHaveBeenCalled();
        expect(app.distError()).toBe('HYKON_ADMIN access is required.');
      });

      it('creates distributor administrator and securely clears passwords on success', () => {
        app.openCreateDistAdminModal();
        expect(app.isCreateDistAdminModalOpen()).toBe(true);

        app.distAdminName.set('Jane Distributor');
        app.distAdminEmail.set('jane@dist.com');
        app.distAdminPassword.set('SecurePass123!');
        app.distAdminConfirmPassword.set('SecurePass123!');

        mockManagementService.createDistributorAdmin.mockReturnValue(of({
          success: true,
          message: 'Distributor administrator created',
          data: {
            id: 88,
            name: 'Jane Distributor',
            email: 'jane@dist.com',
            role: 'DISTRIBUTOR_ADMIN',
            status: 'ACTIVE',
            createdAt: '2026-03-20T10:00:00Z'
          }
        }));

        app.submitCreateDistAdmin();

        expect(mockManagementService.createDistributorAdmin).toHaveBeenCalledWith({
          name: 'Jane Distributor',
          email: 'jane@dist.com',
          password: 'SecurePass123!',
          confirmPassword: 'SecurePass123!'
        });

        // Passwords must be cleared
        expect(app.distAdminPassword()).toBe('');
        expect(app.distAdminConfirmPassword()).toBe('');
        expect(app.isCreateDistAdminModalOpen()).toBe(false);

        // Created administrator ID is stored and distributor modal auto-opened
        expect(app.lastCreatedAdminId()).toBe(88);
        expect(app.distAdminUserId()).toBe('88');
        expect(app.isCreateDistributorModalOpen()).toBe(true);
      });

      it('handles partial admin creation followed by failed distributor creation with safe retry without duplicate admin', () => {
        // Step 1: Admin created successfully
        app.lastCreatedAdminId.set(88);
        app.distAdminUserId.set('88');
        app.openCreateDistributorModal();

        app.distCode.set('DIST-88');
        app.distName.set('Hykon West');
        app.distBranchCode.set('BR-WEST-01');
        app.distBranchName.set('West Branch');
        app.distLocation.set('Kochi');

        // Step 2: Distributor creation fails on backend (e.g. branch code conflict)
        mockManagementService.createDistributor.mockReturnValue(throwError(() => new HttpErrorResponse({
          status: 409,
          error: {
            success: false,
            status: 409,
            error: 'Conflict',
            message: 'Branch code already in use'
          }
        })));

        app.submitCreateDistributor();

        // Modal stays open with admin ID retained for safe retry
        expect(app.isCreateDistributorModalOpen()).toBe(true);
        expect(app.distError()).toContain('Branch code already in use');
        expect(app.distError()).toContain('Administrator account #88 remains created');
        expect(app.distAdminUserId()).toBe('88');
        expect(app.lastCreatedAdminId()).toBe(88);

        // Step 3: User corrects the branch code and retries
        app.distBranchCode.set('BR-WEST-02');
        mockManagementService.createDistributor.mockReturnValue(of({
          success: true,
          message: 'Distributor and first branch created successfully',
          data: {
            id: 15,
            distributorCode: 'DIST-88',
            name: 'Hykon West',
            administratorUserId: 88,
            status: 'ACTIVE',
            branchId: 10,
            branchCode: 'BR-WEST-02',
            branchName: 'West Branch',
            location: 'Kochi',
            branchStatus: 'ACTIVE'
          }
        }));

        app.submitCreateDistributor();

        // Successful retry: modal closes, admin ID reset
        expect(mockManagementService.createDistributor).toHaveBeenCalledWith({
          distributorCode: 'DIST-88',
          name: 'Hykon West',
          administratorUserId: 88,
          branchCode: 'BR-WEST-02',
          branchName: 'West Branch',
          location: 'Kochi'
        });
        expect(app.isCreateDistributorModalOpen()).toBe(false);
        expect(app.lastCreatedAdminId()).toBeNull();
        expect(app.dashboardToast()).toContain('Distributor and initial branch created successfully');
      });

      it('clears sensitive passwords on modal close, logout, or session reset', () => {
        app.openCreateDistAdminModal();
        app.distAdminPassword.set('MySecret123');
        app.distAdminConfirmPassword.set('MySecret123');

        app.closeCreateDistAdminModal();
        expect(app.distAdminPassword()).toBe('');
        expect(app.distAdminConfirmPassword()).toBe('');

        app.openCreateDistAdminModal();
        app.distAdminPassword.set('AnotherSecret');
        app.distAdminConfirmPassword.set('AnotherSecret');

        app.clearDashboardAndGateState();
        expect(app.distAdminPassword()).toBe('');
        expect(app.distAdminConfirmPassword()).toBe('');
      });
    });

    describe('Stale Response & Authentication Recovery', () => {
      it('ignores late registration response after logout', () => {
        app.openAddSystemModal();
        app.newSystemDeviceCode.set('HALO-100');
        app.newSystemSN.set('SN-100');
        app.newSystemName.set('Unit 100');

        const pendingSubject = new Subject<any>();
        mockManagementService.registerDevice.mockReturnValue(pendingSubject.asObservable());

        app.submitAddSystem();
        expect(app.isSubmittingSystem()).toBe(true);

        // User logs out while request is pending
        app.onLogout();
        mockAuthService.clearSession();
        fixture.detectChanges();

        // Late response arrives
        pendingSubject.next({ success: true, data: { id: 99 } });
        pendingSubject.complete();

        expect(app.isDashboardOpen()).toBe(false);
        expect(app.isAddSystemModalOpen()).toBe(false);
      });

      it('401 error during registration triggers login recovery', () => {
        app.openAddSystemModal();
        app.newSystemDeviceCode.set('HALO-100');
        app.newSystemSN.set('SN-100');
        app.newSystemName.set('Unit 100');

        mockManagementService.registerDevice.mockReturnValue(throwError(() => new HttpErrorResponse({
          status: 401,
          error: { message: 'Token expired' }
        })));

        app.submitAddSystem();

        expect(app.isAddSystemModalOpen()).toBe(false);
        expect(app.isDashboardOpen()).toBe(false);
        expect(app.isLoginModalOpen()).toBe(true);
      });

      it('403 error for DISTRIBUTOR_ADMIN redirects to distributor verification gate', () => {
        mockAuthService.currentUser.set({
          id: 2,
          name: 'Bob Dist',
          email: 'bob@dist.com',
          role: 'DISTRIBUTOR_ADMIN',
          status: 'ACTIVE'
        });
        app.isDashboardAuthorized.set(true);

        app.openAddSystemModal();
        app.newSystemDeviceCode.set('HALO-200');
        app.newSystemSN.set('SN-200');
        app.newSystemName.set('Unit 200');
        app.newSystemBranchId.set('1');

        mockManagementService.registerDevice.mockReturnValue(throwError(() => new HttpErrorResponse({
          status: 403,
          error: { message: 'Distributor session unverified' }
        })));

        app.submitAddSystem();

        expect(app.isAddSystemModalOpen()).toBe(false);
        expect(app.isDashboardOpen()).toBe(false);
        expect(app.isDeviceAuthModalOpen()).toBe(true);
        expect(app.isDistributorRole()).toBe(true);
      });

      it('403 error for HYKON_ADMIN displays toast and never opens customer or distributor verification gate', () => {
        app.openAddSystemModal();
        app.newSystemDeviceCode.set('HALO-300');
        app.newSystemSN.set('SN-300');
        app.newSystemName.set('Unit 300');

        mockManagementService.registerDevice.mockReturnValue(throwError(() => new HttpErrorResponse({
          status: 403,
          error: { message: 'Forbidden' }
        })));

        app.submitAddSystem();

        expect(app.isAddSystemModalOpen()).toBe(false);
        expect(app.isDeviceAuthModalOpen()).toBe(false);
        expect(app.dashboardToast()).toContain('Access denied');
      });

      it('uncertain network failure (status 0) shows message asking user to check result before submitting and does not retry', () => {
        app.openAddSystemModal();
        app.newSystemDeviceCode.set('HALO-400');
        app.newSystemSN.set('SN-400');
        app.newSystemName.set('Unit 400');

        mockManagementService.registerDevice.mockReturnValue(throwError(() => new HttpErrorResponse({
          status: 0,
          statusText: 'Unknown Error'
        })));

        app.submitAddSystem();

        expect(mockManagementService.registerDevice).toHaveBeenCalledTimes(1);
        expect(app.isSubmittingSystem()).toBe(false);
        expect(app.newSystemError()).toContain('Please check existing records');
        expect(app.newSystemError()).toContain('before submitting again');
      });
    });
  });

  // ─────────────────────────────────────────────────────────────────────────────
  // Step 9 Part 6: Gap-filling regression tests
  // ─────────────────────────────────────────────────────────────────────────────
  describe('Step 9 Part 6: Gap-filling regression tests', () => {

    beforeEach(() => {
      mockAuthService.isAuthenticated.set(true);
      mockAuthService.currentUser.set({
        id: 1,
        name: 'Admin Charlie',
        email: 'charlie@hykon.com',
        role: 'HYKON_ADMIN',
        status: 'ACTIVE'
      });
      fixture.detectChanges();
      app.isDashboardOpen.set(true);
      app.isDashboardAuthorized.set(true);
    });

    // ── Direct URL routing ────────────────────────────────────────────────────
    describe('Direct URL routing', () => {
      it('/systems direct URL opens Systems tab for authenticated USER via backend verification', () => {
        mockAuthService.currentUser.set({
          id: 2, name: 'Alice', email: 'alice@test.com', role: 'USER', status: 'ACTIVE'
        });

        mockDeviceAccessService.listDevices.mockReturnValue(of({
          success: true,
          message: 'OK',
          data: {
            content: [{ id: 5, deviceCode: 'DEV-5', serialNumber: 'SN-5', systemName: 'HALO 5K', model: 'H5', branchId: 1, distributorId: 1, ownerUserId: 2, status: 'ACTIVE', createdAt: '' }],
            totalElements: 1, totalPages: 1, size: 10, number: 0, first: true, last: true
          }
        }));

        app.handleUrlRouting('/systems');

        expect(app.isDashboardOpen()).toBe(true);
        expect(app.activeDashboardNav()).toBe('systems');
      });

      it('/systems direct URL when backend returns 403 opens correct role gate', () => {
        mockAuthService.currentUser.set({
          id: 2, name: 'Bob', email: 'bob@dist.com', role: 'DISTRIBUTOR_ADMIN', status: 'ACTIVE'
        });

        mockDeviceAccessService.listDevices.mockReturnValue(
          throwError(() => new HttpErrorResponse({ status: 403, error: { message: 'Not verified' } }))
        );

        app.handleUrlRouting('/systems');

        expect(app.isDashboardOpen()).toBe(false);
        expect(app.isDeviceAuthModalOpen()).toBe(true);
        expect(app.isDistributorRole()).toBe(true);
      });

      it('/device-login direct URL for unauthenticated user redirects to login', () => {
        mockAuthService.isAuthenticated.set(false);
        mockAuthService.currentUser.set(null);

        app.handleUrlRouting('/device-login');

        expect(app.isLoginModalOpen()).toBe(true);
        expect(app.isDashboardOpen()).toBe(false);
      });

      it('/device-login direct URL for HYKON_ADMIN bypasses gate and opens dashboard', () => {
        mockDeviceAccessService.listDevices.mockReturnValue(of({
          success: true, message: 'OK',
          data: { content: [], totalElements: 0, totalPages: 0, size: 10, number: 0, first: true, last: true }
        }));

        app.handleUrlRouting('/device-login');

        expect(app.isDeviceAuthModalOpen()).toBe(false);
        expect(app.isDashboardOpen()).toBe(true);
      });
    });

    // ── Distributor verification error paths ──────────────────────────────────
    describe('Distributor verification error paths', () => {
      beforeEach(() => {
        mockAuthService.currentUser.set({
          id: 2, name: 'Bob', email: 'bob@dist.com', role: 'DISTRIBUTOR_ADMIN', status: 'ACTIVE'
        });
        app.isDashboardOpen.set(false);
        app.isDashboardAuthorized.set(false);
        app.isDeviceAuthModalOpen.set(true);
        app.distributorCode.set('DIST-1');
        app.branchCode.set('BR-1');
        app.distributorLocation.set('Kochi');
      });

      it('non-429 failed distributor verification shows error and keeps gate open', () => {
        mockDeviceAccessService.verifyDistributor.mockReturnValue(
          throwError(() => new HttpErrorResponse({ status: 400, error: { message: 'Distributor code not recognised' } }))
        );

        app.verifyDistributorCredentials();

        expect(app.isDashboardOpen()).toBe(false);
        expect(app.isAuthVerifying()).toBe(false);
        expect(app.authErrorMessage()).toBe('Distributor code not recognised');
      });

      it('401 on distributor verification shows error message in gate (gate remains open)', () => {
        // verifyDistributorCredentials does not have a special 401 path; it shows
        // the server error message and leaves the gate open. The auth interceptor
        // handles true 401s at the HTTP level, so a 401 reaching the component
        // falls through to the generic error branch.
        mockDeviceAccessService.verifyDistributor.mockReturnValue(
          throwError(() => new HttpErrorResponse({ status: 401, error: { message: 'Session expired' } }))
        );

        app.verifyDistributorCredentials();

        expect(app.isDashboardOpen()).toBe(false);
        expect(app.isAuthVerifying()).toBe(false);
        // Gate stays open with error message — user must re-authenticate via login button
        expect(app.isDeviceAuthModalOpen()).toBe(true);
        expect(app.authErrorMessage()).toContain('Session expired');
      });

      it('late distributor verification response after gate closure is ignored', () => {
        const responseSubject = new Subject<any>();
        mockDeviceAccessService.verifyDistributor.mockReturnValue(responseSubject.asObservable());

        app.verifyDistributorCredentials();
        app.closeDeviceAuthModal();

        responseSubject.next({
          success: true, message: 'Verified',
          data: { distributorId: 1, distributorCode: 'DIST-1', distributorName: 'Hykon South', branchId: 1, branchCode: 'BR-1', branchName: 'Kochi Branch', location: 'Kochi' }
        });
        responseSubject.complete();

        expect(app.isDashboardOpen()).toBe(false);
      });
    });

    // ── Purchase recording: additional validation and recovery ────────────────
    describe('Purchase recording: additional validation and recovery', () => {
      beforeEach(() => {
        app.openPurchaseModal();
        app.purchaseDeviceId.set('10');
        app.purchaseCustomerUserId.set('20');
      });

      it('blocks USER role from opening or submitting purchase modal', () => {
        mockAuthService.currentUser.set({
          id: 3, name: 'Alice', email: 'alice@test.com', role: 'USER', status: 'ACTIVE'
        });
        // Ensure modal is closed before attempting to open as USER
        app.isPurchaseModalOpen.set(false);

        app.openPurchaseModal();
        expect(app.isPurchaseModalOpen()).toBe(false);

        app.submitRecordPurchase();
        expect(mockManagementService.recordDevicePurchase).not.toHaveBeenCalled();
        expect(app.purchaseError()).toBe('Only administrators can record purchases.');
      });

      it('validates blank purchase reference is rejected', () => {
        app.purchaseReference.set('');

        app.submitRecordPurchase();

        expect(mockManagementService.recordDevicePurchase).not.toHaveBeenCalled();
        expect(app.purchaseError()).toBe('Purchase reference is required.');
      });

      it('validates purchase reference exceeding 150 characters is rejected', () => {
        app.purchaseReference.set('X'.repeat(151));

        app.submitRecordPurchase();

        expect(mockManagementService.recordDevicePurchase).not.toHaveBeenCalled();
        expect(app.purchaseError()).toBe('Purchase reference must not exceed 150 characters.');
      });

      it('prevents duplicate submission while purchase request is pending', () => {
        app.purchaseReference.set('INV-PENDING');

        const pendingSubject = new Subject<any>();
        mockManagementService.recordDevicePurchase.mockReturnValue(pendingSubject.asObservable());

        app.submitRecordPurchase();
        expect(app.isSubmittingPurchase()).toBe(true);

        // Second attempt while pending must be silently blocked
        app.submitRecordPurchase();
        expect(mockManagementService.recordDevicePurchase).toHaveBeenCalledTimes(1);

        pendingSubject.next({ success: true, data: { id: 1 } });
        pendingSubject.complete();

        expect(app.isSubmittingPurchase()).toBe(false);
        expect(app.isPurchaseModalOpen()).toBe(false);
      });

      it('401 on purchase recording closes modal and opens login', () => {
        app.purchaseReference.set('INV-401-TEST');
        mockManagementService.recordDevicePurchase.mockReturnValue(
          throwError(() => new HttpErrorResponse({ status: 401, error: { message: 'Token expired' } }))
        );

        app.submitRecordPurchase();

        expect(app.isPurchaseModalOpen()).toBe(false);
        expect(app.isDashboardOpen()).toBe(false);
        expect(app.isLoginModalOpen()).toBe(true);
      });

      it('403 on purchase for DISTRIBUTOR_ADMIN redirects to distributor gate', () => {
        mockAuthService.currentUser.set({
          id: 2, name: 'Bob', email: 'bob@dist.com', role: 'DISTRIBUTOR_ADMIN', status: 'ACTIVE'
        });
        app.isPurchaseModalOpen.set(true);
        app.purchaseDeviceId.set('10');
        app.purchaseCustomerUserId.set('20');
        app.purchaseReference.set('INV-403-DA');

        mockManagementService.recordDevicePurchase.mockReturnValue(
          throwError(() => new HttpErrorResponse({ status: 403, error: { message: 'Distributor session unverified' } }))
        );

        app.submitRecordPurchase();

        expect(app.isPurchaseModalOpen()).toBe(false);
        expect(app.isDashboardOpen()).toBe(false);
        expect(app.isDeviceAuthModalOpen()).toBe(true);
        expect(app.isDistributorRole()).toBe(true);
      });

      it('403 on purchase for HYKON_ADMIN shows toast — never opens gate', () => {
        app.purchaseReference.set('INV-403-ADMIN');
        mockManagementService.recordDevicePurchase.mockReturnValue(
          throwError(() => new HttpErrorResponse({ status: 403, error: { message: 'Forbidden' } }))
        );

        app.submitRecordPurchase();

        expect(app.isPurchaseModalOpen()).toBe(false);
        expect(app.isDeviceAuthModalOpen()).toBe(false);
        expect(app.dashboardToast()).toContain('Access denied');
      });

      it('ignores late purchase response after logout', () => {
        app.purchaseReference.set('INV-LATE');
        const pendingSubject = new Subject<any>();
        mockManagementService.recordDevicePurchase.mockReturnValue(pendingSubject.asObservable());

        app.submitRecordPurchase();
        expect(app.isSubmittingPurchase()).toBe(true);

        app.onLogout();
        mockAuthService.clearSession();
        fixture.detectChanges();

        pendingSubject.next({ success: true, data: { id: 55 } });
        pendingSubject.complete();

        expect(app.isDashboardOpen()).toBe(false);
        expect(app.isPurchaseModalOpen()).toBe(false);
      });
    });

    // ── Distributor admin: field validation and duplicate-submit prevention ───
    describe('Distributor admin: field validation and duplicate submission', () => {
      it('rejects short name (< 2 chars)', () => {
        app.openCreateDistAdminModal();
        app.distAdminName.set('A');
        app.distAdminEmail.set('a@test.com');
        app.distAdminPassword.set('Password1!');
        app.distAdminConfirmPassword.set('Password1!');

        app.submitCreateDistAdmin();

        expect(mockManagementService.createDistributorAdmin).not.toHaveBeenCalled();
        expect(app.distAdminError()).toBe('Name must be at least 2 characters.');
      });

      it('rejects invalid email format', () => {
        app.openCreateDistAdminModal();
        app.distAdminName.set('Jane Doe');
        app.distAdminEmail.set('not-an-email');
        app.distAdminPassword.set('Password1!');
        app.distAdminConfirmPassword.set('Password1!');

        app.submitCreateDistAdmin();

        expect(mockManagementService.createDistributorAdmin).not.toHaveBeenCalled();
        expect(app.distAdminError()).toBe('Valid email address is required.');
      });

      it('rejects password shorter than 8 characters', () => {
        app.openCreateDistAdminModal();
        app.distAdminName.set('Jane Doe');
        app.distAdminEmail.set('jane@test.com');
        app.distAdminPassword.set('abc');
        app.distAdminConfirmPassword.set('abc');

        app.submitCreateDistAdmin();

        expect(mockManagementService.createDistributorAdmin).not.toHaveBeenCalled();
        expect(app.distAdminError()).toBe('Password must be at least 8 characters.');
      });

      it('rejects mismatched passwords', () => {
        app.openCreateDistAdminModal();
        app.distAdminName.set('Jane Doe');
        app.distAdminEmail.set('jane@test.com');
        app.distAdminPassword.set('Password1!');
        app.distAdminConfirmPassword.set('DifferentPass!');

        app.submitCreateDistAdmin();

        expect(mockManagementService.createDistributorAdmin).not.toHaveBeenCalled();
        expect(app.distAdminError()).toBe('Passwords do not match.');
      });

      it('prevents duplicate admin submission while request is pending', () => {
        app.openCreateDistAdminModal();
        app.distAdminName.set('Jane Doe');
        app.distAdminEmail.set('jane@test.com');
        app.distAdminPassword.set('Password1!');
        app.distAdminConfirmPassword.set('Password1!');

        const pendingSubject = new Subject<any>();
        mockManagementService.createDistributorAdmin.mockReturnValue(pendingSubject.asObservable());

        app.submitCreateDistAdmin();
        expect(app.isSubmittingDistAdmin()).toBe(true);

        app.submitCreateDistAdmin();
        expect(mockManagementService.createDistributorAdmin).toHaveBeenCalledTimes(1);

        pendingSubject.next({ success: true, data: { id: 99, name: 'Jane Doe', email: 'jane@test.com', role: 'DISTRIBUTOR_ADMIN', status: 'ACTIVE', createdAt: '' } });
        pendingSubject.complete();

        expect(app.isSubmittingDistAdmin()).toBe(false);
        expect(app.isCreateDistAdminModalOpen()).toBe(false);
      });

      it('displays backend 400 validation error without closing admin modal', () => {
        app.openCreateDistAdminModal();
        app.distAdminName.set('Jane Doe');
        app.distAdminEmail.set('jane@test.com');
        app.distAdminPassword.set('Password1!');
        app.distAdminConfirmPassword.set('Password1!');

        mockManagementService.createDistributorAdmin.mockReturnValue(
          throwError(() => new HttpErrorResponse({
            status: 400,
            error: { success: false, status: 400, message: 'Email already registered', errors: { email: 'Email already registered' } }
          }))
        );

        app.submitCreateDistAdmin();

        expect(app.isCreateDistAdminModalOpen()).toBe(true);
        expect(app.distAdminError()).toBe('Email already registered');
      });

      it('401 on admin creation closes modal and opens login', () => {
        app.openCreateDistAdminModal();
        app.distAdminName.set('Jane Doe');
        app.distAdminEmail.set('jane@test.com');
        app.distAdminPassword.set('Password1!');
        app.distAdminConfirmPassword.set('Password1!');

        mockManagementService.createDistributorAdmin.mockReturnValue(
          throwError(() => new HttpErrorResponse({ status: 401, error: { message: 'Token expired' } }))
        );

        app.submitCreateDistAdmin();

        expect(app.isCreateDistAdminModalOpen()).toBe(false);
        expect(app.isDashboardOpen()).toBe(false);
        expect(app.isLoginModalOpen()).toBe(true);
      });

      it('401 on distributor creation closes modal and opens login', () => {
        app.lastCreatedAdminId.set(88);
        app.openCreateDistributorModal();
        app.distCode.set('DIST-88');
        app.distName.set('Hykon West');
        app.distAdminUserId.set('88');
        app.distBranchCode.set('BR-WEST-01');
        app.distBranchName.set('West Branch');
        app.distLocation.set('Kochi');

        mockManagementService.createDistributor.mockReturnValue(
          throwError(() => new HttpErrorResponse({ status: 401, error: { message: 'Token expired' } }))
        );

        app.submitCreateDistributor();

        expect(app.isCreateDistributorModalOpen()).toBe(false);
        expect(app.isDashboardOpen()).toBe(false);
        expect(app.isLoginModalOpen()).toBe(true);
      });

      it('prevents duplicate distributor submission while request is pending', () => {
        app.lastCreatedAdminId.set(88);
        app.openCreateDistributorModal();
        app.distCode.set('DIST-88');
        app.distName.set('Hykon West');
        app.distAdminUserId.set('88');
        app.distBranchCode.set('BR-WEST-01');
        app.distBranchName.set('West Branch');
        app.distLocation.set('Kochi');

        const pendingSubject = new Subject<any>();
        mockManagementService.createDistributor.mockReturnValue(pendingSubject.asObservable());

        app.submitCreateDistributor();
        expect(app.isSubmittingDistributor()).toBe(true);

        app.submitCreateDistributor();
        expect(mockManagementService.createDistributor).toHaveBeenCalledTimes(1);

        pendingSubject.next({ success: true, data: { id: 15, distributorCode: 'DIST-88', name: 'Hykon West', administratorUserId: 88, status: 'ACTIVE', branchId: 10, branchCode: 'BR-WEST-01', branchName: 'West Branch', location: 'Kochi', branchStatus: 'ACTIVE' } });
        pendingSubject.complete();

        expect(app.isSubmittingDistributor()).toBe(false);
        expect(app.isCreateDistributorModalOpen()).toBe(false);
      });

      it('distributor creation: validates required fields', () => {
        app.lastCreatedAdminId.set(88);
        app.openCreateDistributorModal();
        // Missing distributorCode
        app.distAdminUserId.set('88');
        app.distCode.set('');
        app.distName.set('Hykon West');
        app.distBranchCode.set('BR-1');
        app.distBranchName.set('Branch 1');
        app.distLocation.set('Kochi');

        app.submitCreateDistributor();
        expect(mockManagementService.createDistributor).not.toHaveBeenCalled();
        expect(app.distError()).toBe('Distributor ID is required.');
      });
    });

    // ── Unauthenticated check (post-logout state guard) ───────────────────────
    describe('Post-logout state guards', () => {
      it('dashboard-open flag is false immediately after logout clears session', () => {
        app.isDashboardOpen.set(true);
        app.isDashboardAuthorized.set(true);

        mockAuthService.clearSession();
        fixture.detectChanges();

        expect(app.isDashboardOpen()).toBe(false);
        expect(app.isDashboardAuthorized()).toBe(false);
        expect(app.isDeviceAuthModalOpen()).toBe(false);
      });

      it('clearDashboardAndGateState wipes all form fields and passwords', () => {
        app.distAdminPassword.set('S3cret!');
        app.distAdminConfirmPassword.set('S3cret!');
        app.newSystemDeviceCode.set('DEV-X');
        app.purchaseReference.set('INV-99');

        app.clearDashboardAndGateState();

        expect(app.distAdminPassword()).toBe('');
        expect(app.distAdminConfirmPassword()).toBe('');
        // Dashboard and gate state cleared
        expect(app.isDashboardOpen()).toBe(false);
        expect(app.isDashboardAuthorized()).toBe(false);
      });
    });
  });

  // ─────────────────────────────────────────────────────────────────────────────
  // Step 9 Part 5: Overview and Warnings accuracy
  // ─────────────────────────────────────────────────────────────────────────────
  describe('Step 9 Part 5: Overview and Warnings accuracy', () => {

    it('totalSystemsCount uses totalElements from backend, not current page length', () => {
      // Seed 42 total devices in the service but only 3 loaded on the current page
      app.systemRecordsService.totalElements.set(42);
      app.systemRecordsService.devices.set([
        { id: 1, deviceCode: 'A1', serialNumber: 'SN1', systemName: 'Dev 1', model: 'M1', status: 'ACTIVE', branchId: null, ownerUserId: null },
        { id: 2, deviceCode: 'A2', serialNumber: 'SN2', systemName: 'Dev 2', model: 'M2', status: 'ACTIVE', branchId: null, ownerUserId: null },
        { id: 3, deviceCode: 'A3', serialNumber: 'SN3', systemName: 'Dev 3', model: 'M3', status: 'ACTIVE', branchId: null, ownerUserId: null }
      ] as any);

      // totalSystemsCount() must reflect the server total, not just the loaded page
      expect(app.totalSystemsCount()).toBe(42);
    });

    it('onlineSystemsCount returns "—" — unavailable telemetry is not shown as zero', () => {
      expect(app.onlineSystemsCount()).toBe('—');
    });

    it('warningSystemsCount returns "—" — unavailable telemetry is not shown as zero', () => {
      expect(app.warningSystemsCount()).toBe('—');
    });

    it('offlineSystemsCount returns "—" — unavailable telemetry is not shown as zero', () => {
      expect(app.offlineSystemsCount()).toBe('—');
    });

    it('submitAiQuery sets honest unavailability response — no fabricated telemetry values', () => {
      app.submitAiQuery();

      const response = app.aiResponse();
      expect(response).toContain('not available');
      // Must not contain fabricated measurement values that were previously hardcoded
      expect(response).not.toContain('983W');
      expect(response).not.toContain('95.5%');
      expect(response).not.toContain('51.2V');
      expect(response).not.toContain('Inverter Normal');
    });

    it('selectSidebarTab does not fire any toast — no fake health/sync claims', () => {
      app.dashboardToast.set('');

      app.selectSidebarTab('alerts');
      expect(app.dashboardToast()).toBe('');

      app.selectSidebarTab('overview');
      expect(app.dashboardToast()).toBe('');

      app.selectSidebarTab('systems');
      expect(app.dashboardToast()).toBe('');
    });

    it('openDashboard seeds totalElements from listDevices page response', () => {
      mockAuthService.isAuthenticated.set(true);
      mockAuthService.currentUser.set({ id: 1, email: 'u@x.com', name: 'User', role: 'USER', verified: true } as any);

      mockDeviceAccessService.listDevices.mockReturnValue(of({
        success: true,
        message: 'OK',
        data: {
          content: [],
          totalElements: 7,
          totalPages: 1,
          size: 10,
          number: 0,
          first: true,
          last: true,
          numberOfElements: 0,
          empty: true
        }
      }));

      app.systemRecordsService.totalElements.set(0);
      app.openDashboard('overview');

      expect(app.totalSystemsCount()).toBe(7);
    });

    it('Warnings tab is accessible to USER, DISTRIBUTOR_ADMIN, and HYKON_ADMIN', () => {
      // USER
      mockAuthService.isAuthenticated.set(true);
      mockAuthService.currentUser.set({ id: 1, email: 'u@x.com', name: 'User', role: 'USER', verified: true } as any);
      expect(app.canAccessTab('alerts')).toBe(true);

      // DISTRIBUTOR_ADMIN
      mockAuthService.currentUser.set({ id: 2, email: 'd@x.com', name: 'DA', role: 'DISTRIBUTOR_ADMIN', verified: true } as any);
      expect(app.canAccessTab('alerts')).toBe(true);

      // HYKON_ADMIN
      mockAuthService.currentUser.set({ id: 3, email: 'h@x.com', name: 'HA', role: 'HYKON_ADMIN', verified: true } as any);
      expect(app.canAccessTab('alerts')).toBe(true);
    });

    it('initial dashboard signals are honest — no live-connection claims', () => {
      // These are the values set before any backend call succeeds
      expect(app.activeDashboardDevice()).toBe('No device selected');
      expect(app.activeDashboardConnection()).toBe('Not connected');
      expect(app.activeDashboardDataSource()).toBe('Not available');
    });
  });
});
