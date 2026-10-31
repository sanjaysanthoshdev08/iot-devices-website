import { inject } from '@angular/core';
import {
  HttpErrorResponse,
  HttpInterceptorFn,
} from '@angular/common/http';
import {
  EMPTY,
  catchError,
  mergeMap,
  of,
  switchMap,
  throwError,
} from 'rxjs';
import { AuthService } from '../services/auth.service';

const PUBLIC_AUTH_PATHS = new Set([
  '/api/v1/auth/register',
  '/api/v1/auth/login',
  '/api/v1/auth/refresh',
  '/api/v1/auth/logout',
]);

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  let url: URL;
  try {
    url = new URL(req.url);
  } catch {
    return next(req);
  }

  if (
    url.origin !== 'http://localhost:8080' ||
    !url.pathname.startsWith('/api/v1/') ||
    PUBLIC_AUTH_PATHS.has(url.pathname)
  ) {
    return next(req);
  }

  const auth = inject(AuthService);
  const version = auth.getSessionVersion();
  const originalToken = auth.getAccessToken();

  const withToken = (token: string) => req.clone({
    setHeaders: { Authorization: 'Bearer ' + token },
  });

  // Calling next directly retries once without re-entering this interceptor.
  const retryOnce = (token: string) => next(withToken(token)).pipe(
    catchError((error: unknown) => {
      if (!auth.isCurrentSession(version)) return EMPTY;

      if (
        error instanceof HttpErrorResponse &&
        error.status === 401 &&
        auth.getAccessToken() === token
      ) {
        auth.clearSession();
      }
      return throwError(() => error);
    })
  );

  return next(originalToken ? withToken(originalToken) : req).pipe(
    catchError((error: unknown) => {
      if (!auth.isCurrentSession(version)) return EMPTY;

      if (
        !(error instanceof HttpErrorResponse) ||
        error.status !== 401 ||
        !originalToken
      ) {
        return throwError(() => error);
      }

      const currentToken = auth.getAccessToken();

      // Another request may already have completed token rotation.
      if (currentToken && currentToken !== originalToken) {
        return retryOnce(currentToken);
      }

      return auth.refreshSession().pipe(
        switchMap(response => {
          if (!auth.isCurrentSession(version)) return EMPTY;
          return retryOnce(response.data.accessToken);
        })
      );
    }),
    mergeMap(event =>
      auth.isCurrentSession(version) ? of(event) : EMPTY
    )
  );
};
