import { CommonModule } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize, forkJoin, of, switchMap } from 'rxjs';
import { catchError } from 'rxjs/operators';
import {
  CandidateCvDto,
  CompletenessResult,
  DiffResult,
  CvCompleteness,
  CvVersionDto,
  ParsedCvContent,
} from '../../../../core/models/profile-optimizer.models';
import { ActiveCvService } from '../../../../core/services/active-cv.service';
import { ProfileOptimizerService } from '../../../../core/services/profile-optimizer.service';
import { ToastComponent } from '../../../../shared/components/toast/toast.component';
import { ToastService } from '../../../../shared/components/toast/toast.service';
import { LUCIDE_ICONS } from '../../../../shared/lucide-icons';

@Component({
  selector: 'app-cv-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, LUCIDE_ICONS, ToastComponent],
  templateUrl: './cv-detail.component.html',
  styleUrl: './cv-detail.component.scss',
})
export class CvDetailComponent implements OnInit {
  // NOTE: Existing codebase pattern uses centralized LUCIDE_ICONS instead of per-component icon imports.
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly optimizerApi = inject(ProfileOptimizerService);
  readonly activeCvService = inject(ActiveCvService);
  private readonly toastService = inject(ToastService);

  readonly loading = signal(true);
  readonly deleting = signal(false);
  readonly optimizing = signal(false);
  readonly error = signal('');
  readonly confirmingDelete = signal(false);
  readonly cv = signal<CandidateCvDto | null>(null);
  readonly originalCv = signal<CandidateCvDto | null>(null);
  readonly versions = signal<CvVersionDto[]>([]);
  readonly selectedVersion = signal<CvVersionDto | null>(null);

  readonly isTailoredView = computed(() => this.selectedVersion() !== null);
  readonly selectedScore = computed(() => this.selectedVersion()?.atsScore ?? this.cv()?.atsScore ?? 0);

  readonly parsedContent = computed<ParsedCvContent | null>(() => {
    const raw = this.cv()?.parsedContent;
    if (!raw) {
      return null;
    }

    try {
      return JSON.parse(raw) as ParsedCvContent;
    } catch {
      return null;
    }
  });

  readonly parseError = computed(() => {
    const raw = this.cv()?.parsedContent;
    if (!raw) {
      return '';
    }

    try {
      JSON.parse(raw);
      return '';
    } catch {
      return 'Unable to parse CV analysis payload from API response.';
    }
  });

  readonly skills = computed(() => this.parsedContent()?.skills ?? []);
  readonly experience = computed(() => this.parsedContent()?.experience ?? []);
  readonly education = computed(() => this.parsedContent()?.education ?? []);

  readonly aiCompleteness = computed<CompletenessResult | null>(() => {
    const raw = this.selectedVersion()?.completenessAnalysis ?? this.cv()?.completenessAnalysis;
    return this.parseJson<CompletenessResult>(raw);
  });

  readonly completenessEntries = computed(() => Object.entries(this.aiCompleteness()?.sections ?? {}));

  readonly aiDiff = computed<DiffResult | null>(() => this.parseJson<DiffResult>(this.selectedVersion()?.diffContent));

  readonly diffSections = computed(() => this.aiDiff()?.sections ?? []);

  readonly completeness = computed<CvCompleteness>(() => {
    const parsed = this.parsedContent();
    return {
      contact: !!parsed?.name && (!!parsed?.email || !!parsed?.phone),
      summary: !!parsed?.summary,
      skills: !!parsed?.skills?.length,
      experience: !!parsed?.experience?.length,
      education: !!parsed?.education?.length,
    };
  });

  readonly completenessCount = computed(() => {
    const items = Object.values(this.completeness());
    return items.filter(Boolean).length;
  });

