
import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import {
  Observable,
  catchError,
  finalize,
  tap,
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

  // Authentication state
  private readonly accessTokenState = signal<string | null>(null);
  private refreshTokenValue: string | null = null;

  // Public, read-only authentication state for Angular components.
  private readonly currentUserState = signal<AuthUser | null>(null);

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

  // Read the current access token when making protected API requests.
  getAccessToken(): string | null {
    return this.accessTokenState();
  }

  // Existing Signup API — behavior preserved.
  register(
    request: RegisterRequest
  ): Observable<ApiResponse<RegisterResponse>> {
    return this.http.post<ApiResponse<RegisterResponse>>(
      `${API_BASE_URL}/register`,
      request
    );
  }

  // Existing Login API, now also initializes authentication state.
  login(
    request: LoginRequest
  ): Observable<ApiResponse<LoginResponse>> {
    return this.http
      .post<ApiResponse<LoginResponse>>(
        `${API_BASE_URL}/login`,
        request
      )
      .pipe(
        tap((response) => {
          this.setSession(response.data);
        })
      );
  }

  // Exchange the current refresh token for rotated tokens.
  refreshSession(): Observable<ApiResponse<RefreshTokenResponse>> {
    const refreshToken = this.refreshTokenValue;

    if (!refreshToken) {
      this.clearSession();

      return throwError(
        () => new Error('No refresh token is available. Please log in again.')
      );
    }

    return this.http
      .post<ApiResponse<RefreshTokenResponse>>(
        `${API_BASE_URL}/refresh`,
        { refreshToken }
      )
      .pipe(
        tap((response) => {
          // Store the newly issued tokens after successful rotation.
          this.accessTokenState.set(response.data.accessToken);
          this.refreshTokenValue = response.data.refreshToken;
          this.persistSession();
        }),
        catchError((error: unknown) => {
          // A failed refresh requires the user to authenticate again.
          this.clearSession();
          return throwError(() => error);
        })
      );
  }

  // Invalidate the refresh token through the existing backend logout API.
  logout(): Observable<ApiResponse<LogoutResponse>> {
    const refreshToken = this.refreshTokenValue;

    if (!refreshToken) {
      this.clearSession();

      return throwError(
        () => new Error('No active refresh token is available.')
      );
    }

    return this.http
      .post<ApiResponse<LogoutResponse>>(
        `${API_BASE_URL}/logout`,
        { refreshToken }
      )
      .pipe(
        // Clear local authentication whether logout succeeds or fails.
        finalize(() => this.clearSession())
      );
  }

  // Start a new authenticated session after successful Login.
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

  // Persist session to browser storage for persistence across reloads.
  private persistSession(): void {
    if (typeof window === 'undefined' || !window.localStorage) {
      return;
    }
    try {
      const accessToken = this.accessTokenState();
      const user = this.currentUserState();
      if (accessToken && user) {
        localStorage.setItem(
          STORAGE_KEY,
          JSON.stringify({
            accessToken,
            refreshToken: this.refreshTokenValue,
            user,
          })
        );
      }
    } catch {
      // Storage unavailable or disabled
    }
  }

  // Restore session from browser storage if present.
  private restoreSessionFromStorage(): void {
    if (typeof window === 'undefined' || !window.localStorage) {
      return;
    }
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) {
        const parsed = JSON.parse(raw);
        if (parsed?.accessToken && parsed?.user) {
          this.accessTokenState.set(parsed.accessToken);
          this.refreshTokenValue = parsed.refreshToken ?? null;
          this.currentUserState.set(parsed.user);
        }
      }
    } catch {
      this.clearStorage();
    }
  }

  // Remove storage entry.
  private clearStorage(): void {
    if (typeof window === 'undefined' || !window.localStorage) {
      return;
    }
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch {
      // Storage unavailable
    }
  }

  // Remove all authentication information.
  clearSession(): void {
    this.accessTokenState.set(null);
    this.refreshTokenValue = null;
    this.currentUserState.set(null);
    this.clearStorage();
  }
}
