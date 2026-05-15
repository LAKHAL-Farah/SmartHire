import { inject } from '@angular/core';
import { CanMatchFn, Router } from '@angular/router';
import { AuthService } from '../../features/front-office/auth/auth.service';
import { environment } from '../../../environments/environment';

/**
 * Restricts admin routes to users with role `admin`, except in local dev when
 * `environment.openAdminPanelInDev` is true (so the backoffice can be tested without a special login).
 */
export const adminCanMatch: CanMatchFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!environment.production && environment.openAdminPanelInDev) {
    return true;
  }
  if (auth.getUser()?.role === 'admin') {
    return true;
  }
  void router.navigate(['/dashboard']);
  return false;
};
