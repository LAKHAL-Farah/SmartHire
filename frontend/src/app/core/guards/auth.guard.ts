import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AutheService } from '../../features/front-office/auth/authe.service';
import { resolveCurrentProfileUserId } from '../services/current-user-id';

/**
 * Guard to check if user is authenticated.
 * If not authenticated, redirects to login page.
 * Profile & Settings routes are allowed even without a resolved UUID (component will handle).
 */
export const authGuard: CanActivateFn = (route) => {
  const auth = inject(AutheService);
  const router = inject(Router);
  const userId = resolveCurrentProfileUserId();

  // Always require login
  if (!auth.isLoggedIn()) {
    auth.logout();
    void router.navigate(['/']);
    return false;
  }

  // Allow profile & settings even without UUID — component will attempt to load/resolve it
  const path = route.routeConfig?.path ?? '';
  const isProfileOrSettingsRoute = path === 'profile' || path === 'settings';
  if (isProfileOrSettingsRoute) {
    return true;
  }

  // For other routes, require UUID to be resolved
  if (!!userId) {
    return true;
  }

  auth.logout();
  void router.navigate(['/']);
  return false;
};
