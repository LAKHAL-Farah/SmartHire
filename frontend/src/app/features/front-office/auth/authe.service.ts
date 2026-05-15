import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { tap } from 'rxjs';
import { setProfileUserUuid } from '../profile/profile-user-id';
import { environment } from '../../../../environments/environment';
import { ThemeService } from '../../../shared/services/theme.service';

@Injectable({
  providedIn: 'root'
})
export class AutheService {

  // Auth endpoints are exposed at the gateway root (/auth), not under /api/v1.
  private readonly API_URL = `${environment.userApiUrl.replace(/\/api\/v1\/?$/, '')}/auth`;

  constructor(
    private http: HttpClient,
    private router: Router,
    private themeService: ThemeService
  ) {}

  private isUuid(value: string): boolean {
    return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
  }

  login(mail: string, password: string) {
    return this.http.post<any>(`${this.API_URL}/connexion`, {
      mail,
      password
    }).pipe(
      tap(res => {
        const token = String(res?.Token ?? res?.token ?? '');
        const userId = String(res?.UserId ?? res?.userId ?? '').trim();
        const userName = String(res?.userName ?? '').trim();
        const email = String(res?.email ?? '').trim();
        const roleRaw = String(res?.roles ?? '').trim().toLowerCase();
        const role = roleRaw.includes('admin')
          ? 'admin'
          : (roleRaw.includes('recruiter') ? 'recruiter' : 'user');

        if (token) {
          localStorage.setItem('auth_token', token);
          localStorage.setItem('access_token', token);
        }
        if (userId) {
          localStorage.setItem('UserId', userId);
          localStorage.setItem('userId', userId);
          if (this.isUuid(userId)) {
            setProfileUserUuid(userId);
          }
        }
        if (userName) {
          localStorage.setItem('userName', userName);
        }
        if (email) {
          localStorage.setItem('email', email);
        }
        if (role) {
          localStorage.setItem('role', role);
        }

        if (userId) {
          localStorage.setItem('user', JSON.stringify({
            id: userId,
            email: email || mail,
            name: userName || (mail?.split('@')[0] ?? 'user'),
            role,
          }));
          // Reload theme after userId is set so it picks up user's stored theme preference
          this.themeService.loadInitialForUser(userId);
        }
      })
    );
  }

  getToken(): string | null {
    return localStorage.getItem('auth_token');
  }

  logout() {
    // Reset theme to app default (dark) before clearing user data
    this.themeService.resetToAppDefault();
    
    localStorage.removeItem('auth_token');
    localStorage.removeItem('access_token');
    localStorage.removeItem('UserId');
    localStorage.removeItem('userId');
    localStorage.removeItem('smarthire_profile_user_uuid');
    localStorage.removeItem('smarthire_interview_user_id');
    localStorage.removeItem('user');
    localStorage.removeItem('userName');
    localStorage.removeItem('email');
    localStorage.removeItem('role');
    void this.router.navigate(['/']);
  }

  isLoggedIn(): boolean {
    return !!this.getToken();
  }

  redirectAfterLogin(): void {
    const role = (localStorage.getItem('role') || '').toLowerCase();
    if (role.includes('recruiter') || role.includes('admin')) {
      void this.router.navigate(['/admin']);
    } else {
      void this.router.navigate(['/dashboard']);
    }
  }

  getRole(): string | null {
    return localStorage.getItem('role');
  }
}