import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';

export interface ForgotPasswordPayload {
  email: string;
}

export interface ResetPasswordPayload {
  resetCode: string;
  newPassword: string;
  confirmPassword: string;
}

@Injectable({
  providedIn: 'root'
})
export class PasswordService {
  private readonly base = `${environment.userApiUrl.replace(/\/api\/v1\/?$/, '')}/auth`;

  constructor(private http: HttpClient) {}

  forgotPassword(payload: ForgotPasswordPayload): Observable<any> {
    return this.http.post(`${this.base}/forgot-password`, payload);
  }

  resetPassword(payload: ResetPasswordPayload): Observable<any> {
    return this.http.post(`${this.base}/reset-password`, payload);
  }
}
