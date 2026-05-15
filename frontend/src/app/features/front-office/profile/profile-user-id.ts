/** Persisted MS-User id (set at register / login). */
export const PROFILE_USER_UUID_STORAGE_KEY = 'smarthire_profile_user_uuid';
const DEMO_MODE_KEY = 'smarthire_local_demo_mode';

/** candidate | recruiter — used after register / login for routing (onboarding + assessments). */
export const ACCOUNT_ROLE_KEY = 'smarthire_account_role';

export function getProfileUserUuid(): string {
  const stored = localStorage.getItem(PROFILE_USER_UUID_STORAGE_KEY);
  if (stored && /^[0-9a-f-]{36}$/i.test(stored)) {
    return stored;
  }

  const userId = localStorage.getItem('userId') || localStorage.getItem('UserId');
  if (userId && /^[0-9a-f-]{36}$/i.test(userId)) {
    localStorage.setItem(PROFILE_USER_UUID_STORAGE_KEY, userId);
    return userId;
  }

  const userRaw = localStorage.getItem('user');
  if (userRaw) {
    try {
      const parsed = JSON.parse(userRaw) as Record<string, unknown>;
      const parsedId = typeof parsed['id'] === 'string' ? parsed['id'] : '';
      if (/^[0-9a-f-]{36}$/i.test(parsedId)) {
        localStorage.setItem(PROFILE_USER_UUID_STORAGE_KEY, parsedId);
        return parsedId;
      }
    } catch {
      // Ignore invalid user JSON in storage.
    }
  }

  return '';
}

/**
 * MS-User id for MS-Assessment APIs (sessions list, start, review).
 *
 * Assignment rows and onboarding use {@link PROFILE_USER_UUID_STORAGE_KEY}. Listing sessions under a
 * different id than the one used when starting the attempt yields an empty history and the hub stays on Start.
 *
 * When a profile UUID is stored, use it; otherwise fall back to the logged-in `user` id, then env dev.
 */
export function getAssessmentUserId(): string {
  return getProfileUserUuid();
}

export function setProfileUserUuid(uuid: string): void {
  if (/^[0-9a-f-]{36}$/i.test(uuid)) {
    localStorage.setItem(PROFILE_USER_UUID_STORAGE_KEY, uuid);
  }
}

/** Browser-only demo when MS-User is unreachable (no server-side user). */
export function isLocalDemoMode(): boolean {
  return localStorage.getItem(DEMO_MODE_KEY) === '1';
}

export function setLocalDemoMode(on: boolean): void {
  if (on) {
    localStorage.setItem(DEMO_MODE_KEY, '1');
  } else {
    localStorage.removeItem(DEMO_MODE_KEY);
  }
}
