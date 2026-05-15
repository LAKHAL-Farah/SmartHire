import { Component, signal, ViewChild, ElementRef, AfterViewInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LUCIDE_ICONS } from '../../../../shared/lucide-icons';
import { ThemeMode, ThemePalette, ThemeService } from '../../../../shared/services/theme.service';
import { ProfileApiService } from '../../profile/profile-api.service';
import { FaceRecognitionService } from '../../auth/setup-face-recognition/face-recognition.service';

type SettingsCategory = 'appearance' | 'account' | 'security' | 'notifications' | 'subscription' | 'connected' | 'privacy';

interface NotificationSetting {
  name: string;
  description: string;
  enabled: boolean;
}

interface ConnectedAccount {
  provider: string;
  icon: string;
  color: string;
  connected: boolean;
  username?: string;
}

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, LUCIDE_ICONS],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.scss'
})
export class SettingsComponent implements AfterViewInit {
  @ViewChild('videoElement') videoElement?: ElementRef<HTMLVideoElement>;
  private mediaStream: MediaStream | null = null;
  activeCategory = signal<SettingsCategory>('account');
  themePalette = signal<ThemePalette>('original');
  themeMode = signal<ThemeMode>('dark');
  readonly themePalettes: ThemePalette[];

  categories: { id: SettingsCategory; label: string }[] = [
    { id: 'appearance', label: 'Appearance & Theme' },
    { id: 'account', label: 'Account' },
    { id: 'security', label: 'Security' },
    { id: 'notifications', label: 'Notifications' },
    { id: 'subscription', label: 'Subscription' },
    { id: 'connected', label: 'Connected Accounts' },
    { id: 'privacy', label: 'Privacy' },
  ];

  /* ── Account (populated from ProfileApi) ── */
  firstName = '';
  lastName = '';
  email = '';
  headline = '';
  location = '';
  bio = '';

  /* ── Security (MFA / face recognition) ── */
  mfaEnabled = false;
  private currentUserId = '';
  showFaceEnrollmentModal = signal(false);
  enrollmentStep = signal<'camera' | 'preview' | 'processing'>('camera');
  capturedImage = signal<string | null>(null);
  enrollmentError = signal<string | null>(null);

  /* ── Notifications ── */
  notifications: NotificationSetting[] = [
    { name: 'Job Alerts', description: 'Get notified when new jobs match your profile', enabled: true },
    { name: 'Interview Reminders', description: 'Reminders before scheduled mock or real interviews', enabled: true },
    { name: 'Weekly Report', description: 'A summary of your progress and activity each week', enabled: false },
    { name: 'AI Recommendations', description: 'Personalized tips from SmartHire AI', enabled: true },
    { name: 'Application Updates', description: 'Status changes on your job applications', enabled: true },
  ];

  /* ── Subscription ── */
  isPremium = false;
  freeFeatures = [
    '5 job applications per month',
    'Basic skill assessments',
    'Limited CV optimization',
    'Community access',
  ];
  premiumFeatures = [
    'Unlimited job applications',
    'Advanced AI skill assessments',
    'Full CV optimizer with AI rewriting',
    'Priority job matching',
    'Mock interview simulator',
    'GitHub & LinkedIn deep analysis',
  ];

