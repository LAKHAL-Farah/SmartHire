import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthLeftPanelComponent } from '../auth-left-panel/auth-left-panel.component';
import { AuthService } from '../auth.service';
import { LUCIDE_ICONS } from '../../../../shared/lucide-icons';
import { AutheService } from '../authe.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AuthLeftPanelComponent, LUCIDE_ICONS],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss'
})
export class LoginComponent {
  email = '';
  password = '';
  rememberMe = false;
  emailTouched = false;
  passwordTouched = false;
  errorMsg = '';

  showPassword = signal(false);
  isLoading = signal(false);

  constructor(private authService: AuthService, private autheService: AutheService, private router: Router) {}

  isEmailValid(): boolean {
    if (!this.email) return false;
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.email);
  }

  onSubmit(): void {
    this.errorMsg = '';
    this.emailTouched = true;
    this.passwordTouched = true;

    console.log('Submit clicked - email:', this.email, 'password:', this.password);

    if (!this.isEmailValid() || !this.password) {
      this.errorMsg = 'Please fill in all fields correctly';
      console.log('Validation failed');
      return;
    }

    console.log('Validation passed, starting login...');
    this.isLoading.set(true);
    
    this.autheService.login(this.email, this.password).subscribe({
      next: () => {
        console.log('Login successful');
        this.isLoading.set(false);
        this.autheService.redirectAfterLogin();
      },
      error: (err) => {
        console.error('Login error:', err);
        this.isLoading.set(false);
        this.errorMsg = 'Login failed: ' + err.message;
      }
    });
  }

  oauthLogin(provider: string): void {
    console.log('OAuth login with:', provider);
    // TODO: integrate OAuth
  }
}
