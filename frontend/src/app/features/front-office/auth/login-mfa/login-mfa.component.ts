import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthMfaService } from '../auth-mfa.service';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthLeftPanelComponent } from '../auth-left-panel/auth-left-panel.component';
import { AutheService } from '../authe.service';
import { setLocalDemoMode, setProfileUserUuid } from '../../profile/profile-user-id';

@Component({
  selector: 'app-login-mfa',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AuthLeftPanelComponent],
  templateUrl: './login-mfa.component.html',
  styleUrls: ['./login-mfa.component.scss']
})
export class LoginMfaComponent {
  username = '';
  password = '';
  error = '';
  loading = false;

  private isAdminRole(role: string): boolean {
    const normalized = role.trim().toLowerCase();
    return normalized.includes('recruiter') || normalized.includes('admin');
  }

  constructor(private auth: AuthMfaService, private router: Router,private authService: AutheService) {}

  private persistLoginSession(data: any): void {
    const token = String(data?.Token ?? data?.token ?? '');
    const userId = String(data?.UserId ?? data?.userId ?? '').trim();
    const userName = String(data?.userName ?? data?.username ?? '').trim();
    const email = String(data?.email ?? this.username ?? '').trim();
    const roleRaw = String(data?.roles ?? 'candidate').trim();
    const role = this.isAdminRole(roleRaw) ? 'recruiter' : 'candidate';

    if (token) {
      localStorage.setItem('auth_token', token);
      localStorage.setItem('access_token', token);
    }

    if (userId) {
      localStorage.setItem('userId', userId);
      localStorage.setItem('UserId', userId);
      setProfileUserUuid(userId);
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

    localStorage.setItem(
      'user',
      JSON.stringify({
        id: userId,
        email,
        name: userName || email.split('@')[0] || 'user',
        role: this.isAdminRole(roleRaw) ? 'recruiter' : 'user',
      })
    );

    setLocalDemoMode(false);
  }

  onLogin(): void {
    this.error = '';
    this.loading = true;
    this.auth.loginMfa(this.username, this.password).subscribe({
      next: (res) => {
        this.loading = false;
        console.log('Login response:', res);
        if (res?.status === 'FACE_REQUIRED') {
          const token = res.data.tempToken;
          sessionStorage.setItem('faceVerificationToken', token);
          void this.router.navigate(['/verify-face'], { queryParams: { token } });
          return;
        }
        if (res?.status === 'SUCCESS') {
          this.persistLoginSession(res?.data);
          this.auth.redirectAfterLogin();
          return;
        }
        this.error = res?.message || 'Erreur inattendue';
      },
      error: (err: HttpErrorResponse) => {
        console.error('Login error:', err);
        this.loading = false;
        this.error = err?.error?.message || 'Nom d\'utilisateur ou mot de passe incorrect';
      }
    });
  }
}
