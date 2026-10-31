
import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import {
  EMPTY,
  Observable,
  catchError,
  defer,
  finalize,
  mergeMap,
  of,
  shareReplay,
  throwError,
} from 'rxjs';

const API_BASE_URL = 'http://localhost:8080/api/v1/auth';

// Standard API response
export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
}

// Signup request
export interface RegisterRequest {
  name: string;
  email: string;
  password: string;
  confirmPassword: string;
}

// Signup response
export interface RegisterResponse {
  id: number;
  name: string;
  email: string;
  role: string;
  status: string;
  createdAt: string;
}

// Login request
export interface LoginRequest {
  email: string;
  password: string;
}

// User details maintained after login
export interface AuthUser {
  id: number;
  name: string;
  email: string;
  role: string;
  status: string;
}

// Login response from the existing backend
export interface LoginResponse {
  id: number;
  name: string;
  email: string;
  role: string;
  status: string;
  accessToken: string;
  refreshToken: string;
  tokenType: string;
}

// Refresh-token request
export interface RefreshTokenRequest {
  refreshToken: string;
}

// Refresh-token response from the existing backend
export interface RefreshTokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
}

// Logout response from the existing backend
export interface LogoutResponse {
  message: string;
}

const STORAGE_KEY = 'hykon_auth_session';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly accessTokenState = signal<string | null>(null);
  private refreshTokenValue: string | null = null;
  private readonly currentUserState = signal<AuthUser | null>(null);

  // Changes only at login/logout/session termination, not token rotation.
  private sessionVersion = 0;
  private refreshInProgress$: Observable<
    ApiResponse<RefreshTokenResponse>
  > | null = null;

  readonly currentUser = this.currentUserState.asReadonly();
  readonly isAuthenticated = computed(
    () => this.accessTokenState() !== null
  );
  readonly userRole = computed(
    () => this.currentUserState()?.role ?? null
  );

  constructor() {
    this.restoreSessionFromStorage();
  }

  getAccessToken(): string | null {
    return this.accessTokenState();
  }

  getSessionVersion(): number {
    return this.sessionVersion;
  }

  isCurrentSession(version: number): boolean {
    return version === this.sessionVersion;
  }

  register(
    request: RegisterRequest
  ): Observable<ApiResponse<RegisterResponse>> {
    return this.http.post<ApiResponse<RegisterResponse>>(
      `${API_BASE_URL}/register`, request
    );
  }

  login(request: LoginRequest): Observable<ApiResponse<LoginResponse>> {
    return defer(() => {
      // Invalidate older login/refresh requests, including the same account.
      this.clearSession();
      const version = this.sessionVersion;

      return this.http.post<ApiResponse<LoginResponse>>(
        `${API_BASE_URL}/login`, request
      ).pipe(
        mergeMap(response => {
          if (!this.isCurrentSession(version)) return EMPTY;
          this.setSession(response.data);
          return of(response);
        })
      );
    });
  }

  refreshSession(): Observable<ApiResponse<RefreshTokenResponse>> {
    return defer(() => {
      if (this.refreshInProgress$) {
        return this.refreshInProgress$;
      }

      const refreshToken = this.refreshTokenValue;
      if (!refreshToken) {
        this.clearSession();
        return throwError(
          () => new Error('Please log in again.')
        );
      }

      const version = this.sessionVersion;
      let sharedRequest$: Observable<ApiResponse<RefreshTokenResponse>>;

      sharedRequest$ = this.http.post<ApiResponse<RefreshTokenResponse>>(
        `${API_BASE_URL}/refresh`, { refreshToken }
      ).pipe(
        mergeMap(response => {
          if (!this.isCurrentSession(version)) return EMPTY;

          if (!response.data?.accessToken ||
              !response.data?.refreshToken) {
            return throwError(
              () => new Error('Invalid refresh response.')
            );
          }

          this.accessTokenState.set(response.data.accessToken);
          this.refreshTokenValue = response.data.refreshToken;
          this.persistSession();
          return of(response);
        }),
        catchError((error: unknown) => {
          // An old refresh failure must not clear a newer login.
          if (!this.isCurrentSession(version)) return EMPTY;
          this.clearSession();
          return throwError(() => error);
        }),
        finalize(() => {
          if (this.refreshInProgress$ === sharedRequest$) {
            this.refreshInProgress$ = null;
          }
        }),
        shareReplay({ bufferSize: 1, refCount: false })
      );

      this.refreshInProgress$ = sharedRequest$;
      return sharedRequest$;
    });
  }

  logout(): Observable<ApiResponse<LogoutResponse>> {
    return defer(() => {
      const refreshToken = this.refreshTokenValue;

      // Close local access immediately, even if the server is unavailable.
      this.clearSession();

      if (!refreshToken) {
        return throwError(
          () => new Error('No active refresh token is available.')
        );
      }

      // Public endpoint: token is supplied in the body.
      // A late logout response never clears a newer login.
      return this.http.post<ApiResponse<LogoutResponse>>(
        `${API_BASE_URL}/logout`, { refreshToken }
      );
    });
  }

  private setSession(response: LoginResponse): void {
    this.accessTokenState.set(response.accessToken);
    this.refreshTokenValue = response.refreshToken;
    this.currentUserState.set({
      id: response.id,
      name: response.name,
      email: response.email,
      role: response.role,
      status: response.status,
    });
    this.persistSession();
  }

  private persistSession(): void {
    try {
      if (typeof window === 'undefined') return;

      const accessToken = this.accessTokenState();
      const user = this.currentUserState();

      if (accessToken && this.refreshTokenValue && user) {
        window.localStorage.setItem(
          STORAGE_KEY,
          JSON.stringify({
            accessToken,
            refreshToken: this.refreshTokenValue,
            user,
          })
        );
      }
    } catch {
      // Storage may be unavailable; the in-memory session still works.
    }
  }

  private restoreSessionFromStorage(): void {
    try {
      if (typeof window === 'undefined') return;

      const raw = window.localStorage.getItem(STORAGE_KEY);
      if (!raw) return;

      const parsed = JSON.parse(raw);
      const user = parsed?.user;
      const valid =
        typeof parsed?.accessToken === 'string' &&
        parsed.accessToken.trim().length > 0 &&
        typeof parsed?.refreshToken === 'string' &&
        parsed.refreshToken.trim().length > 0 &&
        Number.isSafeInteger(user?.id) &&
        user.id > 0 &&
        typeof user?.name === 'string' &&
        typeof user?.email === 'string' &&
        ['USER', 'DISTRIBUTOR_ADMIN', 'HYKON_ADMIN'].includes(user?.role) &&
        user?.status === 'ACTIVE';

      if (!valid) {
        this.clearSession();
        return;
      }

      // Restored values are only a local hint.
      // Protected backend requests still validate the real session.
      this.accessTokenState.set(parsed.accessToken);
      this.refreshTokenValue = parsed.refreshToken;
      this.currentUserState.set({
        id: user.id,
        name: user.name,
        email: user.email,
        role: user.role,
        status: user.status,
      });
    } catch {
      this.clearSession();
    }
  }

  private clearStorage(): void {
    try {
      if (typeof window !== 'undefined') {
        window.localStorage.removeItem(STORAGE_KEY);
      }
    } catch {
      // Storage may be unavailable.
    }
  }

  clearSession(): void {
    this.sessionVersion++;
    this.refreshInProgress$ = null;
    this.accessTokenState.set(null);
    this.refreshTokenValue = null;
    this.currentUserState.set(null);
    this.clearStorage();
  }
}
