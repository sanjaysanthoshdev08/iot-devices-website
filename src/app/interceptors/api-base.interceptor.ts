import { HttpInterceptorFn } from '@angular/common/http';

/** Address the services use while developing on this computer. */
export const DEV_API_ORIGIN = 'http://localhost:8080';

/**
 * On a real domain (not localhost) the backend is reached through the same address as the site
 * (Nginx forwards /api to Spring Boot), so the development address is swapped for the site's own.
 * On localhost nothing changes. Returns the URL to use.
 */
export function resolveApiUrl(url: string, location: { hostname: string; origin: string }): string {
  const isLocal = location.hostname === 'localhost' || location.hostname === '127.0.0.1';
  if (isLocal || !url.startsWith(DEV_API_ORIGIN)) {
    return url;
  }
  return location.origin + url.substring(DEV_API_ORIGIN.length);
}

export const apiBaseInterceptor: HttpInterceptorFn = (req, next) => {
  if (typeof window === 'undefined') {
    return next(req);
  }
  const url = resolveApiUrl(req.url, window.location);
  return url === req.url ? next(req) : next(req.clone({ url }));
};
