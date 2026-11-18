import { describe, it, expect } from 'vitest';
import { resolveApiUrl } from './api-base.interceptor';

describe('resolveApiUrl', () => {
  const dev = 'http://localhost:8080/api/v1/devices';

  it('keeps the development address on localhost', () => {
    expect(resolveApiUrl(dev, { hostname: 'localhost', origin: 'http://localhost' })).toBe(dev);
    expect(resolveApiUrl(dev, { hostname: '127.0.0.1', origin: 'http://127.0.0.1:4200' })).toBe(dev);
  });

  it('uses the site address on a real domain', () => {
    expect(resolveApiUrl(dev, { hostname: 'app.hykonindia.com', origin: 'https://app.hykonindia.com' }))
      .toBe('https://app.hykonindia.com/api/v1/devices');
  });

  it('never rewrites other addresses', () => {
    const other = 'https://example.com/file.json';
    expect(resolveApiUrl(other, { hostname: 'app.hykonindia.com', origin: 'https://app.hykonindia.com' })).toBe(other);
  });
});
