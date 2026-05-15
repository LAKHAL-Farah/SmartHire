import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AutheService } from '../../features/front-office/auth/authe.service';
import { resolveCurrentProfileUserId } from '../services/current-user-id';

/**
 * Guard to prevent authenticated users from accessing login page.
 * If user is already logged in, redirects to appropriate page based on role.
 */
export const noAuthGuard: CanActivateFn = () => {
  const auth = inject(AutheService);
  const router = inject(Router);
  const userId = resolveCurrentProfileUserId();

  if (auth.isLoggedIn() && !!userId) {
    // User is already authenticated
    const role = (localStorage.getItem('role') || '').toLowerCase();
    if (role.includes('recruiter') || role.includes('admin')) {
      void router.navigate(['/admin']);
    } else {
      void router.navigate(['/dashboard']);
    }
    return false;
  }

  if (auth.isLoggedIn() && !userId) {
    auth.logout();
  }

  // User is not authenticated, allow access to login page
  return true;
};
