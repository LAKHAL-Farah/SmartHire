import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError } from 'rxjs/operators';
import { throwError } from 'rxjs';
import { AutheService } from '../authe.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {

  const authService = inject(AutheService);
  const token = authService.getToken();

  let clonedReq = req;

  if (token) {
    clonedReq = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
  }

  return next(clonedReq).pipe(
    catchError(err => {
      if (err.status === 401) {
        authService.logout();
        window.location.href = '/login';
      }
      return throwError(() => err);
    })
  );
};