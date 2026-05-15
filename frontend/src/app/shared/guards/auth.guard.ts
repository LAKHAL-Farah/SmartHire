import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { resolveCurrentUserId } from '../../features/front-office/dashboard/interview/interview-user.util';

export const authGuard: CanActivateFn = () => {
  const userId = resolveCurrentUserId();
  if (userId > 0) {
    return true;
  }

  return inject(Router).createUrlTree(['/login']);
};
