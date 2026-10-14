import { inject } from '@angular/core';
import { HttpInterceptorFn } from '@angular/common/http';

import { AuthService } from '../services/auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);

  // Get the current access token from AuthService.
  const accessToken = authService.getAccessToken();

  // If no token exists, send the original request unchanged.
  if (!accessToken) {
    return next(req);
  }

  // Add the JWT access token to the Authorization header.
  const authenticatedRequest = req.clone({
    setHeaders: {
      Authorization: 'Bearer ' + accessToken,
    },
  });

  // Send the authenticated request.
  return next(authenticatedRequest);
};