import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { InterviewApiService } from '../interview-api.service';
import { InterviewMode, InterviewType, LiveSubMode, RoleType } from '../interview.models';
import { resolveCurrentUserId } from '../interview-user.util';

interface RoleCard {
  value: RoleType;
  icon: string;
  title: string;
  subtitle: string;
}

@Component({
  selector: 'app-interview-setup',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './interview-setup.component.html',
  styleUrl: './interview-setup.component.scss',
})
export class InterviewSetupComponent implements OnInit {
  private readonly api = inject(InterviewApiService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly userId = resolveCurrentUserId();

  readonly selectedRole = signal<RoleType | null>(null);
  readonly selectedType = signal<InterviewType | null>(null);
  readonly selectedMode = signal<InterviewMode | null>(null);
  readonly selectedLiveSubMode = signal<LiveSubMode>('TEST_LIVE');
  readonly questionCount = signal(8);
  readonly selectedInputMode = signal<'TEXT'>('TEXT');

  readonly isStarting = signal(false);
  readonly isPreparingLive = signal(false);
  readonly preparingMessage = signal('Preparing your live interview...');
  readonly errorMessage = signal<string | null>(null);

  readonly estimatedMinutes = computed(() => this.questionCount() * 3);
  readonly minQuestionCount = computed(() => (this.selectedMode() === 'LIVE' ? 3 : 5));
  readonly maxQuestionCount = computed(() => (this.selectedMode() === 'LIVE' ? 15 : 20));
  readonly canStart = computed(
    () =>
      !!this.userId &&
      !!this.selectedRole() &&
      !!this.selectedType() &&
      !!this.selectedMode() &&
      !this.isStarting() &&
      !this.isPreparingLive()
  );

  readonly roles: RoleCard[] = [
    {
      value: 'SE',
      icon: '⌨️',
      title: 'Software Engineer',
      subtitle: 'Algorithms, system design, coding problems',
    },
    {
      value: 'CLOUD',
      icon: '☁️',
      title: 'Cloud Engineer',
      subtitle: 'Cloud architecture, scalability, fault tolerance',
    },
    {
      value: 'AI',
      icon: '🧠',
      title: 'AI Engineer',
      subtitle: 'ML pipelines, model selection, deployment',
    },
  ];

  readonly types: Array<{ label: string; value: InterviewType }> = [
    { label: 'Behavioral', value: 'BEHAVIORAL' },
    { label: 'Technical', value: 'TECHNICAL' },
    { label: 'Mixed', value: 'MIXED' },
  ];

  ngOnInit(): void {
    this.selectedRole.set('SE');
    this.selectedType.set('TECHNICAL');
    this.selectedMode.set('PRACTICE');

    this.route.queryParamMap.subscribe((params) => {
      const mode = this.normalizeMode(params.get('mode'));
      const role = this.normalizeRole(params.get('role'));
      const type = this.normalizeType(params.get('type'));

      if (mode) {
        this.selectedMode.set(mode);
      }

      if (role) {
        this.selectedRole.set(role);
      }

      if (type) {
        this.selectedType.set(type);
      }
    });
  }

  setRole(role: RoleType): void {
    this.selectedRole.set(role);
  }

  setType(type: InterviewType): void {
    this.selectedType.set(type);
  }

  setMode(mode: InterviewMode): void {
    this.selectedMode.set(mode);
    const min = this.minQuestionCount();
    const max = this.maxQuestionCount();
    const clamped = Math.min(max, Math.max(min, this.questionCount()));
    if (clamped !== this.questionCount()) {
      this.questionCount.set(clamped);
    }
  }

  setLiveSubMode(mode: LiveSubMode): void {
    this.selectedLiveSubMode.set(mode);
  }

  setQuestionCount(event: Event): void {
    const target = event.target as HTMLInputElement;
    const min = this.minQuestionCount();
    const max = this.maxQuestionCount();
    const value = Number(target.value);
    this.questionCount.set(Math.min(max, Math.max(min, value)));
  }

  startInterview(): void {
    if (!this.canStart()) {
      if (!this.userId) {
        this.errorMessage.set('No active user found. Please sign in before starting an interview.');
      }
      return;
    }

    const role = this.selectedRole();
    const type = this.selectedType();
    const mode = this.selectedMode();
    if (!role || !type || !mode) {
      return;
    }

    if (mode === 'LIVE') {
      void this.navigateToLiveStart(role);
      return;
    }

    this.isStarting.set(true);
    this.errorMessage.set(null);

    this.api
      .startSession({
        userId: this.userId!,
        careerPathId: 1,
        role,
        roleType: role,
        mode,
        type,
        questionCount: this.questionCount(),
      })
      .subscribe({
        next: (session) => {
          if (!session?.id) {
            this.isStarting.set(false);
            this.errorMessage.set('Session was created but no id was returned.');
            return;
          }

          this.isStarting.set(false);
          this.navigateAfterStart(session.id, role, type, mode);
        },
        error: (error: HttpErrorResponse) => {
          this.isStarting.set(false);
          const backendMessage = this.extractBackendErrorMessage(error);
          this.errorMessage.set(
            backendMessage
              ? `Unable to start interview session: ${backendMessage}`
              : 'Unable to start interview session. Please try again.'
          );
        },
      });
  }

  private async navigateToLiveStart(role: RoleType): Promise<void> {
    this.isStarting.set(false);
    this.isPreparingLive.set(true);
    this.errorMessage.set(null);
    this.preparingMessage.set('Opening live pre-call...');

    const params = new URLSearchParams({
      subMode: this.selectedLiveSubMode(),
      questionCount: String(Math.min(15, Math.max(3, this.questionCount()))),
      company: 'Tech Company',
      targetRole: this.roleLabel(role),
    });

    const target = `/dashboard/interview/live/start?${params.toString()}`;
    const routed = await this.safeNavigate(target);
    if (!routed) {
      this.isPreparingLive.set(false);
      this.errorMessage.set('Unable to open live pre-call screen. Please try again.');
    }
  }

  private async navigateAfterStart(
    sessionId: number,
    role: RoleType,
    type: InterviewType,
    mode: InterviewMode,
  ): Promise<void> {
    if (mode === 'PRACTICE') {
      const navigated = await this.safeNavigate(`/dashboard/interview/session/${sessionId}`);
      if (!navigated) {
        this.errorMessage.set('Session started, but navigation failed. Open it from Interview Hub.');
      }
      return;
    }

    const targets: string[] = [];

    if (role === 'SE' && type === 'TECHNICAL') {
      targets.push(`/dashboard/interview/session/${sessionId}/code`);
    }

    if (role === 'CLOUD') {
      targets.push(`/dashboard/interview/session/${sessionId}/cloud`);
    }

    if (role === 'AI') {
      targets.push(`/dashboard/interview/session/${sessionId}/ml`);
    }

    targets.push(`/dashboard/interview/session/${sessionId}`);

    for (const target of targets) {
      const navigated = await this.safeNavigate(target);
      if (navigated) {
        return;
      }
    }

    this.errorMessage.set('Session started, but navigation failed. Open it from Interview Hub.');
  }

  private async safeNavigate(target: string): Promise<boolean> {
    try {
      const navigated = await this.router.navigateByUrl(target, { replaceUrl: true });
      if (navigated) {
        return true;
      }
    } catch (error) {
      console.error('[InterviewSetup] router navigation failed', { target, error });
    }

    try {
      globalThis.location.assign(target);
      return true;
    } catch {
      return false;
    }
  }

  private normalizeMode(raw: string | null): InterviewMode | null {
    const value = (raw ?? '').trim().toUpperCase();
    if (!value) {
      return null;
    }

    if (value === 'LIVE' || value.startsWith('LIVE')) {
      return 'LIVE';
    }

    if (value === 'TEST' || value.startsWith('TEST')) {
      return 'TEST';
    }

    if (value === 'PRACTICE' || value === 'PRACTICAL' || value.startsWith('PRACTIC')) {
      return 'PRACTICE';
    }

    return null;
  }

  private roleLabel(role: RoleType): string {
    switch (role) {
      case 'SE':
        return 'Software Engineer';
      case 'CLOUD':
        return 'Cloud Engineer';
      case 'AI':
        return 'AI Engineer';
      default:
        return 'Software Engineer';
    }
  }

  private normalizeRole(raw: string | null): RoleType | null {
    const value = (raw ?? '').trim().toUpperCase();
    if (value === 'SE' || value === 'CLOUD' || value === 'AI' || value === 'ALL') {
      return value;
    }

    return null;
  }

  private normalizeType(raw: string | null): InterviewType | null {
    const value = (raw ?? '').trim().toUpperCase();
    if (value === 'TECHNICAL' || value === 'BEHAVIORAL' || value === 'MIXED') {
      return value;
    }

    return null;
  }

  private extractBackendErrorMessage(error: HttpErrorResponse): string | null {
    const payload = error.error;

    if (!payload) {
      return null;
    }

    if (typeof payload === 'string') {
      return payload.trim() || null;
    }

    if (typeof payload === 'object') {
      const message = (payload as { message?: unknown; detail?: unknown }).message;
      if (typeof message === 'string' && message.trim()) {
        return message.trim();
      }

      const detail = (payload as { detail?: unknown }).detail;
      if (typeof detail === 'string' && detail.trim()) {
        return detail.trim();
      }
    }

    return null;
  }

}
