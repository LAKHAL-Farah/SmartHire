const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const PROFILE_USER_UUID_STORAGE_KEY = 'smarthire_profile_user_uuid';
const INTERVIEW_USER_ID_STORAGE_KEY = 'smarthire_interview_user_id';
const MAX_SAFE_INT_BIGINT = BigInt(Number.MAX_SAFE_INTEGER);

function parseUuid(raw: unknown): string | null {
  if (typeof raw !== 'string') {
    return null;
  }
  const trimmed = raw.trim();
  return UUID_REGEX.test(trimmed) ? trimmed : null;
}

function parseStoredJson(key: string): unknown {
  const raw = localStorage.getItem(key);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as unknown;
  } catch {
    return null;
  }
}

export function resolveCurrentProfileUserId(): string | null {
  const directKeys = [
    'UserId',
    'userId',
    'user_id',
    'uid',
    PROFILE_USER_UUID_STORAGE_KEY,
  ];

  for (const key of directKeys) {
    const parsed = parseUuid(localStorage.getItem(key));
    if (parsed) {
      if (key !== PROFILE_USER_UUID_STORAGE_KEY) {
        localStorage.setItem(PROFILE_USER_UUID_STORAGE_KEY, parsed);
      }
      return parsed;
    }
  }

  const user = parseStoredJson('user');
  if (user && typeof user === 'object') {
    const userRecord = user as Record<string, unknown>;
    const possibleUserIds = [
      userRecord['id'],
      userRecord['userId'],
      userRecord['UserId'],
      userRecord['uid'],
    ];

    for (const candidate of possibleUserIds) {
      const parsed = parseUuid(candidate);
      if (parsed) {
        localStorage.setItem(PROFILE_USER_UUID_STORAGE_KEY, parsed);
        return parsed;
      }
    }
  }

  return null;
}

function parseNumericUserId(raw: unknown): number | null {
  if (typeof raw !== 'string') {
    return null;
  }

  const trimmed = raw.trim();
  if (!/^\d+$/.test(trimmed)) {
    return null;
  }

  const parsed = Number(trimmed);
  if (!Number.isSafeInteger(parsed) || parsed <= 0) {
    return null;
  }

  return parsed;
}

function stablePositiveIdFromString(value: string): number {
  // FNV-1a 64-bit hash reduced to JS safe integer range for deterministic, cross-session IDs.
  const normalized = value.trim().toLowerCase();
  let hash = 0xcbf29ce484222325n;

  for (let i = 0; i < normalized.length; i += 1) {
    hash ^= BigInt(normalized.charCodeAt(i));
    hash = (hash * 0x100000001b3n) & 0xffffffffffffffffn;
  }

  const reduced = hash % MAX_SAFE_INT_BIGINT;
  const safe = Number(reduced === 0n ? 1n : reduced);
  return safe > 0 ? safe : 1;
}

function resolveRawLoggedInUserId(): string | null {
  const directKeys = [
    'userId',
    'UserId',
    'user_id',
    'uid',
    PROFILE_USER_UUID_STORAGE_KEY,
  ];

  for (const key of directKeys) {
    const value = localStorage.getItem(key);
    if (typeof value === 'string' && value.trim().length > 0) {
      return value.trim();
    }
  }

  const user = parseStoredJson('user');
  if (user && typeof user === 'object') {
    const userRecord = user as Record<string, unknown>;
    const possibleUserIds = [
      userRecord['id'],
      userRecord['userId'],
      userRecord['UserId'],
      userRecord['uid'],
    ];

    for (const candidate of possibleUserIds) {
      if (typeof candidate === 'string' && candidate.trim().length > 0) {
        return candidate.trim();
      }
    }
  }

  return null;
}

export function resolveCurrentInterviewUserId(): number | null {
  const cached = parseNumericUserId(localStorage.getItem(INTERVIEW_USER_ID_STORAGE_KEY));
  if (cached) {
    return cached;
  }

  const rawUserId = resolveRawLoggedInUserId();
  if (!rawUserId) {
    return null;
  }

  const numeric = parseNumericUserId(rawUserId) ?? stablePositiveIdFromString(rawUserId);
  localStorage.setItem(INTERVIEW_USER_ID_STORAGE_KEY, String(numeric));
  return numeric;
}
