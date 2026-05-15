import { environment } from '../../environments/environment';

/**
 * Base URL for MS-Assessment API calls.
 * When `environment.assessmentApiUrl` is relative (e.g. `/api/v1/assessment`), we prefix
 * `window.location.origin` so the browser targets the Angular dev server and `proxy.conf.json`
 * forwards `/api` → MS-Assessment — avoiding cross-origin calls to :8084 (CORS / status 0).
 */
export function assessmentApiBaseUrl(): string {
  const raw = (environment.assessmentApiUrl || '').trim().replace(/\/$/, '');
  if (!raw) {
    return '';
  }
  if (/^https?:\/\//i.test(raw)) {
    return raw;
  }
  if (typeof window !== 'undefined' && window.location?.origin) {
    const path = raw.startsWith('/') ? raw : `/${raw}`;
    return `${window.location.origin}${path}`;
  }
  return raw;
}
