/** Shared check for MS-User profile `onboardingJson` snapshot. */
export function hasCompletedPreferenceOnboarding(onboardingJson: string | null | undefined): boolean {
  if (!onboardingJson?.trim()) return false;
  try {
    const o = JSON.parse(onboardingJson) as { completedAt?: string };
    return !!o.completedAt;
  } catch {
    return false;
  }
}