  /* ── Connected Accounts ── */
  connectedAccounts: ConnectedAccount[] = [
    {
      provider: 'Google',
      icon: '<svg width="16" height="16" viewBox="0 0 24 24" fill="#fff"><path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 01-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z"/><path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/><path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/><path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/></svg>',
      color: '#4285f4',
      connected: false,
      username: '',
    },
    {
      provider: 'GitHub',
      icon: '<svg width="16" height="16" viewBox="0 0 24 24" fill="#fff"><path d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.435 9.795 8.205 11.385.6.105.825-.255.825-.57 0-.285-.015-1.23-.015-2.235-3.015.555-3.795-.735-4.035-1.41-.135-.345-.72-1.41-1.23-1.695-.42-.225-1.02-.78-.015-.795.945-.015 1.62.87 1.845 1.23 1.08 1.815 2.805 1.305 3.495.99.105-.78.42-1.305.765-1.605-2.67-.3-5.46-1.335-5.46-5.925 0-1.305.465-2.385 1.23-3.225-.12-.3-.54-1.53.12-3.18 0 0 1.005-.315 3.3 1.23.96-.27 1.98-.405 3-.405s2.04.135 3 .405c2.295-1.56 3.3-1.23 3.3-1.23.66 1.65.24 2.88.12 3.18.765.84 1.23 1.905 1.23 3.225 0 4.605-2.805 5.625-5.475 5.925.435.375.81 1.095.81 2.22 0 1.605-.015 2.895-.015 3.3 0 .315.225.69.825.57A12.02 12.02 0 0024 12c0-6.63-5.37-12-12-12z"/></svg>',
      color: '#333',
      connected: false,
      username: '',
    },
    {
      provider: 'LinkedIn',
      icon: '<svg width="16" height="16" viewBox="0 0 24 24" fill="#fff"><path d="M20.447 20.452h-3.554v-5.569c0-1.328-.027-3.037-1.852-3.037-1.853 0-2.136 1.445-2.136 2.939v5.667H9.351V9h3.414v1.561h.046c.477-.9 1.637-1.85 3.37-1.85 3.601 0 4.267 2.37 4.267 5.455v6.286zM5.337 7.433a2.062 2.062 0 01-2.063-2.065 2.064 2.064 0 112.063 2.065zm1.782 13.019H3.555V9h3.564v11.452zM22.225 0H1.771C.792 0 0 .774 0 1.729v20.542C0 23.227.792 24 1.771 24h20.451C23.2 24 24 23.227 24 22.271V1.729C24 .774 23.2 0 22.222 0h.003z"/></svg>',
      color: '#0077b5',
      connected: false,
    },
    {
      provider: 'Microsoft',
      icon: '<svg width="16" height="16" viewBox="0 0 24 24" fill="#fff"><rect x="1" y="1" width="10" height="10"/><rect x="13" y="1" width="10" height="10"/><rect x="1" y="13" width="10" height="10"/><rect x="13" y="13" width="10" height="10"/></svg>',
      color: '#00a4ef',
      connected: false,
    },
  ];

  /* ── Privacy ── */
  profilePublic = true;
  showScores = true;
  showActivity = false;

  constructor(
    private readonly themeService: ThemeService,
    private readonly profileApi: ProfileApiService,
    private readonly faceService: FaceRecognitionService
  ) {
    this.themePalette.set(this.themeService.palette);
    this.themeMode.set(this.themeService.mode);
    this.themePalettes = this.themeService.palettes;
    this.loadAccount();
  }

  get accountInitials(): string {
    const first = this.firstName.trim().charAt(0);
    const last = this.lastName.trim().charAt(0);
    const initials = `${first}${last}`.toUpperCase();
    if (initials) {
      return initials;
    }
    const mailInitial = this.email.trim().charAt(0).toUpperCase();
    return mailInitial || 'SH';
  }

  private loadAccount(): void {
    this.currentUserId = localStorage.getItem('userId') || localStorage.getItem('UserId') || '';

    this.profileApi.getProfile().subscribe({
      next: (p) => {
        if (!p) return;
        this.firstName = p.firstName ?? '';
        this.lastName = p.lastName ?? '';
        this.email = p.email ?? this.email;
        this.headline = p.headline ?? '';
        this.location = p.location ?? '';
        this.bio = '';
        this.syncConnectedAccounts(p.email ?? '', p.githubUrl ?? '', p.linkedinUrl ?? '');
      },
      error: () => {
        // keep defaults if API fails
      }
    });

    // Try to determine MFA state from local storage or backend when available
    if (this.currentUserId) {
      const stored = localStorage.getItem(`face_recognition_enabled:${this.currentUserId}`);
      this.mfaEnabled = stored === 'true';
    }
  }

  private syncConnectedAccounts(email: string, githubUrl: string, linkedinUrl: string): void {
    const githubUser = this.extractGithubUsername(githubUrl);
    const linkedinUser = this.extractLinkedinUsername(linkedinUrl);

    this.connectedAccounts = this.connectedAccounts.map((account) => {
      if (account.provider === 'Google') {
        return { ...account, connected: !!email, username: email || undefined };
      }
      if (account.provider === 'GitHub') {
        return { ...account, connected: !!githubUser, username: githubUser || undefined };
      }
      if (account.provider === 'LinkedIn') {
        return { ...account, connected: !!linkedinUser, username: linkedinUser || undefined };
      }
      return account;
    });
  }

