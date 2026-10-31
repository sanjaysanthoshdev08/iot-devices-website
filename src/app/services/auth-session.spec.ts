import { TestBed } from '@angular/core/testing';
import {
  HttpClient,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { AuthService } from './auth.service';
import { authInterceptor } from '../interceptors/auth.interceptor';

const BASE = 'http://localhost:8080/api/v1';
const STORAGE = 'hykon_auth_session';

describe('Authentication session lifecycle', () => {
  let auth: AuthService;
  let http: HttpClient;
  let backend: HttpTestingController;

  const session = (accessToken = 'test-access-1',
                   refreshToken = 'test-refresh-1') => ({
    success: true,
    message: 'Login successful',
    data: {
      id: 16,
      name: 'Session Test',
      email: 'session-test@example.com',
      role: 'USER',
      status: 'ACTIVE',
      accessToken,
      refreshToken,
      tokenType: 'Bearer',
    },
  });

  function login(access = 'test-access-1', refresh = 'test-refresh-1') {
    auth.login({
      email: 'session-test@example.com',
      password: 'Test-only-password',
    }).subscribe();

    const request = backend.expectOne(`${BASE}/auth/login`);
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush(session(access, refresh));
  }

  beforeEach(() => {
    localStorage.removeItem(STORAGE);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    backend.verify();
    localStorage.removeItem(STORAGE);
  });

  it('attaches the token only to the intended backend API', () => {
    login();
    http.get(`${BASE}/devices`).subscribe();
    const request = backend.expectOne(`${BASE}/devices`);
    expect(request.request.headers.get('Authorization'))
      .toBe('Bearer test-access-1');
    request.flush({});
  });

  it.each([
    'https://example.com/api/v1/devices',
    'http://localhost:8081/api/v1/devices',
    'http://localhost:8080.evil.example/api/v1/devices',
    'http://localhost:8080/public',
    '/assets/example.json',
  ])('does not attach tokens to %s', url => {
    login();
    http.get(url).subscribe();
    const request = backend.expectOne(url);
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({});
  });

  it.each(['login', 'register', 'refresh', 'logout'])(
    'does not attach Bearer or auto-refresh for public %s requests',
    endpoint => {
      login();
      http.post(`${BASE}/auth/${endpoint}`, {}).subscribe({
        error: () => {},
      });
      const request = backend.expectOne(`${BASE}/auth/${endpoint}`);
      expect(request.request.headers.has('Authorization')).toBe(false);
      request.flush({}, { status: 401, statusText: 'Unauthorized' });
      backend.expectNone(`${BASE}/auth/refresh`);
    }
  );

  it('shares one refresh for concurrent expired protected requests', () => {
    login();
    const results: unknown[] = [];
    http.get(`${BASE}/devices`).subscribe(value => results.push(value));
    http.get(`${BASE}/profile`).subscribe(value => results.push(value));

    backend.expectOne(`${BASE}/devices`)
      .flush({}, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne(`${BASE}/profile`)
      .flush({}, { status: 401, statusText: 'Unauthorized' });

    const refreshes = backend.match(`${BASE}/auth/refresh`);
    expect(refreshes.length).toBe(1);
    expect(refreshes[0].request.headers.has('Authorization')).toBe(false);
    refreshes[0].flush({
      success: true,
      message: 'Refreshed',
      data: {
        accessToken: 'test-access-2',
        refreshToken: 'test-refresh-2',
        tokenType: 'Bearer',
      },
    });

    for (const url of [`${BASE}/devices`, `${BASE}/profile`]) {
      const retry = backend.expectOne(url);
      expect(retry.request.headers.get('Authorization'))
        .toBe('Bearer test-access-2');
      retry.flush({ ok: true });
    }
    expect(results.length).toBe(2);
    expect(auth.getAccessToken()).toBe('test-access-2');
    const saved = JSON.parse(localStorage.getItem(STORAGE)!);
    expect(saved.refreshToken).toBe('test-refresh-2');
  });

  it('reuses a rotated token for a delayed old-token 401', () => {
    login();
    http.get(`${BASE}/devices`).subscribe();
    http.get(`${BASE}/profile`).subscribe();

    const devices = backend.expectOne(`${BASE}/devices`);
    const profile = backend.expectOne(`${BASE}/profile`);
    devices.flush({}, { status: 401, statusText: 'Unauthorized' });

    backend.expectOne(`${BASE}/auth/refresh`).flush({
      success: true,
      data: {
        accessToken: 'test-access-2',
        refreshToken: 'test-refresh-2',
        tokenType: 'Bearer',
      },
    });
    backend.expectOne(`${BASE}/devices`).flush({});

    profile.flush({}, { status: 401, statusText: 'Unauthorized' });
    backend.expectNone(`${BASE}/auth/refresh`);
    const retry = backend.expectOne(`${BASE}/profile`);
    expect(retry.request.headers.get('Authorization'))
      .toBe('Bearer test-access-2');
    retry.flush({});
  });

  it('retries once and clears authentication on a final 401', () => {
    login();
    http.get(`${BASE}/devices`).subscribe({ error: () => {} });
    backend.expectOne(`${BASE}/devices`)
      .flush({}, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne(`${BASE}/auth/refresh`).flush({
      success: true,
      data: {
        accessToken: 'test-access-2',
        refreshToken: 'test-refresh-2',
        tokenType: 'Bearer',
      },
    });
    backend.expectOne(`${BASE}/devices`)
      .flush({}, { status: 401, statusText: 'Unauthorized' });

    backend.expectNone(`${BASE}/auth/refresh`);
    expect(auth.isAuthenticated()).toBe(false);
    expect(localStorage.getItem(STORAGE)).toBeNull();
  });

  it('does not refresh on a protected 403', () => {
    login();
    http.get(`${BASE}/devices`).subscribe({ error: () => {} });
    backend.expectOne(`${BASE}/devices`)
      .flush({}, { status: 403, statusText: 'Forbidden' });
    backend.expectNone(`${BASE}/auth/refresh`);
    expect(auth.isAuthenticated()).toBe(true);
  });

  it('clears the current session when refresh fails', () => {
    login();
    http.get(`${BASE}/devices`).subscribe({ error: () => {} });
    backend.expectOne(`${BASE}/devices`)
      .flush({}, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne(`${BASE}/auth/refresh`)
      .flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(auth.currentUser()).toBeNull();
    expect(auth.getAccessToken()).toBeNull();
    expect(localStorage.getItem(STORAGE)).toBeNull();
  });

  it('preserves the session version during successful token rotation', () => {
    login();
    const version = auth.getSessionVersion();
    auth.refreshSession().subscribe();
    backend.expectOne(`${BASE}/auth/refresh`).flush({
      success: true,
      data: {
        accessToken: 'test-access-2',
        refreshToken: 'test-refresh-2',
        tokenType: 'Bearer',
      },
    });
    expect(auth.getSessionVersion()).toBe(version);
    expect(auth.currentUser()?.id).toBe(16);
  });

  it('does not restore authentication from refresh after logout', () => {
    login();
    auth.refreshSession().subscribe();
    const refresh = backend.expectOne(`${BASE}/auth/refresh`);

    auth.logout().subscribe();
    expect(auth.isAuthenticated()).toBe(false);

    refresh.flush({
      success: true,
      data: {
        accessToken: 'stale-access',
        refreshToken: 'stale-refresh',
        tokenType: 'Bearer',
      },
    });
    backend.expectOne(`${BASE}/auth/logout`).flush({
      success: true, data: null,
    });
    expect(auth.isAuthenticated()).toBe(false);
    expect(localStorage.getItem(STORAGE)).toBeNull();
  });

  it('does not let an old refresh failure clear a newer same-account login', () => {
    login();
    auth.refreshSession().subscribe();
    const refresh = backend.expectOne(`${BASE}/auth/refresh`);

    login('new-login-access', 'new-login-refresh');
    refresh.flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(auth.getAccessToken()).toBe('new-login-access');
    expect(auth.isAuthenticated()).toBe(true);
  });

  it('does not let a late logout failure clear a newer login', () => {
    login();
    auth.logout().subscribe({ error: () => {} });
    const logout = backend.expectOne(`${BASE}/auth/logout`);

    login('new-login-access', 'new-login-refresh');
    logout.flush({}, { status: 500, statusText: 'Server Error' });

    expect(auth.getAccessToken()).toBe('new-login-access');
  });

  it('ignores an old protected response after a new login', () => {
    login();
    let received = false;
    http.get(`${BASE}/devices`).subscribe(() => received = true);
    const oldRequest = backend.expectOne(`${BASE}/devices`);

    login('new-login-access', 'new-login-refresh');
    oldRequest.flush({ old: true });
    expect(received).toBe(false);
  });

  it('ignores a superseded login response', () => {
    auth.login({
      email: 'session-test@example.com',
      password: 'Test-only-password',
    }).subscribe();
    const oldLogin = backend.expectOne(`${BASE}/auth/login`);

    login('new-login-access', 'new-login-refresh');
    oldLogin.flush(session('old-access', 'old-refresh'));
    expect(auth.getAccessToken()).toBe('new-login-access');
  });

  it('restores a valid stored session without changing its user', () => {
    login();
    const restored = TestBed.runInInjectionContext(() => new AuthService());
    expect(restored.isAuthenticated()).toBe(true);
    expect(restored.currentUser()?.email).toBe('session-test@example.com');
    expect(restored.getAccessToken()).toBe('test-access-1');
  });

  it('rejects malformed stored session data safely', () => {
    localStorage.setItem(STORAGE, '{"accessToken":123,"user":{}}');
    const restored = TestBed.runInInjectionContext(() => new AuthService());
    expect(restored.isAuthenticated()).toBe(false);
    expect(localStorage.getItem(STORAGE)).toBeNull();
  });
});