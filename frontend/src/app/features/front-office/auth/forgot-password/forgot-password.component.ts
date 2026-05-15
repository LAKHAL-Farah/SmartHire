import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthLeftPanelComponent } from '../auth-left-panel/auth-left-panel.component';
import { PasswordService } from '../password.service';
import { LUCIDE_ICONS } from '../../../../shared/lucide-icons';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AuthLeftPanelComponent, LUCIDE_ICONS],
  templateUrl: './forgot-password.component.html',
  styleUrls: ['./forgot-password.component.scss']
})
export class ForgotPasswordComponent {
  email = '';
  emailTouched = false;
  errorMsg = '';
  successMsg = '';

  isLoading = signal(false);

  constructor(private passwordService: PasswordService) {}

  isEmailValid(): boolean {
    if (!this.email) return false;
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.email);
  }

  onSubmit(): void {
    this.errorMsg = '';
    this.successMsg = '';
    this.emailTouched = true;

    if (!this.isEmailValid()) {
      this.errorMsg = 'Veuillez entrer une adresse e-mail valide.';
      return;
    }

    this.isLoading.set(true);
    this.passwordService.forgotPassword({ email: this.email }).subscribe({
      next: (res) => {
        this.isLoading.set(false);
        this.successMsg = 'Si cet e-mail existe, un code de réinitialisation a été envoyé.';
      },
      error: (err) => {
        this.isLoading.set(false);
        this.errorMsg = err?.error?.message || 'Erreur lors de l\'envoi. Réessayez.';
      }
    });
  }
}
