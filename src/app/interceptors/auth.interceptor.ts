
import { inject } from '@angular/core';
import {
  HttpErrorResponse,
  HttpInterceptorFn,
} from '@angular/common/http';
import {
  Observable,
  catchError,
  finalize,
  shareReplay,
  switchMap,
  throwError,
} from 'rxjs';

import { AuthService, ApiResponse, RefreshTokenResponse } from '../services/auth.service';

const AUTH_REFRESH_URL = '/api/v1/auth/refresh';

// Share a single refresh request across simultaneous 401 responses.
let refreshInProgress$: Observable<
  ApiResponse<RefreshTokenResponse>
> | null = null;

function refreshOnce(
  authService: AuthService
): Observable<ApiResponse<RefreshTokenResponse>> {
  if (!refreshInProgress$) {
    refreshInProgress$ = authService.refreshSession().pipe(
      finalize(() => {
        refreshInProgress$ = null;
      }),
      shareReplay({
        bufferSize: 1,
        refCount: false,
      })
    );
  }

  return refreshInProgress$;
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);

  // Never intercept or retry the refresh request itself.
  if (req.url.includes(AUTH_REFRESH_URL)) {
    return next(req);
  }

  const accessToken = authService.getAccessToken();

  // Attach the current access token when one exists.
  const requestToSend = accessToken
    ? req.clone({
        setHeaders: {
          Authorization: 'Bearer ' + accessToken,
        },
      })
    : req;

  return next(requestToSend).pipe(
    catchError((error: unknown) => {
      // Refresh only for 401 responses to authenticated requests.
      if (
        !(error instanceof HttpErrorResponse) ||
        error.status !== 401 ||
        !accessToken
      ) {
        return throwError(() => error);
      }

      // Concurrent 401 responses reuse the same refresh operation.
      return refreshOnce(authService).pipe(
        switchMap((response) => {
          const retryRequest = req.clone({
            setHeaders: {
              Authorization: 'Bearer ' + response.data.accessToken,
            },
          });

          // Retry the original request once with the new access token.
          return next(retryRequest);
        })
      );
    })
  );
};
