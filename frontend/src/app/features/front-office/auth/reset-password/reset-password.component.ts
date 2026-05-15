import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthLeftPanelComponent } from '../auth-left-panel/auth-left-panel.component';
import { PasswordService } from '../password.service';
import { LUCIDE_ICONS } from '../../../../shared/lucide-icons';

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AuthLeftPanelComponent, LUCIDE_ICONS],
  templateUrl: './reset-password.component.html',
  styleUrls: ['./reset-password.component.scss']
})
export class ResetPasswordComponent {
  code = '';
  newPassword = '';
  confirmPassword = '';

  codeTouched = false;
  newTouched = false;
  confirmTouched = false;

  errorMsg = '';
  successMsg = '';

  isLoading = signal(false);

  constructor(private passwordService: PasswordService, private router: Router) {}

  isCodeValid(): boolean {
    return /^\d{6}$/.test(this.code);
  }

  isPasswordValid(){
    return (this.newPassword && this.newPassword.length >= 6);
  }

  onSubmit(): void {
    this.errorMsg = '';
    this.successMsg = '';
    this.codeTouched = true;
    this.newTouched = true;
    this.confirmTouched = true;

    if (!this.isCodeValid()) {
      this.errorMsg = 'Le code doit être composé de 6 chiffres.';
      return;
    }
    if (!this.isPasswordValid()) {
      this.errorMsg = 'Le mot de passe doit contenir au moins 6 caractères.';
      return;
    }
    if (this.newPassword !== this.confirmPassword) {
      this.errorMsg = 'Les mots de passe ne correspondent pas.';
      return;
    }

    this.isLoading.set(true);
    this.passwordService.resetPassword({ resetCode: this.code, newPassword: this.newPassword, confirmPassword: this.confirmPassword }).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.successMsg = 'Mot de passe réinitialisé avec succès. Vous pouvez maintenant vous connecter.';
        setTimeout(() => void this.router.navigate(['/login']), 1400);
      },
      error: (err) => {
        this.isLoading.set(false);
        this.errorMsg = err?.error?.message || 'Erreur lors de la réinitialisation. Réessayez.';
      }
    });
  }
}
