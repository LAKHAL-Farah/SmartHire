import { CommonModule, DatePipe } from '@angular/common';
import {
  Component,
  DestroyRef,
  HostListener,
  OnDestroy,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  animate,
  query,
  stagger,
  style,
  transition,
  trigger,
} from '@angular/animations';
import { catchError, finalize, forkJoin, map, of } from 'rxjs';
import { LUCIDE_ICONS } from '../../../../shared/lucide-icons';
import {
  CandidateCvDto,
  CreateJobOfferRequest,
  CvVersionDto,
  JobOfferDto,
} from '../../../../core/models/profile-optimizer.models';
import { ProfileOptimizerService } from '../../../../core/services/profile-optimizer.service';
import { resolveCurrentProfileUserId } from '../../../../core/services/current-user-id';

type ToastType = 'success' | 'error';
type UploadState = 'empty' | 'selected' | 'parsing' | 'success' | 'error';
type ScrollTarget = 'tailor' | 'upload' | null;

type Toast = {
  id: number;
  type: ToastType;
  message: string;
};

type LoadingState = {
  page: boolean;
  upload: boolean;
  tailor: boolean;
  score: boolean;
  jobOffer: boolean;
  export: string | null;
};

type ErrorState = {
  upload: string | null;
  tailor: string | null;
  jobOffer: string | null;
  page: string | null;
};

@Component({
  selector: 'app-profile-optimizer',
  standalone: true,
  imports: [CommonModule, FormsModule, DatePipe, RouterLink, LUCIDE_ICONS],
  templateUrl: './profile-optimizer.component.html',
  styleUrl: './profile-optimizer.component.scss',
  animations: [
    trigger('cvPanelTransition', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateY(8px)' }),
        animate('200ms ease-out', style({ opacity: 1, transform: 'translateY(0)' })),
      ]),
      transition(':leave', [
        animate('200ms ease-in', style({ opacity: 0, transform: 'translateY(8px)' })),
      ]),
    ]),
    trigger('expandBlock', [
      transition(':enter', [
        style({ height: '0px', opacity: 0, overflow: 'hidden' }),
        animate('200ms ease', style({ height: '*', opacity: 1 })),
      ]),
      transition(':leave', [
        animate('180ms ease', style({ height: '0px', opacity: 0 })),
      ]),
    ]),
    trigger('versionExpand', [
      transition(':enter', [
        style({ height: '0px', opacity: 0, overflow: 'hidden' }),
        animate('250ms ease', style({ height: '*', opacity: 1 })),
      ]),
      transition(':leave', [
        animate('200ms ease', style({ height: '0px', opacity: 0 })),
      ]),
    ]),
    trigger('toastSlide', [
      transition(':enter', [
        style({ transform: 'translateX(100%)', opacity: 0 }),
        animate('250ms ease-out', style({ transform: 'translateX(0)', opacity: 1 })),
      ]),
      transition(':leave', [
        animate('180ms ease-in', style({ transform: 'translateX(100%)', opacity: 0 })),
      ]),
    ]),
    trigger('statusBarStagger', [
      transition(':enter', [
        query(
          '.po-stat',
          [
            style({ opacity: 0, transform: 'translateY(-8px)' }),
            stagger(150, animate('220ms ease-out', style({ opacity: 1, transform: 'translateY(0)' }))),
          ],
          { optional: true }
        ),
      ]),
    ]),
    trigger('slideOverBackdrop', [
      transition(':enter', [
        style({ opacity: 0 }),
        animate('280ms ease-out', style({ opacity: 1 })),
      ]),
      transition(':leave', [
        animate('220ms ease-in', style({ opacity: 0 })),
      ]),
    ]),
    trigger('slideOverPanel', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateY(16px) scale(0.96)' }),
        animate('220ms ease-out', style({ opacity: 1, transform: 'translateY(0) scale(1)' })),
      ]),
      transition(':leave', [
        animate('180ms ease-in', style({ opacity: 0, transform: 'translateY(8px) scale(0.96)' })),
      ]),
    ]),
  ],
})
export class ProfileOptimizerComponent implements OnInit, OnDestroy {
  // NOTE: Lucide icons remain registered through the shared dashboard icon module used by this codebase.
  private readonly optimizerApi = inject(ProfileOptimizerService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly activeCv = signal<CandidateCvDto | null>(null);
  readonly cvs = signal<CandidateCvDto[]>([]);
  readonly cvVersions = signal<CvVersionDto[]>([]);
  readonly jobOffers = signal<JobOfferDto[]>([]);
  readonly selectedJobOfferId = signal<string | null>(null);
  readonly expandedVersionId = signal<string | null>(null);
  readonly jobOfferPanelOpen = signal(false);

  readonly loading = signal<LoadingState>({
    page: true,
    upload: false,
    tailor: false,
    score: false,
    jobOffer: false,
    export: null,
  });

  readonly errors = signal<ErrorState>({
    upload: null,
    tailor: null,
    jobOffer: null,
    page: null,
  });

  readonly toasts = signal<Toast[]>([]);
  readonly uploadState = signal<UploadState>('empty');
  readonly uploadErrorMessage = signal<string | null>(null);
  readonly jobOfferSubmitted = signal(false);
  readonly selectedFile = signal<File | null>(null);
  readonly dragOver = signal(false);
  readonly tailorStatus = signal('');
  readonly focusedCvId = signal<string | null>(null);
  readonly pendingScrollTarget = signal<ScrollTarget>(null);

  readonly canTailor = computed(() => this.activeCv() !== null && this.selectedJobOfferId() !== null);
  readonly statusScore = computed(() => this.activeCv()?.atsScore ?? 0);
  readonly scorePercent = computed(() => Math.min(100, Math.max(0, Number(this.statusScore()) || 0)));
  readonly currentUserCvs = computed(() =>
    [...this.cvs()].sort((left, right) => new Date(right.uploadedAt).getTime() - new Date(left.uploadedAt).getTime())
  );
  readonly recentCvs = computed(() => this.currentUserCvs().slice(0, 3));
  readonly originalVersion = computed(() => this.cvVersions().find((version) => version.versionType === 'ORIGINAL') ?? null);
  readonly tailoredVersions = computed(() => this.cvVersions().filter((version) => version.versionType !== 'ORIGINAL'));
  readonly selectedJobOffer = computed(() => this.jobOffers().find((offer) => offer.id === this.selectedJobOfferId()) ?? null);

  selectedFileName = '';
  jobTitle = '';
  jobCompany = '';
  jobDescription = '';
  jobSourceUrl = '';
  confirmingDeleteJobOfferId: string | null = null;
  downloadedVersionId = signal<string | null>(null);

  private toastCounter = 0;
  private tailorStatusIntervalId: number | null = null;
  private uploadSuccessTimeoutId: number | null = null;

  ngOnInit(): void {
    this.route.queryParamMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => {
        this.focusedCvId.set(params.get('cvId'));
        const action = params.get('action');
        const scroll = params.get('scroll');
        if (action === 'tailor') {
          this.pendingScrollTarget.set('tailor');
        } else if (scroll === 'upload') {
          this.pendingScrollTarget.set('upload');
        }
      });

    this.loadInitial();
  }

