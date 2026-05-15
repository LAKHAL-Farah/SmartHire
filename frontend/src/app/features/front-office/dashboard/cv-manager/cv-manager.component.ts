import { CommonModule } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { finalize, forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { CandidateCvDto, CvVersionDto, ParsedCvContent } from '../../../../core/models/profile-optimizer.models';
import { resolveCurrentProfileUserId } from '../../../../core/services/current-user-id';
import { ActiveCvService } from '../../../../core/services/active-cv.service';
import { ProfileOptimizerService } from '../../../../core/services/profile-optimizer.service';
import { LUCIDE_ICONS } from '../../../../shared/lucide-icons';
import { ToastComponent } from '../../../../shared/components/toast/toast.component';
import { ToastService } from '../../../../shared/components/toast/toast.service';

type ManagerTab = 'uploaded' | 'tailored';
type UploadedSort = 'newest' | 'oldest' | 'highest' | 'lowest';
type TailoredSort = 'newest' | 'highest' | 'lowest';

@Component({
  selector: 'app-cv-manager',
  standalone: true,
  imports: [CommonModule, RouterLink, LUCIDE_ICONS, ToastComponent],
  templateUrl: './cv-manager.component.html',
  styleUrl: './cv-manager.component.scss',
})
export class CvManagerComponent implements OnInit {
  // NOTE: Existing codebase pattern uses centralized LUCIDE_ICONS instead of per-component icon imports.
  private readonly optimizerApi = inject(ProfileOptimizerService);
  readonly activeCvService = inject(ActiveCvService);
  private readonly toastService = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  readonly loading = signal(true);
  readonly parsing = signal(false);
  readonly tab = signal<ManagerTab>('uploaded');

  readonly uploadedSort = signal<UploadedSort>('newest');
  readonly tailoredSort = signal<TailoredSort>('newest');

  readonly dragOver = signal(false);
  readonly selectedFile = signal<File | null>(null);
  readonly uploadError = signal('');

  readonly cvs = signal<CandidateCvDto[]>([]);
  readonly tailoredVersions = signal<CvVersionDto[]>([]);

  readonly deletingCvId = signal<string | null>(null);
  readonly downloadingVersionId = signal<string | null>(null);
  readonly optimizingCvId = signal<string | null>(null);
  readonly scoreRingRadius = 26;
  readonly scoreRingCircumference = 2 * Math.PI * this.scoreRingRadius;

  readonly activeCv = this.activeCvService.activeCv;

  private pickLatestActiveCv(cvs: CandidateCvDto[]): CandidateCvDto | null {
    return [...cvs]
      .filter((cv) => cv.isActive)
      .sort((left, right) => {
        const leftTime = new Date(left.updatedAt ?? left.uploadedAt ?? 0).getTime();
        const rightTime = new Date(right.updatedAt ?? right.uploadedAt ?? 0).getTime();
        return rightTime - leftTime;
      })[0] ?? null;
  }

  readonly uploadedList = computed(() => {
    const rows = [...this.cvs()];
    const mode = this.uploadedSort();

    if (mode === 'newest') {
      return rows.sort((a, b) => new Date(b.uploadedAt).getTime() - new Date(a.uploadedAt).getTime());
    }
    if (mode === 'oldest') {
      return rows.sort((a, b) => new Date(a.uploadedAt).getTime() - new Date(b.uploadedAt).getTime());
    }
    if (mode === 'highest') {
      return rows.sort((a, b) => (b.atsScore ?? 0) - (a.atsScore ?? 0));
    }

    return rows.sort((a, b) => (a.atsScore ?? 0) - (b.atsScore ?? 0));
  });

  readonly tailoredList = computed(() => {
    const rows = [...this.tailoredVersions()];
    const mode = this.tailoredSort();

    if (mode === 'newest') {
      return rows.sort((a, b) => new Date(b.generatedAt).getTime() - new Date(a.generatedAt).getTime());
    }
    if (mode === 'highest') {
      return rows.sort((a, b) => (b.atsScore ?? 0) - (a.atsScore ?? 0));
    }

    return rows.sort((a, b) => (a.atsScore ?? 0) - (b.atsScore ?? 0));
  });

  ngOnInit(): void {
    this.activeCvService.loadActiveCv();
    this.loadData();
  }

  loadData(): void {
    this.loading.set(true);
    forkJoin({
      cvs: this.optimizerApi.getAllCvs().pipe(catchError(() => of([] as CandidateCvDto[]))),
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(({ cvs }) => {
        const userCvs = cvs.filter((cv) => cv.userId === resolveCurrentProfileUserId());
        const backendActiveCv = this.pickLatestActiveCv(userCvs);
        const currentActiveCv = this.activeCv();
        this.cvs.set(userCvs);

        if (backendActiveCv) {
          if (currentActiveCv?.id !== backendActiveCv.id) {
            this.activeCvService.setActiveCv(backendActiveCv);
          }
        } else if (!currentActiveCv) {
          const initial = userCvs[0] ?? null;
          if (initial) {
            this.activeCvService.setActiveCv(initial);
          }
        }

        this.loadTailoredVersions(userCvs);
      });
  }

  loadTailoredVersions(cvs: CandidateCvDto[]): void {
    if (!cvs.length) {
      this.tailoredVersions.set([]);
      this.loading.set(false);
      return;
    }

    const requests = cvs.map((cv) =>
      this.optimizerApi.getCvVersions(cv.id).pipe(catchError(() => of([] as CvVersionDto[])))
    );

    forkJoin(requests)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((versionSets) => {
        const merged = versionSets.flat().filter((version) => version.versionType !== 'ORIGINAL');
        this.tailoredVersions.set(merged);
        this.loading.set(false);
      });
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragOver.set(true);
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    this.dragOver.set(false);
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragOver.set(false);
    this.selectedFile.set(event.dataTransfer?.files?.item(0) ?? null);
  }

  onFilePicked(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile.set(input.files?.item(0) ?? null);
    input.value = '';
  }

  clearSelectedFile(): void {
    this.selectedFile.set(null);
    this.uploadError.set('');
  }

  parseSelectedCv(): void {
    const file = this.selectedFile();
    if (!file) {
      return;
    }

    this.uploadError.set('');
    this.parsing.set(true);

    this.optimizerApi
      .uploadCv(file, resolveCurrentProfileUserId() ?? undefined)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.parsing.set(false))
      )
      .subscribe({
        next: (cv) => {
          this.selectedFile.set(null);
          this.cvs.update((rows) => [cv, ...rows.filter((row) => row.id !== cv.id)]);
          this.activeCvService.setActiveCv(cv);
          this.toastService.show('success', 'Parsed!');
          this.loadTailoredVersions(this.cvs());
        },
        error: (error: Error) => {
          this.uploadError.set(error.message);
          this.toastService.show('error', 'Parsing failed');
        },
      });
  }

  setAsActive(cv: CandidateCvDto): void {
    // NOTE: Backend endpoint for toggling active CV is not exposed in current API service.
    // We keep active state in ActiveCvService + localStorage as required.
    this.activeCvService.setActiveCv(cv);
    this.toastService.show('info', 'Active CV updated');
  }

  parseTopSkills(cv: CandidateCvDto): string[] {
    if (!cv.parsedContent) {
      return [];
    }

    try {
      const parsed = JSON.parse(cv.parsedContent) as ParsedCvContent;
      return parsed.skills?.slice(0, 3) ?? [];
    } catch {
      return [];
    }
  }

  isParsedContentInvalid(cv: CandidateCvDto): boolean {
    if (!cv.parsedContent) {
      return false;
    }

    try {
      JSON.parse(cv.parsedContent);
      return false;
    } catch {
      return true;
    }
  }

  promptDelete(cvId: string): void {
    this.deletingCvId.set(cvId);
  }

  cancelDelete(): void {
    this.deletingCvId.set(null);
  }

  confirmDelete(cv: CandidateCvDto): void {
    this.optimizerApi
      .deleteCv(cv.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.cvs.update((rows) => rows.filter((item) => item.id !== cv.id));
          if (this.activeCvService.isActive(cv.id)) {
            this.activeCvService.clearActiveCv();
          }
          this.deletingCvId.set(null);
          this.toastService.show('success', 'CV deleted');
          this.loadTailoredVersions(this.cvs());
        },
        error: () => this.toastService.show('error', 'Failed to delete CV'),
      });
  }

  exportVersionAsPdf(versionId: string): void {
    this.downloadingVersionId.set(versionId);
    this.optimizerApi
      .exportCvVersionPdf(versionId)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.downloadingVersionId.set(null))
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

  optimizeCv(cv: CandidateCvDto): void {
    this.optimizingCvId.set(cv.id);
    this.optimizerApi
      .optimizeCv(cv.id)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.optimizingCvId.set(null))
      )
      .subscribe({
        next: (version) => {
          this.tailoredVersions.update((rows) => [version, ...rows.filter((item) => item.id !== version.id)]);
          this.tab.set('tailored');
          this.toastService.show('success', 'CV optimized');
        },
        error: (error: Error) => this.toastService.show('error', error.message),
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

  scoreDelta(version: CvVersionDto): number {
    const original = this.cvs().find((cv) => cv.id === version.cvId);
    const base = original?.atsScore ?? 0;
    return (version.atsScore ?? 0) - base;
  }

  cvFileName(cvId: string): string {
    return this.cvs().find((cv) => cv.id === cvId)?.originalFileName ?? 'Unknown CV';
  }

  setTab(next: ManagerTab): void {
    this.tab.set(next);
  }
}
