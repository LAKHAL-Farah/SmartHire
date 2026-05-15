import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { ActivatedRoute, Router } from '@angular/router';
import { LiveSessionService } from '../services/live-session.service';
import { LiveSessionStartRequest, LiveSessionStartResponse, LiveSubMode } from '../models/live-session.model';
import { resolveCurrentUserId } from '../../features/front-office/dashboard/interview/interview-user.util';

@Component({
  selector: 'app-live-start',
  standalone: true,
  imports: [CommonModule, FormsModule, MatButtonModule, MatIconModule, MatInputModule],
  templateUrl: './live-start.component.html',
  styleUrl: './live-start.component.scss',
})
export class LiveStartComponent implements OnInit, OnDestroy {
  // Form state
  questionCount = 5;
  liveSubMode: LiveSubMode = 'PRACTICE_LIVE';
  companyName = '';
  targetRole = '';

  // UI state
  isLoading = false;
  errorMessage = '';
  cameraStream: MediaStream | null = null;
  cameraPermission: 'pending' | 'granted' | 'denied' = 'pending';

  private readonly currentUser = resolveCurrentUserId();

  constructor(
    private readonly liveService: LiveSessionService,
    private readonly route: ActivatedRoute,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    this.applySetupPrefill();
    this.requestCameraPreview();
  }

  ngOnDestroy(): void {
    this.cameraStream?.getTracks().forEach((track) => track.stop());
  }

  onJoin(): void {
    if (this.isLoading) {
      return;
    }

    this.errorMessage = '';
    this.isLoading = true;

    const request: LiveSessionStartRequest = {
      userId: this.currentUser,
      careerPathId: 1,
      liveSubMode: this.liveSubMode,
      questionCount: this.questionCount,
      companyName: this.companyName.trim() || undefined,
      targetRole: this.targetRole.trim() || undefined,
    };

    this.liveService.startLiveSession(request).subscribe({
      next: (response) => {
        this.isLoading = false;

        const resolvedGreetingAudioUrl = this.resolveGreetingAudioUrl(response.greetingAudioUrl);
        void this.router.navigate(['/dashboard/interview/live', response.sessionId], {
          queryParams: {
            subMode: response.liveSubMode || this.liveSubMode,
            company: this.companyName || 'Tech Company',
            targetRole: this.targetRole || 'Candidate',
            total: response.totalQuestions,
            firstQ: response.firstQuestionText,
            preparedGreetingUrl: response.greetingAudioUrl,
            preparedResolvedGreetingUrl: resolvedGreetingAudioUrl,
          },
        });
      },
      error: (error: HttpErrorResponse) => {
        this.isLoading = false;
        this.errorMessage =
          (error.error as { message?: string } | undefined)?.message ??
          'Failed to start session. Please try again.';
      },
    });
  }

  get joinDisabled(): boolean {
    return this.isLoading;
  }

  get joinLabel(): string {
    if (this.isLoading) {
      return 'Preparing room...';
    }

    return 'Join Interview';
  }

  private requestCameraPreview(): void {
    navigator.mediaDevices
      .getUserMedia({ video: true, audio: false })
      .then((stream) => {
        this.cameraStream = stream;
        this.cameraPermission = 'granted';
      })
      .catch((error: unknown) => {
        console.warn('[LiveStart] Camera permission denied or unavailable:', error);
        this.cameraPermission = 'denied';
      });
  }

  private applySetupPrefill(): void {
    const params = this.route.snapshot.queryParamMap;

    const subMode = (params.get('subMode') ?? '').trim().toUpperCase();
    if (subMode === 'PRACTICE_LIVE' || subMode === 'TEST_LIVE') {
      this.liveSubMode = subMode;
    }

    const rawCount = Number(params.get('questionCount'));
    if (Number.isFinite(rawCount)) {
      this.questionCount = Math.min(15, Math.max(3, rawCount));
    }

    const company = (params.get('company') ?? '').trim();
    if (company) {
      this.companyName = company;
    }

    const targetRole = (params.get('targetRole') ?? '').trim();
    if (targetRole) {
      this.targetRole = targetRole;
    }
  }

  private resolveGreetingAudioUrl(url: string): string {
    if (!url) {
      return '';
    }

    if (/^https?:\/\//i.test(url)) {
      return url;
    }

    if (url.startsWith('/interview-service/') && globalThis.location?.origin) {
      return new URL(url, globalThis.location.origin).toString();
    }

    const configured = (globalThis.localStorage?.getItem('smarthire.interviewApiBaseUrl') ?? '').trim();
    if (configured && /^https?:\/\//i.test(configured)) {
      try {
        return new URL(url, configured).toString();
      } catch {
        // Fall through to origin fallback.
      }
    }

    if (globalThis.location?.origin) {
      return new URL(url, globalThis.location.origin).toString();
    }

    return url;
  }
}
