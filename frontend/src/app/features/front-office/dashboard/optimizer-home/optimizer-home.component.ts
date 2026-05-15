import { CommonModule } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { animate, query, stagger, style, transition, trigger } from '@angular/animations';
import { Router, RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { JobOfferDto, ParsedCvContent, ProfileTipDto } from '../../../../core/models/profile-optimizer.models';
import { resolveCurrentProfileUserId } from '../../../../core/services/current-user-id';
import { ActiveCvService } from '../../../../core/services/active-cv.service';
import { ProfileOptimizerService } from '../../../../core/services/profile-optimizer.service';
import { LUCIDE_ICONS } from '../../../../shared/lucide-icons';
import { JobOfferPanelComponent } from '../../../../shared/components/job-offer-panel/job-offer-panel.component';
import { ToastComponent } from '../../../../shared/components/toast/toast.component';

@Component({
  selector: 'app-optimizer-home',
  standalone: true,
  imports: [CommonModule, RouterLink, LUCIDE_ICONS, JobOfferPanelComponent, ToastComponent],
  templateUrl: './optimizer-home.component.html',
  styleUrl: './optimizer-home.component.scss',
  animations: [
    trigger('pipelineCardsStagger', [
      transition(':enter', [
        query(
          '.po-pipeline-card',
          [
            style({ opacity: 0, transform: 'translateY(20px)' }),
            stagger(60, [animate('300ms ease-out', style({ opacity: 1, transform: 'translateY(0)' }))]),
          ],
          { optional: true }
        ),
      ]),
    ]),
  ],
})
export class OptimizerHomeComponent implements OnInit {
  // NOTE: Existing codebase pattern uses centralized LUCIDE_ICONS instead of per-component icon imports.
  private readonly optimizerApi = inject(ProfileOptimizerService);
  private readonly activeCvService = inject(ActiveCvService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly router = inject(Router);

  readonly loading = signal(true);
  readonly error = signal('');
  readonly jobOfferPanelOpen = signal(false);
  readonly cvs = signal<any[]>([]);
  readonly jobOffers = signal<JobOfferDto[]>([]);
  readonly tips = signal<ProfileTipDto[]>([]);

  readonly activeCv = this.activeCvService.activeCv;

  readonly parsedStats = computed(() => {
    const parsed = this.cvs().filter((cv) => cv.parseStatus === 'COMPLETED').length;
    const failed = this.cvs().filter((cv) => cv.parseStatus === 'FAILED').length;
    return { parsed, failed };
  });

  readonly hireReadiness = computed(() => this.activeCv()?.atsScore ?? 0);
  readonly recentOffers = computed(() => this.jobOffers().slice(0, 5));
  readonly activeCvTips = computed(() => {
    const activeCvId = this.activeCv()?.id;
    if (!activeCvId) {
      return [] as ProfileTipDto[];
    }

    return [...this.tips()]
      .filter((tip) => tip.profileType === 'CV' && tip.sourceEntityId === activeCvId)
      .sort((left, right) => {
        const priorityRank = (priority: ProfileTipDto['priority']): number => {
          if (priority === 'HIGH') {
            return 0;
          }
          if (priority === 'MEDIUM') {
            return 1;
          }
          return 2;
        };

        return priorityRank(left.priority) - priorityRank(right.priority);
      });
  });

  readonly activeTips = this.activeCvTips;
  readonly activeOpenTips = computed(() => this.activeTips().filter((tip) => !tip.isResolved));
  readonly activeCvName = computed(() => this.activeCv()?.originalFileName ?? 'active CV');

  readonly activeCvParsed = computed<ParsedCvContent | null>(() => {
    const raw = this.activeCv()?.parsedContent;
    if (!raw) {
      return null;
    }

    try {
      return JSON.parse(raw) as ParsedCvContent;
    } catch {
      return null;
    }
  });

  readonly comingSoonPipelines = [
    {
      id: 'linkedin',
      title: 'LinkedIn Pipeline',
      description:
        'Score your LinkedIn profile, generate AI-optimized headline, summary and skills — then align it to any job offer.',
      features: [
        'Profile scoring across 4 sections',
        'AI-optimized headline & summary',
        'Skills alignment to job offers',
        'Side-by-side before/after view',
      ],
      icon: 'linkedin',
      badge: 'NEW',
      cta: 'Get Started →',
      theme: 'linkedin',
      disabled: false,
    },
    {
      id: 'github',
      title: 'GitHub Pipeline',
      description:
        'Audit all your public repositories, score README quality, CI/CD practices and test coverage — get actionable fix suggestions.',
      features: [
        'Public repo quality audit',
        'README scoring & improvement tips',
        'CI/CD and test coverage detection',
        'Language diversity analysis',
      ],
      icon: 'github',
      badge: 'NEW',
      cta: 'Get Started →',
      theme: 'github',
      disabled: false,
    },
  ] as const;

  private pickLatestActiveCv(cvs: any[]): any | null {
    return [...cvs]
      .filter((cv) => cv.isActive)
      .sort((left, right) => {
        const leftTime = new Date(left.updatedAt ?? left.uploadedAt ?? 0).getTime();
        const rightTime = new Date(right.updatedAt ?? right.uploadedAt ?? 0).getTime();
        return rightTime - leftTime;
      })[0] ?? null;
  }

  readonly parsedContentInvalid = computed(() => {
    const raw = this.activeCv()?.parsedContent;
    if (!raw) {
      return false;
    }

    try {
      JSON.parse(raw);
      return false;
    } catch {
      return true;
    }
  });

  readonly scoreMetrics = computed(() => {
    const cv = this.activeCv();
    const parsed = this.activeCvParsed();
    const baseScore = cv?.atsScore ?? 0;

    if (!cv) {
      return {
        keyword: 0,
        structure: 0,
        formatting: 0,
      };
    }

    const skillsCount = parsed?.skills?.length ?? 0;
    const experienceCount = parsed?.experience?.length ?? 0;
    const educationCount = parsed?.education?.length ?? 0;
    const summaryLength = parsed?.summary?.trim().length ?? 0;

    const keyword = this.clampScore(baseScore * 0.76 + Math.min(skillsCount * 3.2, 24));
    const structure = this.clampScore(
      baseScore * 0.7 +
      Math.min(experienceCount * 6, 18) +
      Math.min(educationCount * 4, 10) +
      (summaryLength > 40 ? 8 : summaryLength > 0 ? 4 : 0)
    );
    const formatting = this.clampScore(
      baseScore * 0.68 +
      (cv.parseStatus === 'COMPLETED' ? 10 : 4) +
      (summaryLength > 120 ? 8 : 3)
    );

    return {
      keyword,
      structure,
      formatting,
    };
  });

  ngOnInit(): void {
    this.activeCvService.loadActiveCv();
    this.loadData();
  }

  greeting(): string {
    const hour = new Date().getHours();
    if (hour < 12) {
      return 'Good morning,';
    }
    if (hour < 18) {
      return 'Good afternoon,';
    }
    return 'Good evening,';
  }

  loadData(): void {
    this.loading.set(true);
    this.error.set('');

    forkJoin({
      cvs: this.optimizerApi.getAllCvs().pipe(catchError(() => of([]))),
      jobOffers: this.optimizerApi.listJobOffers().pipe(catchError(() => of([]))),
      tips: this.optimizerApi.getTips().pipe(catchError(() => of([]))),
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ cvs, jobOffers, tips }) => {
          const currentUserId = resolveCurrentProfileUserId();
          const userCvs = (cvs as any[]).filter((cv) => cv.userId === currentUserId);
          const userOffers = (jobOffers as JobOfferDto[])
            .filter((offer) => offer.userId === currentUserId)
            .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
          const backendActiveCv = this.pickLatestActiveCv(userCvs);
          const currentActiveCv = this.activeCv();

          this.cvs.set(userCvs);
          this.jobOffers.set(userOffers);
          this.tips.set(tips as ProfileTipDto[]);

          if (backendActiveCv) {
            if (currentActiveCv?.id !== backendActiveCv.id) {
              this.activeCvService.setActiveCv(backendActiveCv);
            }
          } else if (!currentActiveCv) {
            const fallback = userCvs[0] ?? null;
            if (fallback) {
              this.activeCvService.setActiveCv(fallback);
            }
          }

          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
          this.error.set('Unable to load optimizer data right now.');
        },
      });
  }

  openPanel(): void {
    this.jobOfferPanelOpen.set(true);
  }

  closePanel(): void {
    this.jobOfferPanelOpen.set(false);
  }

  onOfferSaved(offer: JobOfferDto): void {
    this.jobOffers.update((rows) => [offer, ...rows.filter((item) => item.id !== offer.id)]);
    this.jobOfferPanelOpen.set(false);
  }

  resolveTip(tipId: string): void {
    this.optimizerApi
      .resolveTip(tipId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.tips.update((rows) => rows.map((tip) => (tip.id === tipId ? { ...tip, isResolved: true } : tip)));
        },
      });
  }

  scoreTone(score: number | null): 'high' | 'mid' | 'low' {
    const value = score ?? 0;
    if (value >= 80) {
      return 'high';
    }
    if (value >= 60) {
      return 'mid';
    }
    return 'low';
  }

  scoreLabel(score: number | null): string {
    const tone = this.scoreTone(score);
    if (tone === 'high') {
      return 'Strong';
    }
    if (tone === 'mid') {
      return 'Moderate';
    }
    return 'Needs work';
  }

  scoreClass(score: number | null): string {
    return `po-score--${this.scoreTone(score)}`;
  }

  offerAgeDays(createdAt: string): number {
    const ageMs = Date.now() - new Date(createdAt).getTime();
    return Math.max(0, Math.floor(ageMs / 86400000));
  }

  jobOfferKeywords(offer: JobOfferDto): string[] {
    if (!offer.extractedKeywords) {
      return [];
    }

    try {
      const parsed = JSON.parse(offer.extractedKeywords) as unknown;
      return Array.isArray(parsed) ? parsed.map((entry) => String(entry)) : [];
    } catch {
      return [];
    }
  }

  colorClassByIndex(index: number): string {
    return `po-offer-theme--${index % 5}`;
  }

  navigateToOffer(jobId: string): void {
    void this.router.navigate(['/dashboard/job-offers', jobId]);
  }

  openPipeline(pipelineId: 'linkedin' | 'github'): void {
    if (pipelineId === 'linkedin') {
      void this.router.navigate(['/dashboard/linkedin']);
      return;
    }

    if (pipelineId === 'github') {
      void this.router.navigate(['/dashboard/github']);
    }
  }

  private clampScore(value: number): number {
    return Math.max(0, Math.min(100, Math.round(value)));
  }
}
