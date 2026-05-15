import type { CandidateAssignmentStatusDto } from './candidate-assignment-api.service';
import { PROFILE_USER_UUID_STORAGE_KEY } from '../profile/profile-user-id';

/**
 * MS-Assessment stores sessions under the assignment row’s `userId`. That must match
 * `GET /sessions/user/{userId}`. Prefer the id returned by {@link CandidateAssignmentStatusDto#userId}
 * when a plan exists; otherwise fall back to {@link getAssessmentUserId}.
 */
export function canonicalSessionListUserId(
  plan: CandidateAssignmentStatusDto | null,
  baseUid: string
): string {
  const t = plan?.userId?.trim();
  if (t && /^[0-9a-f-]{36}$/i.test(t)) {
    return t;
  }
  return baseUid;
}

/**
 * Every MS-User UUID that might have been used to persist sessions (profile key, auth `user`, assignment row).
 * Listing only one id often returned `[]` while attempts lived under another id — merge `GET /sessions/user` for each.
 */
export function collectCandidateUserIdsForSessions(
  plan: CandidateAssignmentStatusDto | null,
  baseUid: string
): string[] {
  const seen = new Set<string>();
  const ids: string[] = [];
  const add = (u: string | undefined | null) => {
    const t = u?.trim();
    if (!t || !/^[0-9a-f-]{36}$/i.test(t)) return;
    const k = t.toLowerCase();
    if (seen.has(k)) return;
    seen.add(k);
    ids.push(t);
  };
  add(baseUid);
  add(plan?.userId);
  try {
    const raw = typeof localStorage !== 'undefined' ? localStorage.getItem('user') : null;
    if (raw) {
      const u = JSON.parse(raw) as { id?: string };
      add(u?.id);
    }
  } catch {
    /* ignore */
  }
  add(typeof localStorage !== 'undefined' ? localStorage.getItem(PROFILE_USER_UUID_STORAGE_KEY) : null);
  return ids;
}