  ngOnInit(): void {
    this.activeCvService.loadActiveCv();
    this.route.paramMap
      .pipe(
        switchMap((params) => {
          const cvId = params.get('cvId');
          if (!cvId) {
            this.error.set('No CV id was provided.');
            this.loading.set(false);
            return of(null);
          }

          this.loading.set(true);
          this.error.set('');
          return forkJoin({
            cv: this.optimizerApi.getCvById(cvId),
            versions: this.optimizerApi.getCvVersions(cvId).pipe(catchError(() => of([] as CvVersionDto[]))),
          });
        }),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: (payload) => {
          if (!payload) {
            return;
          }

          this.cv.set(payload.cv);
          this.originalCv.set(payload.cv);
          this.versions.set(payload.versions);

          const versionId = this.route.snapshot.queryParamMap.get('version');
          if (versionId) {
            const found = payload.versions.find((item) => item.id === versionId) ?? null;
            this.selectedVersion.set(found);
          } else {
            this.selectedVersion.set(null);
          }

          this.loading.set(false);
        },
        error: (err: Error) => {
          this.error.set(err.message);
          this.loading.set(false);
        },
      });
  }

  backToManager(): void {
    void this.router.navigate(['/dashboard/cv-manager']);
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

  scoreDelta(): number {
    if (!this.selectedVersion()) {
      return 0;
    }

    const current = this.selectedVersion()?.atsScore ?? 0;
    const original = this.originalCv()?.atsScore ?? 0;
    return current - original;
  }

  completenessScore(): number {
    return this.aiCompleteness()?.overallScore ?? this.completenessCount() * 20;
  }

  completenessLabel(): string {
    const score = this.completenessScore();
    if (score >= 80) {
      return 'Complete';
    }
    if (score >= 60) {
      return 'Nearly there';
    }
    return 'Needs work';
  }

  optimizeCv(): void {
    const cv = this.cv();
    if (!cv) {
      return;
    }

    this.optimizing.set(true);
    this.optimizerApi
      .optimizeCv(cv.id)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.optimizing.set(false))
      )
      .subscribe({
        next: (version) => {
          this.versions.update((rows) => [version, ...rows.filter((item) => item.id !== version.id)]);
          this.selectedVersion.set(version);
          const refreshedCv = this.cv();
          if (refreshedCv) {
            this.cv.set({ ...refreshedCv, atsScore: version.atsScore, completenessAnalysis: refreshedCv.completenessAnalysis });
          }
          this.toastService.show('success', 'Generic optimization created');
        },
        error: (err: Error) => {
          this.toastService.show('error', err.message);
        },
      });
  }

  setAsActive(): void {
    const cv = this.cv();
    if (!cv) {
      return;
    }

    this.activeCvService.setActiveCv(cv);
    this.toastService.show('success', 'Active CV updated');
  }

  promptDelete(): void {
    this.confirmingDelete.set(true);
  }

  cancelDelete(): void {
    this.confirmingDelete.set(false);
  }

  deleteCv(): void {
    const cv = this.cv();
    if (!cv) {
      return;
    }

    this.deleting.set(true);
    this.optimizerApi
      .deleteCv(cv.id)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.deleting.set(false))
      )
      .subscribe({
        next: () => {
          if (this.activeCvService.isActive(cv.id)) {
            this.activeCvService.clearActiveCv();
          }
          this.toastService.show('success', 'CV deleted');
          void this.router.navigate(['/dashboard/cv-manager']);
        },
        error: () => {
          this.toastService.show('error', 'Failed to delete CV');
        },
      });
  }

  downloadTailoredPdf(): void {
    const version = this.selectedVersion();
    if (!version) {
      return;
    }

    this.optimizerApi
      .exportCvVersionPdf(version.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (blob) => {
          const objectUrl = URL.createObjectURL(blob);
          const anchor = document.createElement('a');
          anchor.href = objectUrl;
          anchor.download = `tailored-${version.id}.pdf`;
          anchor.click();
          URL.revokeObjectURL(objectUrl);
          this.toastService.show('success', 'Downloaded!');
        },
        error: () => {
          this.toastService.show('error', 'Download failed');
        },
      });
  }

  goToTailor(): void {
    const cv = this.cv();
    if (!cv) {
      return;
    }
    void this.router.navigate(['/dashboard/job-offers'], { queryParams: { cvId: cv.id } });
  }

  private parseJson<T>(raw: string | null | undefined): T | null {
    if (!raw) {
      return null;
    }

    try {
      return JSON.parse(raw) as T;
    } catch {
      return null;
    }
  }
}
