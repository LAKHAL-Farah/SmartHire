import { resolveCurrentInterviewUserId } from '../../../../core/services/current-user-id';

export function resolveCurrentUserId(): number {
  return resolveCurrentInterviewUserId() ?? 0;
}

export function isCurrentInterviewUser(userId: number | null | undefined): boolean {
  const currentUserId = resolveCurrentInterviewUserId();
  if (!currentUserId) {
    return false;
  }
  return Number(userId) === currentUserId;
}
