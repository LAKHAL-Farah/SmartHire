import { resolveCurrentInterviewUserId } from '../../../../core/services/current-user-id';

export function resolveRoadmapUserId(): number | null {
  return resolveCurrentInterviewUserId();
}
