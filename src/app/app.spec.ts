import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { Subject, of, throwError } from 'rxjs';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { App } from './app';
import { AuthService, AuthUser } from './services/auth.service';
import { DeviceAccessService } from './services/device-access.service';

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

    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: mockAuthService },
        { provide: DeviceAccessService, useValue: mockDeviceAccessService }
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
});