  ngOnDestroy(): void {
    this.stopTailorStatusCycle();
    if (this.uploadSuccessTimeoutId != null) {
      window.clearTimeout(this.uploadSuccessTimeoutId);
      this.uploadSuccessTimeoutId = null;
    }
    document.body.style.overflow = '';
  }

  @HostListener('document:keydown.escape')
  handleEscape(): void {
    if (this.jobOfferPanelOpen()) {
      this.closeJobOfferPanel();
    }
  }

  openJobOfferPanel(): void {
    this.jobOfferSubmitted.set(false);
    this.errors.update((current) => ({ ...current, jobOffer: null }));
    this.jobOfferPanelOpen.set(true);
    document.body.style.overflow = 'hidden';
  }

  closeJobOfferPanel(): void {
    this.jobOfferPanelOpen.set(false);
    this.resetJobOfferForm();
    this.jobOfferSubmitted.set(false);
    this.errors.update((current) => ({ ...current, jobOffer: null }));
    document.body.style.overflow = '';
  }

  loadInitial(): void {
    this.patchLoading({ page: true });
    this.patchErrors({ page: null });

    const currentUserId = resolveCurrentProfileUserId();
    if (!currentUserId) {
      this.patchErrors({ page: 'Session expired. Please log in again.' });
      this.patchLoading({ page: false });
      return;
    }

    const cvs$ = this.optimizerApi.getAllCvs().pipe(
      map((rows) => rows.filter((cv) => cv.userId === currentUserId)),
      catchError((err: Error) => {
        this.patchErrors({ page: err.message });
        return of([] as CandidateCvDto[]);
      })
    );

    const offers$ = this.optimizerApi.listJobOffers().pipe(
      map((rows) => rows.filter((offer) => offer.userId === currentUserId)),
      catchError((err: Error) => {
        this.patchErrors({ page: this.errors().page ? this.errors().page : err.message });
        return of([] as JobOfferDto[]);
      })
    );

    forkJoin({ cvs: cvs$, offers: offers$ })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(({ cvs, offers }) => {
        const sortedCvs = this.sortCvs(cvs);
        this.cvs.set(sortedCvs);
        this.jobOffers.set(this.sortJobOffers(offers));
        this.selectedJobOfferId.set(this.jobOffers()[0]?.id ?? null);

        const active = this.pickInitialCv(sortedCvs);
        this.activeCv.set(active);

        if (!active) {
          this.patchLoading({ page: false });
          this.runPendingScrollAction();
          return;
        }

        this.refreshScoreAndVersions(active.id, true);
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
    this.setSelectedFile(event.dataTransfer?.files?.item(0) ?? null);
  }

  onFilePicked(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.setSelectedFile(input.files?.item(0) ?? null);
    input.value = '';
  }

  parseSelectedCv(): void {
    const file = this.selectedFile();
    if (!file) {
      this.uploadErrorMessage.set('Please choose a CV file before uploading.');
      this.uploadState.set('error');
      return;
    }

    this.patchErrors({ upload: null });
    this.uploadErrorMessage.set(null);
    this.uploadState.set('parsing');
    this.patchLoading({ upload: true });

    this.optimizerApi
      .uploadCv(file)
      .pipe(
        finalize(() => this.patchLoading({ upload: false })),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: (cv) => {
          this.cvs.update((rows) => this.insertOrReplaceCv(rows, cv));
          this.activeCv.set(cv);
          this.selectedFile.set(null);
          this.selectedFileName = '';
          this.uploadState.set('success');

          if (this.uploadSuccessTimeoutId != null) {
            window.clearTimeout(this.uploadSuccessTimeoutId);
          }

          this.uploadSuccessTimeoutId = window.setTimeout(() => {
            this.uploadState.set('empty');
            this.uploadErrorMessage.set(null);
            void this.router.navigate(['/dashboard/cv-detail', cv.id]);
          }, 1500);
        },
        error: (err: Error) => {
          this.uploadState.set('error');
          this.uploadErrorMessage.set(err.message);
          this.patchErrors({ upload: err.message });
        },
      });
  }

  removeSelectedFile(): void {
    this.resetUploadState();
  }

  retryUpload(): void {
    this.resetUploadState();
  }

  openCvDetail(cv: CandidateCvDto): void {
    if (cv.parseStatus !== 'COMPLETED') {
      return;
    }

    void this.router.navigate(['/dashboard/cv-detail', cv.id]);
  }

  saveJobOffer(): void {
    this.jobOfferSubmitted.set(true);

    if (!this.jobTitle.trim() || !this.jobDescription.trim()) {
      return;
    }

    const body: CreateJobOfferRequest = {
      title: this.jobTitle.trim(),
      company: this.jobCompany.trim() || undefined,
      rawDescription: this.jobDescription.trim(),
      sourceUrl: this.jobSourceUrl.trim() || undefined,
    };

    this.patchErrors({ jobOffer: null });
    this.patchLoading({ jobOffer: true });

    this.optimizerApi
      .createJobOffer(body)
      .pipe(
        finalize(() => this.patchLoading({ jobOffer: false })),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: (created) => {
          this.jobOffers.update((rows) => this.sortJobOffers([created, ...rows.filter((offer) => offer.id !== created.id)]));
          this.selectedJobOfferId.set(created.id);
          this.pushToast('success', 'Job offer saved');
          this.closeJobOfferPanel();
        },
        error: () => {
          this.patchErrors({ jobOffer: 'Failed to save. Please try again.' });
        },
      });
  }

