import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  animate,
  state,
  style,
  transition,
  trigger,
} from '@angular/animations';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { catchError, finalize, forkJoin, of } from 'rxjs';
import {
  JobOfferDto,
  LinkedInProfileDto,
  LinkedInSectionScores,
  ProfileTipDto,
} from '../../../../core/models/profile-optimizer.models';
import { resolveCurrentProfileUserId } from '../../../../core/services/current-user-id';
import { ProfileOptimizerService } from '../../../../core/services/profile-optimizer.service';
import { LUCIDE_ICONS } from '../../../../shared/lucide-icons';
import { ToastService } from '../../../../shared/components/toast/toast.service';
import { ToastComponent } from '../../../../shared/components/toast/toast.component';

@Component({
  selector: 'app-linkedin',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, LUCIDE_ICONS, ToastComponent],
  templateUrl: './linkedin.component.html',
  styleUrls: ['./linkedin.component.scss'],
  animations: [
    trigger('phaseSwap', [
      state('input', style({ opacity: 1, transform: 'translateY(0)' })),
      state('results', style({ opacity: 1, transform: 'translateY(0)' })),
      transition('input <=> results', [
        style({ opacity: 0, transform: 'translateY(8px)' }),
        animate('260ms ease-out'),
      ]),
    ]),
    trigger('accordion', [
      state('closed', style({ height: '0px', opacity: 0, overflow: 'hidden' })),
      state('open', style({ height: '*', opacity: 1, overflow: 'hidden' })),
      transition('closed <=> open', animate('220ms ease')),
    ]),
  ],
})
export class LinkedinComponent implements OnInit {
  private readonly optimizerApi = inject(ProfileOptimizerService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(FormBuilder);
  private readonly toastService = inject(ToastService);
  private readonly router = inject(Router);

  readonly profile = signal<LinkedInProfileDto | null>(null);
  readonly jobOffers = signal<JobOfferDto[]>([]);
  readonly selectedJobId = signal<string | null>(null);
  readonly phase = signal<'input' | 'results'>('input');
  readonly loading = signal({ analyze: false, align: false, page: true });
  readonly error = signal<string | null>(null);
  readonly statusMessage = signal('');
  readonly copiedField = signal<string | null>(null);
  readonly expandedSection = signal<string | null>('headline');
  readonly resetAlignmentMode = signal(false);

  private statusIntervalId: number | null = null;

  readonly form = this.fb.nonNullable.group({
    currentHeadline: [''],
    currentSummary: [''],
    rawContent: ['', [Validators.required]],
    currentSkills: [''],
  });

  readonly sectionScores = computed<LinkedInSectionScores | null>(() => {
    const raw = this.profile()?.sectionScoresJson;
    if (!raw) {
      return null;
    }

    try {
      return JSON.parse(raw) as LinkedInSectionScores;
    } catch {
      return null;
    }
  });

  readonly optimizedSkills = computed<string[]>(() => {
    const raw = this.profile()?.optimizedSkills;
    if (!raw) {
      return [];
    }

    try {
      const parsed = JSON.parse(raw) as unknown;
      return Array.isArray(parsed) ? parsed.map((entry) => String(entry)) : [];
    } catch {
      return [];
    }
  });

  readonly alignedSkills = computed<string[]>(() => {
    const raw = this.profile()?.jobAlignedSkills;
    if (!raw) {
      return [];
    }

    try {
      const parsed = JSON.parse(raw) as unknown;
      return Array.isArray(parsed) ? parsed.map((entry) => String(entry)) : [];
    } catch {
      return [];
    }
  });

  readonly currentSkillsList = computed<string[]>(() => this.parseSkills(this.profile()?.currentSkills ?? this.form.controls.currentSkills.value));

  readonly linkedInTips = signal<ProfileTipDto[]>([]);

  readonly selectedOffer = computed(() => {
    const selectedId = this.selectedJobId();
    if (!selectedId) {
      return null;
    }
    return this.jobOffers().find((offer) => offer.id === selectedId) ?? null;
  });

  readonly incorporatedKeywords = computed<string[]>(() => {
    const offer = this.selectedOffer();
    if (!offer?.extractedKeywords || !this.alignedSkills().length) {
      return [];
    }

    try {
      const keywords = JSON.parse(offer.extractedKeywords) as string[];
      const aligned = this.alignedSkills().map((item) => item.toLowerCase());
      return keywords.filter((keyword) => aligned.some((skill) => skill.includes(keyword.toLowerCase()))).slice(0, 8);
    } catch {
      return [];
    }
  });

  ngOnInit(): void {
    this.loadPage();
  }

  scoreTone(score: number | null | undefined): 'high' | 'mid' | 'low' {
    const value = Number(score ?? 0);
    if (value >= 75) {
      return 'high';
    }
    if (value >= 45) {
      return 'mid';
    }
    return 'low';
  }

  scorePercent(score: number | null | undefined): number {
    const value = Number(score ?? 0);
    return Math.max(0, Math.min(100, value));
  }

  loadPage(): void {
    this.loading.update((state) => ({ ...state, page: true }));
    this.error.set(null);

    forkJoin({
      profile: this.optimizerApi.getLinkedInProfile().pipe(
        catchError((error: unknown) => {
          if (error instanceof HttpErrorResponse && error.status === 404) {
            return of(null);
          }
          this.error.set('Unable to load LinkedIn profile right now.');
          return of(null);
        })
      ),
      offers: this.optimizerApi.listJobOffers().pipe(catchError(() => of([]))),
      tips: this.optimizerApi.getTips('LINKEDIN').pipe(catchError(() => of([]))),
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(({ profile, offers, tips }) => {
        this.profile.set(profile);
        const currentUserId = resolveCurrentProfileUserId();
        this.jobOffers.set((offers as JobOfferDto[]).filter((offer) => offer.userId === currentUserId).slice(0, 10));
        this.linkedInTips.set((tips as ProfileTipDto[]).slice(0, 3));

        if (profile?.analysisStatus === 'COMPLETED') {
          this.phase.set('results');
          this.prefillForm(profile);
        } else {
          this.phase.set('input');
          if (profile) {
            this.prefillForm(profile);
          }
        }

        this.loading.update((state) => ({ ...state, page: false }));
      });
  }

  analyze(): void {
    if (this.form.invalid || this.loading().analyze) {
      this.form.markAllAsTouched();
      return;
    }

    this.error.set(null);
    this.loading.update((state) => ({ ...state, analyze: true }));
    this.startStatusCycle([
      'Reading your profile...',
      'Scoring each section...',
      'Generating optimized content...',
      'Almost done...',
    ]);

    this.optimizerApi
      .analyzeLinkedIn({
        rawContent: this.form.controls.rawContent.value,
        currentHeadline: this.form.controls.currentHeadline.value || undefined,
        currentSummary: this.form.controls.currentSummary.value || undefined,
        currentSkills: this.form.controls.currentSkills.value || undefined,
      })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.stopStatusCycle();
          this.loading.update((state) => ({ ...state, analyze: false }));
        })
      )
      .subscribe({
        next: (profile) => {
          this.profile.set(profile);
          this.phase.set('results');
          this.prefillForm(profile);
          this.toastService.show('success', 'LinkedIn profile analyzed');
        },
        error: (error: Error) => {
          this.error.set(error.message || 'Failed to analyze LinkedIn profile.');
          this.toastService.show('error', 'LinkedIn analysis failed');
        },
      });
  }

