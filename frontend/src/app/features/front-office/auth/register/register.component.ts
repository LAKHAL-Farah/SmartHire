import { Component, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { environment } from '../../../../../environments/environment';
import { ProfileApiService, ProfileApiResponse } from '../../profile/profile-api.service';
import { ACCOUNT_ROLE_KEY, setProfileUserUuid, setLocalDemoMode } from '../../profile/profile-user-id';
import { AuthLeftPanelComponent } from '../auth-left-panel/auth-left-panel.component';
import { AutheService } from '../authe.service';
import { LUCIDE_ICONS } from '../../../../shared/lucide-icons';
import { UserService } from '../user.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AuthLeftPanelComponent, LUCIDE_ICONS],
  templateUrl: './register.component.html',
  styleUrl: './register.component.scss'
})
export class RegisterComponent {
  private readonly profileApi = inject(ProfileApiService);
  private readonly userService = inject(UserService);
  private readonly authService = inject(AutheService);
  private readonly router = inject(Router);

  firstName = '';
  lastName = '';
  email = '';
  password = '';
  location = '';
  githubUrl = '';
  linkedinUrl = '';
  headline = '';
  acceptTerms = false;
  nameTouched = false;
  lastNameTouched = false;
  emailTouched = false;
  passwordTouched = false;
  locationTouched = false;
  githubTouched = false;
  linkedinTouched = false;
  headlineTouched = false;

  selectedRole = signal<'candidate' | 'recruiter'>('candidate');
  showPassword = signal(false);
  isLoading = signal(false);
  passwordStrength = signal(0);

  strengthLabel = computed(() => {
    const s = this.passwordStrength();
    if (s <= 1) return 'Weak';
    if (s === 2) return 'Fair';
    if (s === 3) return 'Good';
    return 'Strong';
  });

  isEmailValid(): boolean {
    if (!this.email) return false;
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.email);
  }

  isValidUrl(s: string): boolean {
    const t = s?.trim() ?? '';
    if (!t) {
      return true;
    }
    try {
      new URL(t);
      return true;
    } catch {
      return false;
    }
  }

  hasUppercase(): boolean { return /[A-Z]/.test(this.password); }
  hasNumber(): boolean { return /[0-9]/.test(this.password); }
  hasSpecial(): boolean { return /[^A-Za-z0-9]/.test(this.password); }

  onPasswordInput(): void {
    let strength = 0;
    if (this.password.length >= 8) strength++;
    if (this.hasUppercase()) strength++;
    if (this.hasNumber()) strength++;
    if (this.hasSpecial()) strength++;
    this.passwordStrength.set(strength);
  }

  onSubmit(): void {
    this.nameTouched = true;
    this.lastNameTouched = true;
    this.emailTouched = true;
    this.passwordTouched = true;
    this.locationTouched = true;
    this.githubTouched = true;
    this.linkedinTouched = true;
    this.headlineTouched = true;

    const fn = this.firstName.trim();
    const ln = this.lastName.trim();
    if (
      !fn ||
      !ln ||
      !this.isEmailValid() ||
      !this.password ||
      !this.acceptTerms ||
      !this.location.trim() ||
      (this.githubUrl.trim() && !this.isValidUrl(this.githubUrl)) ||
      (this.linkedinUrl.trim() && !this.isValidUrl(this.linkedinUrl)) ||
      !this.headline.trim() ||
      this.headline.length > 200
    ) {
      return;
    }


    console.log('Registering user with:');
    this.isLoading.set(true);
    
   const userRequest= {
          email: this.email,
          password: this.password,
          roleName: this.selectedRole() === 'recruiter' ? 'recruiter' : 'candidate',
        };

        
        const profileRequest= { firstName: this.firstName, lastName: this.lastName, headline: this.headline, location: this.location, githubUrl: this.githubUrl, linkedinUrl: this.linkedinUrl };

    this.userService
      .createUser(
        userRequest,
        profileRequest
      )
      .subscribe({
        next: (u) => {
          console.log('Registered user:', u);
          setLocalDemoMode(false);
          if (u?.id) {
            const uid = String(u.id);
            setProfileUserUuid(uid);
            localStorage.setItem(
              'user',
              JSON.stringify({
                id: uid,
                email: this.email.trim(),
                name: `${this.firstName} ${this.lastName}`,
                role: this.selectedRole() === 'recruiter' ? 'recruiter' : 'user',
              })
            );
          }
          localStorage.setItem(
            ACCOUNT_ROLE_KEY,
            this.selectedRole() === 'recruiter' ? 'recruiter' : 'candidate'
          );
          this.authService.login(this.email.trim(), this.password).subscribe({
            next: () => {
              this.isLoading.set(false);
              void this.router.navigate(['/onboarding']);
            },
            error: () => {
              this.isLoading.set(false);
              if (environment.localAuthFallback) {
                this.startLocalDemoRegistration(this.firstName, this.lastName);
                return;
              }
              alert('Account created, but automatic login failed. Please sign in once or check the auth service.');
            },
          });
        },
        error: () => {
          this.isLoading.set(false);
          if (environment.localAuthFallback) {
            this.startLocalDemoRegistration(this.firstName, this.lastName);
            return;
          }
          alert(
            'Registration failed. Is MS-User on port 8082? If the email already exists, try logging in instead.'
          );
        },
      });
  }

  /**
   * MS-User unavailable — keep going with a random user id and data in localStorage only.
   * Sync to the real service when your teammate’s MS-User is running again.
   */
  private startLocalDemoRegistration(firstName: string, lastName: string): void {
    const id = crypto.randomUUID();
    setProfileUserUuid(id);
    setLocalDemoMode(true);
    localStorage.setItem(
      'smarthire_local_user',
      JSON.stringify({
        email: this.email.trim(),
        firstName,
        lastName,
        role: this.selectedRole(),
      })
    );
    localStorage.setItem(
      ACCOUNT_ROLE_KEY,
      this.selectedRole() === 'recruiter' ? 'recruiter' : 'candidate'
    );
    localStorage.setItem(
      'user',
      JSON.stringify({
        id,
        email: this.email.trim(),
        name: `${firstName} ${lastName}`,
        role: this.selectedRole() === 'recruiter' ? 'recruiter' : 'user',
      })
    );
    const profile: ProfileApiResponse = {
      userId: id,
      firstName,
      lastName,
      email: this.email.trim(),
      headline: '',
    };
    localStorage.setItem('smarthire_local_profile', JSON.stringify(profile));
    void this.router.navigate(['/onboarding']);
  }

  oauthSignup(provider: string): void {
    console.log('OAuth signup with:', provider);
    // TODO: integrate OAuth
  }
}