  promptDeleteJobOffer(id: string): void {
    this.confirmingDeleteJobOfferId = id;
  }

  cancelDeleteJobOffer(): void {
    this.confirmingDeleteJobOfferId = null;
  }

  confirmDeleteJobOffer(id: string): void {
    this.optimizerApi
      .deleteJobOffer(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.jobOffers.update((rows) => rows.filter((row) => row.id !== id));
          if (this.selectedJobOfferId() === id) {
            this.selectedJobOfferId.set(this.jobOffers()[0]?.id ?? null);
          }
          this.confirmingDeleteJobOfferId = null;
          this.pushToast('success', 'Job offer deleted');
        },
        error: (err: Error) => {
          this.pushToast('error', err.message);
        },
      });
  }

  runTailor(): void {
    const cv = this.activeCv();
    const jobOfferId = this.selectedJobOfferId();
    if (!cv || !jobOfferId) {
      return;
    }

    this.patchErrors({ tailor: null });
    this.patchLoading({ tailor: true });
    this.startTailorStatusCycle();

    this.optimizerApi
      .tailorCv(cv.id, jobOfferId)
      .pipe(
        finalize(() => {
          this.patchLoading({ tailor: false });
          this.stopTailorStatusCycle();
        }),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: (newVersion) => {
          this.cvVersions.update((rows) => [newVersion, ...rows]);
          this.refreshScore(cv.id);
          this.pushToast('success', 'Tailored version created');
        },
        error: (err: Error) => {
          this.patchErrors({ tailor: err.message });
          this.pushToast('error', err.message);
        },
      });
  }

  toggleExpandedVersion(id: string): void {
    this.expandedVersionId.set(this.expandedVersionId() === id ? null : id);
  }

  downloadVersion(version: CvVersionDto): void {
    this.patchLoading({ export: version.id });
    this.optimizerApi
      .exportCvVersionPdf(version.id)
      .pipe(
        finalize(() => this.patchLoading({ export: null })),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: (blob) => {
          const objectUrl = URL.createObjectURL(blob);
          const anchor = document.createElement('a');
          anchor.href = objectUrl;
          anchor.download = `cv-version-${version.id}.pdf`;
          anchor.click();
          URL.revokeObjectURL(objectUrl);

          this.downloadedVersionId.set(version.id);
          window.setTimeout(() => this.downloadedVersionId.set(null), 900);
        },
        error: (err: Error) => {
          this.pushToast('error', err.message);
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

  formatCvDate(value: string): string {
    return new Date(value).toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
    });
  }

  jobTitleError(): boolean {
    return this.jobOfferSubmitted() && !this.jobTitle.trim();
  }

  jobDescriptionError(): boolean {
    return this.jobOfferSubmitted() && !this.jobDescription.trim();
  }

  jobDescriptionChars(): number {
    return this.jobDescription.length;
  }

  canSaveJobOffer(): boolean {
    return !!this.jobTitle.trim() && !!this.jobDescription.trim() && !this.loading().jobOffer;
  }

  offerKeywords(offer: JobOfferDto): string[] {
    if (!offer.extractedKeywords) {
      return [];
    }

    try {
      const parsed = JSON.parse(offer.extractedKeywords) as unknown;
      return Array.isArray(parsed) ? parsed.map((item) => String(item)) : [];
    } catch {
      return [];
    }
  }

  versionJobOfferTitle(version: CvVersionDto): string {
    if (!version.jobOfferId) {
      return 'General optimization';
    }

    const offer = this.jobOffers().find((job) => job.id === version.jobOfferId);
    return offer?.title ?? 'Unknown job offer';
  }

  parseStatusLabel(status: CandidateCvDto['parseStatus']): string {
    switch (status) {
      case 'COMPLETED':
        return 'Parsed';
      case 'PENDING':
        return 'Pending';
      case 'IN_PROGRESS':
        return 'Processing';
      case 'FAILED':
      default:
        return 'Failed';
    }
  }

  setSelectedJobOfferId(id: string): void {
    this.selectedJobOfferId.set(id);
  }

  trackById(_: number, item: { id: string }): string {
    return item.id;
  }

  trackByToast(_: number, item: Toast): number {
    return item.id;
  }

  private setSelectedFile(file: File | null): void {
    this.selectedFile.set(file);
    this.selectedFileName = file?.name ?? '';
    this.uploadErrorMessage.set(null);
    this.patchErrors({ upload: null });
    this.uploadState.set(file ? 'selected' : 'empty');
  }

  private resetUploadState(): void {
    this.selectedFile.set(null);
    this.selectedFileName = '';
    this.dragOver.set(false);
    this.uploadErrorMessage.set(null);
    this.patchErrors({ upload: null });
    this.uploadState.set('empty');

    if (this.uploadSuccessTimeoutId != null) {
      window.clearTimeout(this.uploadSuccessTimeoutId);
      this.uploadSuccessTimeoutId = null;
    }
  }

  private resetJobOfferForm(): void {
    this.jobTitle = '';
    this.jobCompany = '';
    this.jobDescription = '';
    this.jobSourceUrl = '';
  }

  private sortJobOffers(offers: JobOfferDto[]): JobOfferDto[] {
    return [...offers].sort(
      (left, right) => new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime()
    );
  }

  private sortCvs(cvs: CandidateCvDto[]): CandidateCvDto[] {
    return [...cvs].sort((left, right) => new Date(right.uploadedAt).getTime() - new Date(left.uploadedAt).getTime());
  }

  private pickInitialCv(cvs: CandidateCvDto[]): CandidateCvDto | null {
    if (cvs.length === 0) {
      return null;
    }

    const focusId = this.focusedCvId();
    if (focusId) {
      const focused = cvs.find((cv) => cv.id === focusId);
      if (focused) {
        return focused;
      }
    }

    return cvs.find((cv) => cv.isActive) ?? cvs[0] ?? null;
  }

  private refreshScoreAndVersions(cvId: string, initialLoad = false): void {
    this.patchLoading({ score: true });
    forkJoin({
      versions: this.optimizerApi.getCvVersions(cvId).pipe(catchError(() => of([] as CvVersionDto[]))),
      score: this.optimizerApi.getCvScore(cvId).pipe(catchError(() => of(null))),
    })
      .pipe(
        finalize(() => {
          this.patchLoading({ score: false });
          if (initialLoad) {
            this.patchLoading({ page: false });
            this.runPendingScrollAction();
          }
        }),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(({ versions, score }) => {
        this.cvVersions.set(versions);
        if (score?.atsScore != null) {
          this.activeCv.update((cur) => (cur ? { ...cur, atsScore: score.atsScore } : cur));
        }
      });
  }

  private refreshScore(cvId: string): void {
    this.patchLoading({ score: true });
    this.optimizerApi
      .getCvScore(cvId)
      .pipe(
        finalize(() => this.patchLoading({ score: false })),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: (score) => {
          this.activeCv.update((cur) => (cur ? { ...cur, atsScore: score.atsScore } : cur));
        },
      });
  }

  private insertOrReplaceCv(rows: CandidateCvDto[], cv: CandidateCvDto): CandidateCvDto[] {
    const next = rows.filter((row) => row.id !== cv.id);
    next.unshift(cv);
    return next;
  }

  private startTailorStatusCycle(): void {
    this.stopTailorStatusCycle();
    const steps = [
      'Analyzing job requirements...',
      'Matching your experience...',
      'Rewriting key sections...',
      'Finalizing your CV...',
    ];
    let index = 0;
    this.tailorStatus.set(steps[0]);

    this.tailorStatusIntervalId = window.setInterval(() => {
      index = Math.min(index + 1, steps.length - 1);
      this.tailorStatus.set(steps[index]);
    }, 3000);
  }

  private stopTailorStatusCycle(): void {
    if (this.tailorStatusIntervalId != null) {
      window.clearInterval(this.tailorStatusIntervalId);
      this.tailorStatusIntervalId = null;
    }
    this.tailorStatus.set('');
  }

  private runPendingScrollAction(): void {
    const target = this.pendingScrollTarget();
    if (!target) {
      return;
    }

    window.setTimeout(() => {
      this.scrollToElement(target === 'upload' ? 'po-upload-zone' : 'po-job-offers');
      this.pendingScrollTarget.set(null);
    }, 120);
  }

  private scrollToElement(id: string): void {
    const element = document.getElementById(id);
    if (!element) {
      return;
    }

    element.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  private pushToast(type: ToastType, message: string): void {
    const id = ++this.toastCounter;
    this.toasts.update((rows) => [...rows, { id, type, message }]);
    window.setTimeout(() => {
      this.toasts.update((rows) => rows.filter((toast) => toast.id !== id));
    }, 3000);
  }

  private patchLoading(patch: Partial<LoadingState>): void {
    this.loading.update((current) => ({ ...current, ...patch }));
  }

  private patchErrors(patch: Partial<ErrorState>): void {
    this.errors.update((current) => ({ ...current, ...patch }));
  }
}