  reanalyze(): void {
    this.phase.set('input');
    this.resetAlignmentMode.set(false);
  }

  alignToJobOffer(): void {
    const selected = this.selectedJobId();
    if (!selected || this.loading().align) {
      return;
    }

    this.error.set(null);
    this.loading.update((state) => ({ ...state, align: true }));
    this.startStatusCycle([
      'Reading your profile...',
      'Matching role keywords...',
      'Rewriting aligned content...',
      'Almost done...',
    ]);

    this.optimizerApi
      .alignLinkedInToJob(selected)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.stopStatusCycle();
          this.loading.update((state) => ({ ...state, align: false }));
        })
      )
      .subscribe({
        next: (profile) => {
          this.profile.set(profile);
          this.resetAlignmentMode.set(false);
          this.toastService.show('success', `LinkedIn profile aligned to ${this.selectedOffer()?.title ?? 'selected role'}`);
        },
        error: (error: Error) => {
          this.error.set(error.message || 'Failed to align LinkedIn profile.');
          this.toastService.show('error', 'LinkedIn alignment failed');
        },
      });
  }

  resetAlignment(): void {
    this.resetAlignmentMode.set(true);
  }

  toggleFeedback(section: 'headline' | 'summary' | 'skills' | 'recommendations'): void {
    this.expandedSection.set(this.expandedSection() === section ? null : section);
  }

  copyToClipboard(text: string | null | undefined, field: string): void {
    if (!text) {
      return;
    }

    navigator.clipboard.writeText(text).then(() => {
      this.copiedField.set(field);
      window.setTimeout(() => this.copiedField.set(null), 1500);
    });
  }

  goToJobOffers(): void {
    void this.router.navigate(['/dashboard/job-offers']);
  }

  viewAllTips(): void {
    void this.router.navigate(['/dashboard/optimizer']);
  }

  trackByOfferId(_: number, offer: JobOfferDto): string {
    return offer.id;
  }

  private prefillForm(profile: LinkedInProfileDto): void {
    this.form.patchValue({
      currentHeadline: profile.currentHeadline ?? '',
      currentSummary: profile.currentSummary ?? '',
      rawContent: profile.rawContent ?? '',
      currentSkills: profile.currentSkills ?? '',
    });
  }

  private parseSkills(raw: string): string[] {
    if (!raw) {
      return [];
    }

    return raw
      .split(/\n|,/)
      .map((entry) => entry.trim())
      .filter((entry) => !!entry);
  }

  private startStatusCycle(messages: string[]): void {
    this.stopStatusCycle();
    let index = 0;
    this.statusMessage.set(messages[index]);

    this.statusIntervalId = window.setInterval(() => {
      index = (index + 1) % messages.length;
      this.statusMessage.set(messages[index]);
    }, 3000);
  }

  private stopStatusCycle(): void {
    if (this.statusIntervalId != null) {
      window.clearInterval(this.statusIntervalId);
      this.statusIntervalId = null;
    }
    this.statusMessage.set('');
  }
}
