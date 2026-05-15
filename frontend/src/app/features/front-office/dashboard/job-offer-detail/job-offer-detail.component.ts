import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize, forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { CandidateCvDto, CvVersionDto, JobOfferDto, LinkedInProfileDto } from '../../../../core/models/profile-optimizer.models';
import { ActiveCvService } from '../../../../core/services/active-cv.service';
import { resolveCurrentProfileUserId } from '../../../../core/services/current-user-id';
import { ProfileOptimizerService } from '../../../../core/services/profile-optimizer.service';
import { LUCIDE_ICONS } from '../../../../shared/lucide-icons';
import { ToastComponent } from '../../../../shared/components/toast/toast.component';
import { ToastService } from '../../../../shared/components/toast/toast.service';

type TailorState = 'idle' | 'processing' | 'success' | 'error';

@Component({
  selector: 'app-job-offer-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, LUCIDE_ICONS, ToastComponent],
  templateUrl: './job-offer-detail.component.html',
  styleUrl: './job-offer-detail.component.scss',
})
export class JobOfferDetailComponent implements OnInit, OnDestroy {
  // NOTE: Existing codebase pattern uses centralized LUCIDE_ICONS instead of per-component icon imports.
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly optimizerApi = inject(ProfileOptimizerService);
  readonly activeCvService = inject(ActiveCvService);
  private readonly toastService = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  readonly loading = signal(true);
  readonly error = signal('');
  readonly deleting = signal(false);

  readonly offer = signal<JobOfferDto | null>(null);
  readonly cvs = signal<CandidateCvDto[]>([]);
  readonly versions = signal<CvVersionDto[]>([]);

  readonly selectedCvId = signal<string | null>(null);
  readonly selectedVersion = signal<CvVersionDto | null>(null);
  readonly tailorState = signal<TailorState>('idle');
  readonly tailorError = signal('');
  readonly downloading = signal(false);
  readonly linkedInProfile = signal<LinkedInProfileDto | null>(null);
  readonly linkedInAligning = signal(false);
  readonly linkedInAligned = signal(false);

  readonly statusMessage = signal('');
  readonly animatedScores = signal<Record<string, number>>({});
  private statusIntervalId: number | null = null;
  private readonly animationTimers = new Map<string, number>();

  readonly parsedCvs = computed(() => this.cvs().filter((cv) => cv.parseStatus === 'COMPLETED'));
  readonly previousVersions = computed(() => this.versions().slice(0, 3));

  readonly scoreDelta = computed(() => {
    const version = this.selectedVersion();
    if (!version) {
      return 0;
    }
    const original = this.cvs().find((cv) => cv.id === version.cvId);
    return (version.atsScore ?? 0) - (original?.atsScore ?? 0);
  });

  readonly themeClass = computed(() => {
    const offer = this.offer();
    if (!offer) {
      return 'po-theme--0';
    }
    const index = this.offerIndexSeed(offer.id) % 6;
    return `po-theme--${index}`;
  });

  ngOnInit(): void {
    this.activeCvService.loadActiveCv();
    const jobId = this.route.snapshot.paramMap.get('jobId');
    if (!jobId) {
      this.error.set('Job offer id is missing');
      this.loading.set(false);
      return;
    }

    this.loadData(jobId);
  }

  ngOnDestroy(): void {
    this.stopStatusCycle();
    for (const timer of this.animationTimers.values()) {
      window.clearInterval(timer);
    }
    this.animationTimers.clear();
  }

