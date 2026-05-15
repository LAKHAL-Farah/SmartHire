import { Component, ElementRef, OnDestroy, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, ActivatedRoute } from '@angular/router';
import { AuthMfaService } from '../auth-mfa.service';
import { AuthLeftPanelComponent } from '../auth-left-panel/auth-left-panel.component';
import { AutheService } from '../authe.service';
import { setLocalDemoMode, setProfileUserUuid } from '../../profile/profile-user-id';

@Component({
  selector: 'app-verify-face',
  standalone: true,
  imports: [CommonModule, AuthLeftPanelComponent],
  templateUrl: './verify-face.component.html',
  styleUrls: ['./verify-face.component.scss']
})
export class VerifyFaceComponent implements OnDestroy {
  @ViewChild('video') videoRef!: ElementRef<HTMLVideoElement>;
  faceImagePreview = '';
  faceImageBase64 = '';
  token = '';
  loading = false;
  verificationResult: any = null;
  attemptsRemaining = 3;
  stream: MediaStream | null = null;

  private isAdminRole(role: string): boolean {
    const normalized = role.trim().toLowerCase();
    return normalized.includes('recruiter') || normalized.includes('admin');
  }

  constructor(private auth: AuthMfaService, private route: ActivatedRoute, private router: Router, private authService: AutheService) {
    this.route.queryParams.subscribe((q) => {
      this.token = q['token'] || sessionStorage.getItem('faceVerificationToken') || '';
      console.log('Received face verification token:', this.token);
    });
    // start camera automatically
    void this.startCamera();
  }

  private persistLoginSession(data: any): void {
    const token = String(data?.Token ?? data?.token ?? '');
    const userId = String(data?.UserId ?? data?.userId ?? '').trim();
    const userName = String(data?.userName ?? data?.username ?? '').trim();
    const email = String(data?.email ?? '').trim();
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

  async startCamera(): Promise<void> {
    try {
      this.stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'user' } });
      if (this.videoRef?.nativeElement) {
        this.videoRef.nativeElement.srcObject = this.stream;
        await this.videoRef.nativeElement.play();
      }
    } catch (err) {
      // ignore: user may upload image instead
    }
  }

  captureFace(): void {
    const video = this.videoRef?.nativeElement;
    if (!video) return;
    const data = this.auth.captureWebcamImage(video);
    this.faceImagePreview = data;
    this.faceImageBase64 = data;
  }

  onFileSelected(e: Event): void {
    const input = e.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.auth.convertImageToBase64(file).then((b) => {
      this.faceImagePreview = b;
      this.faceImageBase64 = b;
    });
  }

  verifyFace(): void {
    if (!this.faceImageBase64 || !this.token) return;
    this.loading = true;
    this.auth.verifyFace(this.token, this.faceImageBase64).subscribe({
      next: (res) => {
        this.loading = false;
        console.log('Face verification result:', res);
        this.verificationResult = res;
        if (res?.status === 'SUCCESS') {
          this.persistLoginSession(res?.data);
          this.auth.redirectAfterLogin();
          return;
        }
      },
      error: (err) => {
        this.loading = false;
        this.verificationResult = err?.error || { matches: false };
        this.attemptsRemaining = err?.error?.details?.attemptRemaining ?? (this.attemptsRemaining - 1);
        if (this.attemptsRemaining <= 0) {
          void this.router.navigate(['/login']);
        }
      }
    });
  }

  ngOnDestroy(): void {
    if (this.stream) {
      this.stream.getTracks().forEach(t => t.stop());
    }
  }
}
