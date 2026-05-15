import { inject } from '@angular/core';
import { CanMatchFn, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { hasCompletedPreferenceOnboarding } from '../onboarding-state';
import { ProfileApiService } from '../../features/front-office/profile/profile-api.service';
import { ACCOUNT_ROLE_KEY, isLocalDemoMode } from '../../features/front-office/profile/profile-user-id';

/**
 * `/onboarding` is only for first-time preference capture.
 * If the profile already has onboarding JSON with `completedAt`, redirect to dashboard.
 */
export const onboardingCanMatch: CanMatchFn = async () => {
  const router = inject(Router);
  const profileApi = inject(ProfileApiService);

  const postOnboardingTarget = (): string[] => {
    const role = (localStorage.getItem(ACCOUNT_ROLE_KEY) || 'candidate').toLowerCase();
    if (role === 'recruiter') {
      return ['/dashboard'];
    }
    return ['/dashboard/assessments'];
  };

  if (isLocalDemoMode()) {
    try {
      const p = await firstValueFrom(profileApi.getProfile());
      if (hasCompletedPreferenceOnboarding(p.onboardingJson ?? null)) {
        await router.navigate(postOnboardingTarget());
        return false;
      }
    } catch {
      /* allow onboarding */
    }
    return true;
  }

  try {
    const p = await firstValueFrom(profileApi.getProfile());
    if (hasCompletedPreferenceOnboarding(p.onboardingJson ?? null)) {
      await router.navigate(postOnboardingTarget());
      return false;
    }
  } catch {
    /* MS-User down — allow onboarding */
  }
  return true;
};