  loadData(jobId: string): void {
    this.loading.set(true);
    this.error.set('');
    const requestedCvId = this.route.snapshot.queryParamMap.get('cvId');

    forkJoin({
      offer: this.optimizerApi.getJobOfferById(jobId),
      cvs: this.optimizerApi.getAllCvs().pipe(catchError(() => of([] as CandidateCvDto[]))),
      versions: this.optimizerApi.getCvVersionsForJobOffer(jobId).pipe(catchError(() => of([] as CvVersionDto[]))),
      linkedIn: this.optimizerApi.getLinkedInProfile().pipe(
        catchError((error: unknown) => {
          if (error instanceof HttpErrorResponse && error.status === 404) {
            return of(null);
          }
          return of(null);
        })
      ),
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ offer, cvs, versions, linkedIn }) => {
          this.offer.set(offer);
          const userCvs = cvs.filter((cv) => cv.userId === resolveCurrentProfileUserId());
          this.cvs.set(userCvs);
          this.versions.set(versions.sort((a, b) => new Date(b.generatedAt).getTime() - new Date(a.generatedAt).getTime()));
          this.linkedInProfile.set(linkedIn);

          const active = this.activeCvService.activeCv();
          const firstParsed = userCvs.find((cv) => cv.parseStatus === 'COMPLETED');
          this.selectedCvId.set(requestedCvId ?? active?.id ?? firstParsed?.id ?? null);
          this.startScoreAnimations();

          this.loading.set(false);
        },
        error: () => {
          this.error.set('Unable to load this job offer right now.');
          this.loading.set(false);
        },
      });
  }

  back(): void {
    void this.router.navigate(['/dashboard/job-offers']);
  }

  deleteOffer(): void {
    const offer = this.offer();
    if (!offer) {
      return;
    }

    this.deleting.set(true);
    this.optimizerApi
      .deleteJobOffer(offer.id)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.deleting.set(false))
      )
      .subscribe({
        next: () => {
          this.toastService.show('success', 'Job offer deleted');
          void this.router.navigate(['/dashboard/job-offers']);
        },
        error: () => this.toastService.show('error', 'Failed to delete job offer'),
      });
  }

  tailor(): void {
    const cvId = this.selectedCvId();
    const jobId = this.offer()?.id;
    if (!cvId || !jobId) {
      return;
    }

    this.tailorState.set('processing');
    this.tailorError.set('');
    this.startStatusCycle();

    this.optimizerApi
      .tailorCv(cvId, jobId)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.stopStatusCycle())
      )
      .subscribe({
        next: (version) => {
          this.selectedVersion.set(version);
          this.versions.update((rows) => [version, ...rows.filter((item) => item.id !== version.id)]);
          this.tailorState.set('success');
          this.animateScore(`version-${version.id}`, version.atsScore ?? 0);
          this.toastService.show('success', 'CV Tailored Successfully!');
        },
        error: (error: Error) => {
          this.tailorState.set('error');
          this.tailorError.set(error.message);
          this.toastService.show('error', 'Tailoring failed');
        },
      });
  }

  resetTailor(): void {
    this.selectedVersion.set(null);
    this.tailorState.set('idle');
    this.tailorError.set('');
  }

  downloadVersion(versionId: string): void {
    this.downloading.set(true);
    this.optimizerApi
      .exportCvVersionPdf(versionId)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.downloading.set(false))
      )
      .subscribe({
        next: (blob) => {
          const objectUrl = URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.href = objectUrl;
          a.download = `tailored-${versionId}.pdf`;
          a.click();
          URL.revokeObjectURL(objectUrl);
          this.toastService.show('success', 'Downloaded!');
        },
        error: () => this.toastService.show('error', 'Download failed'),
      });
  }

  viewAnalysis(): void {
    const version = this.selectedVersion();
    if (!version) {
      return;
    }
    void this.router.navigate(['/dashboard/cv-detail', version.cvId], { queryParams: { version: version.id } });
  }

  extractKeywords(): void {
    // NOTE: Dedicated extract-keywords endpoint is not available in the current API service.
    this.toastService.show('info', 'Keywords are extracted when saving or tailoring this offer.');
  }

  keywords(): string[] {
    const raw = this.offer()?.extractedKeywords;
    if (!raw) {
      return [];
    }

    try {
      const parsed = JSON.parse(raw) as unknown;
      return Array.isArray(parsed) ? parsed.map((entry) => String(entry)) : [];
    } catch {
      return [];
    }
  }

  offerIndexSeed(id: string): number {
    return id.split('').reduce((acc, char) => acc + char.charCodeAt(0), 0);
  }

  selectCv(id: string): void {
    this.selectedCvId.set(id);
  }

  selectedCvName(): string {
    const selectedId = this.selectedCvId();
    if (!selectedId) {
      return 'Selected CV';
    }
    return this.parsedCvs().find((cv) => cv.id === selectedId)?.originalFileName ?? 'Selected CV';
  }

  scoreTone(score: number | null): 'high' | 'mid' | 'low' {
    const value = score ?? 0;
    if (value > 90) {
      return 'high';
    }
    if (value >= 70) {
      return 'mid';
    }
    return 'low';
  }

  scoreDisplay(key: string, fallbackScore: number | null): number {
    const value = this.animatedScores()[key];
    if (typeof value === 'number') {
      return value;
    }
    return fallbackScore ?? 0;
  }

  alignLinkedInToThisRole(): void {
    const jobId = this.offer()?.id;
    if (!jobId || !this.linkedInProfile() || this.linkedInProfile()?.analysisStatus !== 'COMPLETED') {
      return;
    }

    this.linkedInAligning.set(true);
    this.linkedInAligned.set(false);

    this.optimizerApi
      .alignLinkedInToJob(jobId)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.linkedInAligning.set(false))
      )
      .subscribe({
        next: (profile) => {
          this.linkedInProfile.set(profile);
          this.linkedInAligned.set(true);
          this.animateScore('linkedin-global', profile.globalScore ?? 0);
          this.toastService.show('success', `LinkedIn profile aligned to ${this.offer()?.title ?? 'selected role'}`);
          window.setTimeout(() => {
            void this.router.navigate(['/dashboard/linkedin']);
          }, 500);
        },
        error: () => {
          this.toastService.show('error', 'Failed to align LinkedIn profile');
        },
      });
  }

  private startStatusCycle(): void {
    this.stopStatusCycle();
    const messages = [
      'Analyzing job requirements...',
      'Matching your experience...',
      'Rewriting key sections...',
      'Finalizing your CV...',
    ];
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

  private startScoreAnimations(): void {
    for (const cv of this.parsedCvs()) {
      this.animateScore(`cv-${cv.id}`, cv.atsScore ?? 0);
    }

    const linkedin = this.linkedInProfile();
    if (linkedin?.analysisStatus === 'COMPLETED') {
      this.animateScore('linkedin-global', linkedin.globalScore ?? 0);
    }
  }

  private animateScore(key: string, target: number): void {
    const boundedTarget = Math.max(0, Math.min(100, Math.round(target)));
    const previousTimer = this.animationTimers.get(key);
    if (previousTimer != null) {
      window.clearInterval(previousTimer);
    }

    if (boundedTarget === 0) {
      this.animatedScores.update((scores) => ({ ...scores, [key]: 0 }));
      return;
    }

    const durationMs = 800;
    const frameMs = 40;
    const totalFrames = Math.max(1, Math.floor(durationMs / frameMs));
    let frame = 0;

    this.animatedScores.update((scores) => ({ ...scores, [key]: 0 }));

    const timer = window.setInterval(() => {
      frame += 1;
      const progress = frame / totalFrames;
      const nextValue = Math.round(boundedTarget * Math.min(progress, 1));
      this.animatedScores.update((scores) => ({ ...scores, [key]: nextValue }));

      if (frame >= totalFrames) {
        window.clearInterval(timer);
        this.animationTimers.delete(key);
      }
    }, frameMs);

    this.animationTimers.set(key, timer);
  }
}
