import { inject } from '@angular/core';
import { CanActivateFn, ActivatedRouteSnapshot, Router } from '@angular/router';
import { AutheService } from '../../features/front-office/auth/authe.service';
import { resolveCurrentProfileUserId } from '../services/current-user-id';

/**
 * Guard to check if user has required role.
 * If user is not authorized, redirects to access-denied page.
 * 
 * Usage in routes:
 * canActivate: [roleGuard]
 * data: { requiredRoles: ['recruiter', 'candidate'] }
 */
export const roleGuard: CanActivateFn = (route: ActivatedRouteSnapshot) => {
  const auth = inject(AutheService);
  const router = inject(Router);

  const userRole = (localStorage.getItem('role') || '').toLowerCase();
  const userId = resolveCurrentProfileUserId();
  const requiredRoles = route.data['requiredRoles'] as string[] | undefined;

  if (!auth.isLoggedIn() || !userId) {
    auth.logout();
    void router.navigate(['/']);
    return false;
  }

  // If no specific roles required, allow access
  if (!requiredRoles || requiredRoles.length === 0) {
    return true;
  }

  // Check if user has one of the required roles
  if (userRole && requiredRoles.some((role) => userRole.includes(role.toLowerCase()))) {
    return true;
  }

  // User doesn't have required role
  void router.navigate(['/access-denied']);
  return false;
};