  private extractGithubUsername(url: string): string {
    const match = url.match(/github\.com\/([^/?#]+)/i);
    return match?.[1] ?? '';
  }

  private extractLinkedinUsername(url: string): string {
    const match = url.match(/linkedin\.com\/(?:in|company)\/([^/?#]+)/i);
    return match?.[1] ?? '';
  }

  selectThemePalette(palette: ThemePalette): void {
    this.themeService.setTheme(palette);
    this.themePalette.set(palette);
  }

  selectThemeMode(mode: ThemeMode): void {
    this.themeService.setMode(mode);
    this.themeMode.set(mode);
  }

  formatPaletteName(palette: ThemePalette): string {
    return palette.split('-').map((part) => part.charAt(0).toUpperCase() + part.slice(1)).join(' ');
  }

  persistMfaSetting(enabled: boolean): void {
    const uid = this.currentUserId || localStorage.getItem('userId') || localStorage.getItem('UserId') || '';
    this.mfaEnabled = enabled;
    if (!uid) {
      return;
    }
    localStorage.setItem(`face_recognition_enabled:${uid}`, enabled ? 'true' : 'false');
  }

  enableFaceRecognition(imageBase64: string): void {
    const uid = this.currentUserId || localStorage.getItem('userId') || localStorage.getItem('UserId') || '';
    if (!uid) return;
    this.faceService.enableFaceRecognition(imageBase64).subscribe({
      next: () => {
        this.mfaEnabled = true;
        localStorage.setItem(`face_recognition_enabled:${uid}`, 'true');
      },
      error: () => {
        // handle error (show toast in real UI)
      }
    });
  }

  disableFaceRecognition(): void {
    const uid = this.currentUserId || localStorage.getItem('userId') || localStorage.getItem('UserId') || '';
    if (!uid) return;
    this.faceService.disableFaceRecognition(uid).subscribe({
      next: () => {
        this.mfaEnabled = false;
        localStorage.setItem(`face_recognition_enabled:${uid}`, 'false');
      },
      error: () => {
        // handle error
      }
    });
  }

  /* ── Face Recognition Enrollment Flow ── */
  openFaceEnrollmentModal(): void {
    this.enrollmentStep.set('camera');
    this.capturedImage.set(null);
    this.enrollmentError.set(null);
    this.showFaceEnrollmentModal.set(true);
    setTimeout(() => this.startCamera(), 100);
  }

  closeFaceEnrollmentModal(): void {
    this.stopCamera();
    this.showFaceEnrollmentModal.set(false);
    this.capturedImage.set(null);
    this.enrollmentError.set(null);
  }

  ngAfterViewInit(): void {
    // Called when component is fully initialized
  }

  private async startCamera(): Promise<void> {
    try {
      if (!this.videoElement?.nativeElement) return;
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'user', width: { ideal: 1280 }, height: { ideal: 720 } }
      });
      this.mediaStream = stream;
      this.videoElement.nativeElement.srcObject = stream;
    } catch (error) {
      console.error('Camera access denied:', error);
      this.enrollmentError.set('Unable to access camera. Please check permissions.');
    }
  }

  private stopCamera(): void {
    if (this.mediaStream) {
      this.mediaStream.getTracks().forEach(track => track.stop());
      this.mediaStream = null;
    }
  }

  captureFromCamera(): void {
    if (!this.videoElement?.nativeElement) return;
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const video = this.videoElement.nativeElement;
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    ctx.drawImage(video, 0, 0);
    const base64 = canvas.toDataURL('image/jpeg', 0.9);
    this.captureFaceImage(base64);
    this.stopCamera();
  }

  captureFaceImage(base64Image: string): void {
    this.capturedImage.set(base64Image);
    this.enrollmentStep.set('preview');
  }

  retakeFaceImage(): void {
    this.capturedImage.set(null);
    this.enrollmentStep.set('camera');
    this.enrollmentError.set(null);
  }

  submitFaceEnrollment(): void {
    const image = this.capturedImage();
    if (!image) {
      this.enrollmentError.set('No image captured');
      return;
    }

    this.enrollmentStep.set('processing');
    this.enableFaceRecognition(image);
  }

  handleFaceEnrollmentSuccess(): void {
    this.mfaEnabled = true;
    this.closeFaceEnrollmentModal();
  }

  handleFaceEnrollmentError(error: string): void {
    this.enrollmentError.set(error);
    this.enrollmentStep.set('preview');
  }
}
