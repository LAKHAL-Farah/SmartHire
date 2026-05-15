import { CommonModule, DOCUMENT } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, HostListener, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, finalize, forkJoin, map, of, Subscription, switchMap } from 'rxjs';
import {
  NodeCourseContentDto,
  NodeProjectLabDto,
  NodeProjectValidationResponseDto,
  ProjectSubmissionDto,
  ProjectSuggestionDto,
  RoadmapApiService,
} from '../../../../../services/roadmap-api.service';
import { resolveRoadmapUserId } from '../roadmap-user-context';

type WorkspaceMode = 'course' | 'lab' | 'challenge';
type ChallengeFilter = 'all' | 'beginner' | 'intermediate' | 'advanced';

interface WorkspaceContext {
  mode: WorkspaceMode;
  roadmapId: number;
  userId: number;
  nodeId: number;
  stepOrder: number;
  stepTitle: string;
  refresh: boolean;
  generate: boolean;
  historyId: number | null;
  generatedAt: string | null;
  challengeId: number | null;
}

interface ChallengeWorkspaceCard {
  id: number;
  createdAt?: string;
  title: string;
  description: string;
  estimatedDays: number;
  difficulty: string;
  techStack: string[];
  repoUrlDraft: string;
  submission: ProjectSubmissionDto | null;
  submitting: boolean;
  reviewLoading: boolean;
  reviewText: string | null;
}

@Component({
  selector: 'app-roadmap-workspace',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './roadmap-workspace.component.html',
  styleUrl: './roadmap-workspace.component.scss',
})
export class RoadmapWorkspaceComponent implements OnInit, OnDestroy {
  private readonly roadmapApi = inject(RoadmapApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly document = inject(DOCUMENT);

  private queryParamSub: Subscription | null = null;
  private infoTimer: ReturnType<typeof setTimeout> | null = null;

  readonly maxSubmissionRetries = 3;

  readonly context = signal<WorkspaceContext | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly infoMessage = signal<string | null>(null);

  readonly courseLoading = signal(false);
  readonly course = signal<NodeCourseContentDto | null>(null);
  readonly courseHistory = signal<NodeCourseContentDto[]>([]);
  readonly activeLessonIndex = signal(0);
  readonly lessonCompletionState = signal<Record<number, boolean>>({});

  readonly labLoading = signal(false);
  readonly projectLab = signal<NodeProjectLabDto | null>(null);
  readonly projectLabHistory = signal<NodeProjectLabDto[]>([]);
  readonly projectSolutionDraft = signal('');
  readonly projectValidation = signal<NodeProjectValidationResponseDto | null>(null);
  readonly projectValidationLoading = signal(false);

  readonly challengesLoading = signal(false);
  readonly challenges = signal<ChallengeWorkspaceCard[]>([]);
  readonly activeChallengeId = signal<number | null>(null);
  readonly challengeFilter = signal<ChallengeFilter>('all');

  readonly mode = computed<WorkspaceMode>(() => this.context()?.mode ?? 'course');

  readonly lessonCount = computed(() => this.course()?.lessons?.length ?? 0);

  readonly completedLessonCount = computed(() => {
    const lessonCount = this.lessonCount();
    if (lessonCount === 0) {
      return 0;
    }

    let completed = 0;
    const completion = this.lessonCompletionState();
    for (let index = 0; index < lessonCount; index += 1) {
      if (completion[index]) {
        completed += 1;
      }
    }
    return completed;
  });

  readonly courseProgressPercent = computed(() => {
    const lessonCount = this.lessonCount();
    if (lessonCount === 0) {
      return 0;
    }
    return Math.round((this.completedLessonCount() / lessonCount) * 100);
  });

  readonly activeLesson = computed(() => {
    const course = this.course();
    if (!course?.lessons?.length) {
      return null;
    }

    const index = Math.max(0, Math.min(this.activeLessonIndex(), course.lessons.length - 1));
    return course.lessons[index] ?? null;
  });

  readonly activeLessonDone = computed(
    () => this.lessonCompletionState()[this.activeLessonIndex()] === true
  );

  readonly canOpenPreviousLesson = computed(() => this.activeLessonIndex() > 0);

  readonly canOpenNextLesson = computed(() => this.activeLessonIndex() < this.lessonCount() - 1);

  readonly activeChallenge = computed(() => {
    const activeId = this.activeChallengeId();
    if (!activeId) {
      return null;
    }

    return this.challenges().find((challenge) => challenge.id === activeId) ?? null;
  });

  readonly filteredChallenges = computed(() => {
    const filter = this.challengeFilter();
    const challenges = this.challenges();

    if (filter === 'all') {
      return challenges;
    }

    return challenges.filter(
      (challenge) => (challenge.difficulty || '').toLowerCase() === filter
    );
  });

  readonly activeChallengeRepoUrl = computed(() => {
    const challenge = this.activeChallenge();
    if (!challenge) {
      return '';
    }

    return (challenge.repoUrlDraft || challenge.submission?.repoUrl || '').trim();
  });

  ngOnInit(): void {
    this.queryParamSub = this.route.queryParamMap.subscribe(() => {
      this.initializeFromRoute();
    });
  }

  ngOnDestroy(): void {
    this.queryParamSub?.unsubscribe();
    this.queryParamSub = null;

    if (this.infoTimer) {
      clearTimeout(this.infoTimer);
      this.infoTimer = null;
    }
  }

  @HostListener('window:keydown', ['$event'])
  onWorkspaceKeydown(event: KeyboardEvent): void {
    if (this.mode() !== 'course' || this.lessonCount() === 0) {
      return;
    }

    const target = event.target as HTMLElement | null;
    const targetTag = (target?.tagName || '').toLowerCase();
    if (targetTag === 'input' || targetTag === 'textarea' || target?.isContentEditable) {
      return;
    }

    if (event.key === 'ArrowLeft') {
      event.preventDefault();
      this.openPreviousLesson();
      return;
    }

    if (event.key === 'ArrowRight') {
      event.preventDefault();
      this.openNextLesson();
    }
  }

  private initializeFromRoute(): void {
    const parsed = this.parseWorkspaceContext();
    if (!parsed) {
      this.context.set(null);
      this.errorMessage.set('Missing roadmap workspace context. Open this page from the roadmap panel.');
      return;
    }

    let context = parsed;
    if (context.mode === 'challenge' && !context.challengeId) {
      const persistedChallengeId = this.readPersistedChallengeId(context);
      if (persistedChallengeId) {
        context = {
          ...context,
          challengeId: persistedChallengeId,
        };
      }
    }

    this.context.set(context);
    this.errorMessage.set(null);
  this.setInfoMessage(null);

    if (context.mode === 'course') {
      this.activeLessonIndex.set(this.readPersistedLessonIndex(context));
      this.lessonCompletionState.set(this.readPersistedLessonCompletion(context));
    }

    this.loadModeData(context);
  }

  modeLabel(mode: WorkspaceMode): string {
    if (mode === 'course') {
      return 'Course Studio';
    }
    if (mode === 'lab') {
      return 'Project Lab Studio';
    }
    return 'Challenge Studio';
  }

  setChallengeFilter(filter: ChallengeFilter): void {
    this.challengeFilter.set(filter);

    const filtered = this.filteredChallenges();
    if (filtered.length === 0) {
      this.activeChallengeId.set(null);
      return;
    }

    const active = this.activeChallengeId();
    if (!active || !filtered.some((challenge) => challenge.id === active)) {
      this.selectChallenge(filtered[0].id);
    }
  }

  isChallengeFilterActive(filter: ChallengeFilter): boolean {
    return this.challengeFilter() === filter;
  }

  jumpToLesson(rawIndex: string | number): void {
    const index = Number(rawIndex);
    if (!Number.isFinite(index)) {
      return;
    }

    this.openLesson(index);
  }

  toggleActiveLessonDone(): void {
    const index = this.activeLessonIndex();
    const wasDone = this.lessonCompletionState()[index] === true;

    this.lessonCompletionState.update((state) => ({
      ...state,
      [index]: !wasDone,
    }));

    this.persistLessonCompletionState();
    this.setInfoMessage(
      wasDone
        ? `Page ${index + 1} marked as not done.`
        : `Page ${index + 1} marked as done.`
    );
  }

  copyActiveLessonSnippet(): void {
    const snippet = this.activeLesson()?.codeSnippet || '';
    this.copyToClipboard(snippet, 'Lesson code snippet copied.');
  }

  copyStarterCode(): void {
    const starter = this.projectLab()?.starterCode || '';
    this.copyToClipboard(starter, 'Starter code copied.');
  }

  resetProjectSolutionToStarter(): void {
    const starter = this.projectLab()?.starterCode || '';
    if (!starter.trim()) {
      this.errorMessage.set('No starter code is available for this lab yet.');
      return;
    }

    this.projectSolutionDraft.set(starter);
    this.projectValidation.set(null);
    this.setInfoMessage('Solution reset to starter code.');
  }

  openActiveRepository(): void {
    const repoUrl = this.activeChallengeRepoUrl();
    if (!repoUrl) {
      this.errorMessage.set('No repository URL is available for this challenge yet.');
      return;
    }

    if (typeof window !== 'undefined') {
      window.open(repoUrl, '_blank', 'noopener');
    }
  }

  buildModeQuery(mode: WorkspaceMode): Record<string, string | number> {
    const context = this.context();
    if (!context) {
      return {};
    }

    const query: Record<string, string | number> = {
      mode,
      roadmapId: context.roadmapId,
      userId: context.userId,
      nodeId: context.nodeId,
      stepOrder: context.stepOrder,
      stepTitle: context.stepTitle,
    };

    if (mode === 'challenge') {
      const activeId = this.activeChallengeId();
      if (activeId) {
        query['challengeId'] = activeId;
      }
    }

    const historySource =
      mode === 'course' ? this.course() : mode === 'lab' ? this.projectLab() : null;

    if (historySource?.historyId) {
      query['historyId'] = historySource.historyId;
    }

    if (historySource?.generatedAt) {
      query['generatedAt'] = historySource.generatedAt;
    }

    return query;
  }

  openLesson(index: number): void {
    const lessonCount = this.lessonCount();
    if (lessonCount === 0) {
      return;
    }

    const target = Math.max(0, Math.min(index, lessonCount - 1));
    this.activeLessonIndex.set(target);
    this.persistActiveLessonIndex(target);
  }

  openPreviousLesson(): void {
    this.openLesson(this.activeLessonIndex() - 1);
  }

  openNextLesson(): void {
    this.openLesson(this.activeLessonIndex() + 1);
  }

  reloadCourse(refresh: boolean): void {
    const context = this.context();
    if (!context) {
      return;
    }

    this.loadCourseWorkspace({
      ...context,
      refresh,
    });
  }

  selectCourseVersion(version: NodeCourseContentDto): void {
    const normalized = this.normalizeCoursePayload(version);
    this.course.set(normalized);
    this.activeLessonIndex.set(0);
    this.persistActiveLessonIndex(0);
  }

  reloadProjectLab(refresh: boolean): void {
    const context = this.context();
    if (!context) {
      return;
    }

    this.loadLabWorkspace({
      ...context,
      refresh,
    });
  }

  selectProjectLabFromHistory(version: NodeProjectLabDto): void {
    this.applyProjectLab(version, this.projectLabHistory());
  }

  setProjectSolutionDraft(value: string): void {
    this.projectSolutionDraft.set(value);
    if (this.projectValidation()) {
      this.projectValidation.set(null);
    }
  }

  validateProjectSolution(): void {
    const context = this.context();
    const projectLab = this.projectLab();

    if (!context || !projectLab) {
      this.errorMessage.set('Project lab context is unavailable.');
      return;
    }

    const code = this.projectSolutionDraft().trim();
    if (!code) {
      this.errorMessage.set('Write or paste your solution code before validation.');
      return;
    }

    this.projectValidationLoading.set(true);

    this.roadmapApi
      .validateNodeProject(context.nodeId, context.userId, {
        projectTitle: projectLab.projectTitle,
        language: projectLab.language,
        acceptanceCriteria: projectLab.acceptanceCriteria,
        code,
      })
      .pipe(finalize(() => this.projectValidationLoading.set(false)))
      .subscribe({
        next: (validation) => {
          this.projectValidation.set(validation);
          this.errorMessage.set(null);
          this.setInfoMessage(
            validation.passed
              ? 'Validation passed. Great progress.'
              : 'Validation updated with improvement hints.'
          );
        },
        error: (err: HttpErrorResponse) => {
          this.errorMessage.set(
            this.extractHttpErrorMessage(err) ||
              'Could not validate your project solution right now.'
          );
        },
      });
  }

  refreshChallenges(): void {
    const context = this.context();
    if (!context) {
      return;
    }

    this.loadChallengeWorkspace(
      {
        ...context,
        generate: false,
      },
      false
    );
  }

  generateNewChallenge(): void {
    const context = this.context();
    if (!context) {
      return;
    }

    this.loadChallengeWorkspace(
      {
        ...context,
        generate: true,
      },
      true
    );
  }

  selectChallenge(challengeId: number): void {
    this.activeChallengeId.set(challengeId);
    this.persistChallengeId(challengeId);
  }

  setActiveChallengeRepoUrl(value: string): void {
    const challenge = this.activeChallenge();
    if (!challenge) {
      return;
    }

    this.patchChallenge(challenge.id, {
      repoUrlDraft: value,
    });
  }

  canSubmitChallenge(challenge: ChallengeWorkspaceCard): boolean {
    if (!challenge.submission) {
      return true;
    }
    return challenge.submission.retryCount < this.maxSubmissionRetries;
  }

  formatSubmissionStatus(status: string | undefined): string {
    const normalized = (status || 'PENDING_REVIEW').toLowerCase().replace(/_/g, ' ');
    return normalized.charAt(0).toUpperCase() + normalized.slice(1);
  }

  hasChallengeScores(challenge: ChallengeWorkspaceCard): boolean {
    const submission = challenge.submission;
    if (!submission) {
      return false;
    }

    return [
      submission.score,
      submission.readmeScore,
      submission.structureScore,
      submission.testScore,
      submission.ciScore,
    ].some((value) => typeof value === 'number');
  }

  submitActiveChallenge(): void {
    const context = this.context();
    const challenge = this.activeChallenge();
    if (!context || !challenge) {
      this.errorMessage.set('Select a challenge first.');
      return;
    }

    if (!this.canSubmitChallenge(challenge)) {
      this.errorMessage.set('Retry limit reached for this challenge submission.');
      return;
    }

    const repoUrl = (challenge.repoUrlDraft || challenge.submission?.repoUrl || '').trim();
    if (!repoUrl) {
      this.errorMessage.set('Add a repository URL before submitting this challenge.');
      return;
    }

    const request$ = challenge.submission
      ? this.roadmapApi.retryProjectSubmission(challenge.submission.id, { repoUrl })
      : this.roadmapApi.submitProject({
          userId: context.userId,
          projectSuggestionId: challenge.id,
          repoUrl,
        });

    this.patchChallenge(challenge.id, {
      submitting: true,
    });

    request$
      .pipe(
        finalize(() => {
          this.patchChallenge(challenge.id, { submitting: false });
        })
      )
      .subscribe({
        next: (submission) => {
          this.patchChallenge(challenge.id, {
            submission,
            repoUrlDraft: submission.repoUrl || repoUrl,
            reviewText: submission.aiFeedback || challenge.reviewText,
          });
          this.errorMessage.set(null);
          this.setInfoMessage(
            challenge.submission ? 'Challenge resubmitted.' : 'Challenge submitted.'
          );

          if (!(submission.aiFeedback || '').trim()) {
            this.loadChallengeReview(challenge.id);
          }
        },
        error: (err: HttpErrorResponse) => {
          this.errorMessage.set(
            this.extractHttpErrorMessage(err) ||
              'Could not submit this challenge right now.'
          );
        },
      });
  }

  loadActiveChallengeReview(): void {
    const challenge = this.activeChallenge();
    if (!challenge) {
      this.errorMessage.set('Select a challenge first.');
      return;
    }

    this.loadChallengeReview(challenge.id);
  }

  private loadChallengeReview(challengeId: number): void {
    const challenge = this.challenges().find((item) => item.id === challengeId);
    if (!challenge?.submission?.id) {
      this.errorMessage.set('Submit the challenge project before requesting review.');
      return;
    }

    this.patchChallenge(challengeId, { reviewLoading: true });

    this.roadmapApi
      .getProjectSubmissionReview(challenge.submission.id)
      .pipe(
        finalize(() => {
          this.patchChallenge(challengeId, { reviewLoading: false });
        })
      )
      .subscribe({
        next: (payload) => {
          const reviewText = (payload.review || '').trim();
          const updatedSubmission: ProjectSubmissionDto = {
            ...challenge.submission!,
            status: payload.status || challenge.submission!.status,
            score: payload.score ?? challenge.submission!.score,
            readmeScore: payload.readmeScore ?? challenge.submission!.readmeScore,
            structureScore:
              payload.structureScore ?? challenge.submission!.structureScore,
            testScore: payload.testScore ?? challenge.submission!.testScore,
            ciScore: payload.ciScore ?? challenge.submission!.ciScore,
            recommendations:
              payload.recommendations ?? challenge.submission!.recommendations,
            reviewedAt: payload.reviewedAt || challenge.submission!.reviewedAt,
            aiFeedback: reviewText || challenge.submission!.aiFeedback,
          };

          this.patchChallenge(challengeId, {
            submission: updatedSubmission,
            reviewText:
              reviewText ||
              'No AI review text is available yet for this submission.',
          });

          this.errorMessage.set(null);
          this.setInfoMessage('AI review loaded.');
        },
        error: (err: HttpErrorResponse) => {
          this.errorMessage.set(
            this.extractHttpErrorMessage(err) ||
              'Could not load AI review right now.'
          );
        },
      });
  }

  private loadModeData(context: WorkspaceContext): void {
    this.errorMessage.set(null);

    if (context.mode === 'course') {
      this.loadCourseWorkspace(context);
      return;
    }

    if (context.mode === 'lab') {
      this.loadLabWorkspace(context);
      return;
    }

    this.loadChallengeWorkspace(context, context.generate);
  }

  private canUseStorage(): boolean {
    return typeof window !== 'undefined' && !!window.localStorage;
  }

  private buildStorageKey(context: WorkspaceContext, suffix: string): string {
    return `roadmap-workspace:${context.roadmapId}:${context.nodeId}:${context.stepOrder}:${context.userId}:${suffix}`;
  }

  private readPersistedLessonIndex(context: WorkspaceContext): number {
    if (!this.canUseStorage()) {
      return 0;
    }

    const raw = window.localStorage.getItem(this.buildStorageKey(context, 'course-active-lesson'));
    if (!raw) {
      return 0;
    }

    const parsed = Number(raw);
    if (!Number.isFinite(parsed) || parsed < 0) {
      return 0;
    }

    return Math.floor(parsed);
  }

  private persistActiveLessonIndex(index: number): void {
    const context = this.context();
    if (!context || !this.canUseStorage()) {
      return;
    }

    window.localStorage.setItem(
      this.buildStorageKey(context, 'course-active-lesson'),
      String(Math.max(0, Math.floor(index)))
    );
  }

  private readPersistedLessonCompletion(context: WorkspaceContext): Record<number, boolean> {
    if (!this.canUseStorage()) {
      return {};
    }

    const raw = window.localStorage.getItem(
      this.buildStorageKey(context, 'course-lesson-completion')
    );
    if (!raw) {
      return {};
    }

    try {
      const parsed = JSON.parse(raw) as Record<string, unknown>;
      const normalized: Record<number, boolean> = {};

      for (const [key, value] of Object.entries(parsed || {})) {
        const index = Number(key);
        if (Number.isFinite(index) && index >= 0 && value === true) {
          normalized[Math.floor(index)] = true;
        }
      }

      return normalized;
    } catch {
      return {};
    }
  }

  private sanitizeLessonCompletionState(
    completion: Record<number, boolean>,
    lessonCount: number
  ): Record<number, boolean> {
    if (lessonCount <= 0) {
      return {};
    }

    const normalized: Record<number, boolean> = {};
    for (const [rawIndex, isDone] of Object.entries(completion)) {
      const index = Number(rawIndex);
      if (!Number.isFinite(index) || index < 0 || index >= lessonCount || isDone !== true) {
        continue;
      }
      normalized[Math.floor(index)] = true;
    }

    return normalized;
  }

  private persistLessonCompletionState(): void {
    const context = this.context();
    if (!context || !this.canUseStorage()) {
      return;
    }

    const normalized = this.sanitizeLessonCompletionState(
      this.lessonCompletionState(),
      this.lessonCount()
    );

    window.localStorage.setItem(
      this.buildStorageKey(context, 'course-lesson-completion'),
      JSON.stringify(normalized)
    );
  }

  private readPersistedChallengeId(context: WorkspaceContext): number | null {
    if (!this.canUseStorage()) {
      return null;
    }

    const raw = window.localStorage.getItem(
      this.buildStorageKey(context, 'challenge-active-id')
    );
    const parsed = Number(raw);

    if (!Number.isFinite(parsed) || parsed <= 0) {
      return null;
    }

    return Math.floor(parsed);
  }

  private persistChallengeId(challengeId: number): void {
    const context = this.context();
    if (!context || !this.canUseStorage()) {
      return;
    }

    window.localStorage.setItem(
      this.buildStorageKey(context, 'challenge-active-id'),
      String(challengeId)
    );
  }

  private setInfoMessage(message: string | null, autoClearMs = 2600): void {
    this.infoMessage.set(message);

    if (this.infoTimer) {
      clearTimeout(this.infoTimer);
      this.infoTimer = null;
    }

    if (!message) {
      return;
    }

    this.infoTimer = setTimeout(() => {
      this.infoMessage.set(null);
      this.infoTimer = null;
    }, autoClearMs);
  }

  private copyToClipboard(value: string, successMessage: string): void {
    const content = (value || '').trim();
    if (!content) {
      this.errorMessage.set('Nothing to copy yet.');
      return;
    }

    if (typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
      navigator.clipboard
        .writeText(content)
        .then(() => {
          this.errorMessage.set(null);
          this.setInfoMessage(successMessage);
        })
        .catch(() => this.copyWithFallback(content, successMessage));
      return;
    }

    this.copyWithFallback(content, successMessage);
  }

  private copyWithFallback(value: string, successMessage: string): void {
    try {
      const textarea = this.document.createElement('textarea');
      textarea.value = value;
      textarea.setAttribute('readonly', 'true');
      textarea.style.position = 'fixed';
      textarea.style.opacity = '0';

      this.document.body.appendChild(textarea);
      textarea.focus();
      textarea.select();

      const copied = this.document.execCommand('copy');
      this.document.body.removeChild(textarea);

      if (!copied) {
        this.errorMessage.set('Clipboard copy failed. Copy manually from the editor.');
        return;
      }

      this.errorMessage.set(null);
      this.setInfoMessage(successMessage);
    } catch {
      this.errorMessage.set('Clipboard copy failed. Copy manually from the editor.');
    }
  }

  private loadCourseWorkspace(context: WorkspaceContext): void {
    this.courseLoading.set(true);

    this.roadmapApi
      .getNodeCourseHistory(context.nodeId, context.userId)
      .pipe(
        catchError(() => of([] as NodeCourseContentDto[])),
        switchMap((historyPayload) => {
          const normalizedHistory = (historyPayload || []).map((entry) =>
            this.normalizeCoursePayload(entry)
          );
          this.courseHistory.set(normalizedHistory);

          const selectedFromHistory = context.refresh
            ? null
            : this.pickCourseVersionFromContext(normalizedHistory, context);

          if (selectedFromHistory) {
            return of({
              course: selectedFromHistory,
              history: normalizedHistory,
            });
          }

          return this.roadmapApi
            .getNodeCourse(context.nodeId, context.userId, context.refresh)
            .pipe(
              map((course) => ({
                course: this.normalizeCoursePayload(course),
                history: normalizedHistory,
              }))
            );
        }),
        finalize(() => this.courseLoading.set(false))
      )
      .subscribe({
        next: ({ course, history }) => {
          this.course.set(course);
          this.courseHistory.set(this.mergeCourseHistory(course, history));

          const persistedIndex = this.readPersistedLessonIndex(context);
          const maxIndex = Math.max(0, (course.lessons?.length || 1) - 1);
          const activeIndex = Math.min(persistedIndex, maxIndex);
          this.activeLessonIndex.set(activeIndex);
          this.persistActiveLessonIndex(activeIndex);

          const persistedCompletion = this.readPersistedLessonCompletion(context);
          const normalizedCompletion = this.sanitizeLessonCompletionState(
            persistedCompletion,
            course.lessons?.length || 0
          );
          this.lessonCompletionState.set(normalizedCompletion);
          this.persistLessonCompletionState();
        },
        error: (err: HttpErrorResponse) => {
          this.errorMessage.set(
            this.extractHttpErrorMessage(err) ||
              'Could not load this course workspace right now.'
          );
        },
      });
  }

  private loadLabWorkspace(context: WorkspaceContext): void {
    this.labLoading.set(true);

    this.roadmapApi
      .getNodeProjectLabHistory(context.nodeId, context.userId)
      .pipe(
        catchError(() => of([] as NodeProjectLabDto[])),
        switchMap((historyPayload) => {
          const history = historyPayload || [];
          this.projectLabHistory.set(history);

          const selectedFromHistory = context.refresh
            ? null
            : this.pickProjectLabFromContext(history, context);

          if (selectedFromHistory) {
            return of({
              projectLab: selectedFromHistory,
              history,
            });
          }

          return this.roadmapApi
            .getNodeProjectLab(context.nodeId, context.userId)
            .pipe(map((projectLab) => ({ projectLab, history })));
        }),
        finalize(() => this.labLoading.set(false))
      )
      .subscribe({
        next: ({ projectLab, history }) => {
          const mergedHistory = this.mergeProjectLabHistory(projectLab, history);
          this.applyProjectLab(projectLab, mergedHistory);
        },
        error: (err: HttpErrorResponse) => {
          this.errorMessage.set(
            this.extractHttpErrorMessage(err) ||
              'Could not load this project lab right now.'
          );
        },
      });
  }

  private loadChallengeWorkspace(context: WorkspaceContext, generate: boolean): void {
    this.challengesLoading.set(true);

    const suggestions$ = generate
      ? this.roadmapApi
          .generateProjectSuggestionsByRoadmapStep(
            context.roadmapId,
            context.stepOrder,
            context.stepTitle,
            'INTERMEDIATE'
          )
          .pipe(
            switchMap((generated) =>
              this.roadmapApi
                .getProjectSuggestionsByRoadmapStep(context.roadmapId, context.stepOrder)
                .pipe(
                  map((history) => (history.length > 0 ? history : generated)),
                  catchError(() => of(generated))
                )
            )
          )
      : this.roadmapApi.getProjectSuggestionsByRoadmapStep(
          context.roadmapId,
          context.stepOrder
        );

    forkJoin({
      suggestions: suggestions$.pipe(catchError(() => of([] as ProjectSuggestionDto[]))),
      submissions: this.roadmapApi
        .getUserProjectSubmissions(context.userId)
        .pipe(catchError(() => of([] as ProjectSubmissionDto[]))),
    })
      .pipe(finalize(() => this.challengesLoading.set(false)))
      .subscribe({
        next: ({ suggestions, submissions }) => {
          const cards = this.toChallengeCards(
            suggestions,
            submissions,
            this.challenges()
          );
          this.challenges.set(cards);

          const filteredCards = this.filterChallengesByDifficulty(
            cards,
            this.challengeFilter()
          );
          const selectionPool = filteredCards.length > 0 ? filteredCards : cards;

          const preferredId = context.challengeId ?? this.activeChallengeId();
          const selected =
            selectionPool.find((challenge) => challenge.id === preferredId) ||
            selectionPool[0] ||
            null;
          this.activeChallengeId.set(selected?.id ?? null);

          if (selected?.id) {
            this.persistChallengeId(selected.id);
          }

          if (cards.length === 0 && generate) {
            this.errorMessage.set('No challenge was generated for this node. Try again.');
          }
        },
        error: (err: HttpErrorResponse) => {
          this.errorMessage.set(
            this.extractHttpErrorMessage(err) ||
              'Could not load challenge workspace right now.'
          );
        },
      });
  }

  private filterChallengesByDifficulty(
    challenges: ChallengeWorkspaceCard[],
    filter: ChallengeFilter
  ): ChallengeWorkspaceCard[] {
    if (filter === 'all') {
      return challenges;
    }

    return challenges.filter(
      (challenge) => (challenge.difficulty || '').toLowerCase() === filter
    );
  }

  private parseWorkspaceContext(): WorkspaceContext | null {
    const params = this.route.snapshot.queryParamMap;
    const modeParam = params.get('mode');
    const mode: WorkspaceMode =
      modeParam === 'lab' || modeParam === 'challenge' ? modeParam : 'course';

    const roadmapId = this.toPositiveInt(params.get('roadmapId'));
    const nodeId = this.toPositiveInt(params.get('nodeId'));
    const stepOrder = this.toPositiveInt(params.get('stepOrder')) ?? 1;
    const userId = this.toPositiveInt(params.get('userId')) ?? resolveRoadmapUserId();

    if (!roadmapId || !nodeId || !userId) {
      return null;
    }

    const stepTitle = (params.get('stepTitle') || `Step ${stepOrder}`).trim();

    return {
      mode,
      roadmapId,
      userId,
      nodeId,
      stepOrder,
      stepTitle,
      refresh: this.toBooleanFlag(params.get('refresh')),
      generate: this.toBooleanFlag(params.get('generate')),
      historyId: this.toPositiveInt(params.get('historyId')),
      generatedAt: params.get('generatedAt'),
      challengeId: this.toPositiveInt(params.get('challengeId')),
    };
  }

  private toChallengeCards(
    suggestions: ProjectSuggestionDto[],
    submissions: ProjectSubmissionDto[],
    existing: ChallengeWorkspaceCard[]
  ): ChallengeWorkspaceCard[] {
    const bySuggestion: Record<number, ProjectSubmissionDto> = {};

    for (const submission of submissions || []) {
      if (!submission?.projectSuggestionId) {
        continue;
      }

      const current = bySuggestion[submission.projectSuggestionId];
      if (!current) {
        bySuggestion[submission.projectSuggestionId] = submission;
        continue;
      }

      const currentTime = current.submittedAt ? new Date(current.submittedAt).getTime() : 0;
      const nextTime = submission.submittedAt ? new Date(submission.submittedAt).getTime() : 0;
      if (nextTime >= currentTime) {
        bySuggestion[submission.projectSuggestionId] = submission;
      }
    }

    const existingById = new Map(existing.map((challenge) => [challenge.id, challenge]));

    const sortedSuggestions = [...(suggestions || [])].sort((left, right) => {
      const rightRaw = right.createdAt ? new Date(right.createdAt).getTime() : 0;
      const leftRaw = left.createdAt ? new Date(left.createdAt).getTime() : 0;
      const rightTs = Number.isFinite(rightRaw) ? rightRaw : 0;
      const leftTs = Number.isFinite(leftRaw) ? leftRaw : 0;
      if (rightTs !== leftTs) {
        return rightTs - leftTs;
      }
      return (right.id || 0) - (left.id || 0);
    });

    return sortedSuggestions.map((suggestion) => {
      const existingCard = existingById.get(suggestion.id);
      const submission = bySuggestion[suggestion.id] || existingCard?.submission || null;
      const repoUrlDraft = existingCard?.repoUrlDraft || submission?.repoUrl || '';

      return {
        id: suggestion.id,
        createdAt: suggestion.createdAt,
        title: suggestion.title,
        description: suggestion.description,
        estimatedDays: suggestion.estimatedDays,
        difficulty: suggestion.difficulty,
        techStack: suggestion.techStack || [],
        repoUrlDraft,
        submission,
        submitting: existingCard?.submitting ?? false,
        reviewLoading: existingCard?.reviewLoading ?? false,
        reviewText: existingCard?.reviewText || submission?.aiFeedback || null,
      };
    });
  }

  private patchChallenge(challengeId: number, patch: Partial<ChallengeWorkspaceCard>): void {
    this.challenges.update((challenges) =>
      challenges.map((challenge) =>
        challenge.id === challengeId
          ? {
              ...challenge,
              ...patch,
            }
          : challenge
      )
    );
  }

  private pickCourseVersionFromContext(
    history: NodeCourseContentDto[],
    context: WorkspaceContext
  ): NodeCourseContentDto | null {
    if (history.length === 0) {
      return null;
    }

    if (context.historyId) {
      const byId = history.find((entry) => entry.historyId === context.historyId);
      if (byId) {
        return byId;
      }
    }

    if (context.generatedAt) {
      const byDate = history.find((entry) => entry.generatedAt === context.generatedAt);
      if (byDate) {
        return byDate;
      }
    }

    return history[0];
  }

  private mergeCourseHistory(
    latest: NodeCourseContentDto,
    history: NodeCourseContentDto[]
  ): NodeCourseContentDto[] {
    return [latest, ...(history || [])].filter(
      (entry, index, all) =>
        all.findIndex(
          (candidate) =>
            (candidate.historyId && entry.historyId && candidate.historyId === entry.historyId) ||
            (!!candidate.generatedAt && candidate.generatedAt === entry.generatedAt) ||
            (!candidate.historyId && !entry.historyId &&
              !candidate.generatedAt && !entry.generatedAt &&
              candidate.courseTitle === entry.courseTitle && candidate.nodeId === entry.nodeId)
        ) === index
    );
  }

  private normalizeCoursePayload(course: NodeCourseContentDto): NodeCourseContentDto {
    return {
      ...course,
      courseTitle: course.courseTitle || `${course.nodeTitle || 'Node'} Course`,
      intro: course.intro || 'Practical node course.',
      difficulty: course.difficulty || 'BEGINNER',
      lessons: (course.lessons || []).map((lesson) => ({
        ...lesson,
        sectionTitle: lesson.sectionTitle || 'Lesson',
        explanation: lesson.explanation || 'Practice this concept with a small example.',
        miniExample: lesson.miniExample || '',
        codeSnippet: lesson.codeSnippet || '',
        commonPitfalls: lesson.commonPitfalls || [],
        practiceTasks: lesson.practiceTasks || [],
      })),
      checkpoints: (course.checkpoints || []).map((checkpoint) => ({
        question: checkpoint.question || 'Checkpoint question',
        answerHint: checkpoint.answerHint || 'Use a practical explanation.',
      })),
      cheatSheet: course.cheatSheet || [],
      nextNodeFocus: course.nextNodeFocus || '',
    };
  }

  private pickProjectLabFromContext(
    history: NodeProjectLabDto[],
    context: WorkspaceContext
  ): NodeProjectLabDto | null {
    if (history.length === 0) {
      return null;
    }

    if (context.historyId) {
      const byId = history.find((entry) => entry.historyId === context.historyId);
      if (byId) {
        return byId;
      }
    }

    if (context.generatedAt) {
      const byDate = history.find((entry) => entry.generatedAt === context.generatedAt);
      if (byDate) {
        return byDate;
      }
    }

    return history[0];
  }

  private mergeProjectLabHistory(
    latest: NodeProjectLabDto,
    history: NodeProjectLabDto[]
  ): NodeProjectLabDto[] {
    return [latest, ...(history || [])].filter(
      (entry, index, all) =>
        all.findIndex((candidate) => this.sameProjectLabHistoryEntry(candidate, entry)) === index
    );
  }

  private applyProjectLab(projectLab: NodeProjectLabDto, history: NodeProjectLabDto[]): void {
    this.projectLab.set(projectLab);
    this.projectLabHistory.set(history);
    this.projectSolutionDraft.set(projectLab.starterCode || '');
    this.projectValidation.set(null);
  }

  private sameProjectLabHistoryEntry(left: NodeProjectLabDto, right: NodeProjectLabDto): boolean {
    if (left.historyId && right.historyId) {
      return left.historyId === right.historyId;
    }

    const leftGeneratedAt = left.generatedAt || '';
    const rightGeneratedAt = right.generatedAt || '';
    if (leftGeneratedAt && rightGeneratedAt) {
      return leftGeneratedAt === rightGeneratedAt;
    }

    return left.projectTitle === right.projectTitle && left.language === right.language;
  }

  private extractHttpErrorMessage(error: HttpErrorResponse): string {
    if (typeof error.error === 'string') {
      return error.error;
    }

    const payload = error.error as { message?: string; error?: string } | null;
    if (payload?.message) {
      return payload.message;
    }
    if (payload?.error) {
      return payload.error;
    }

    return error.message || '';
  }

  private toPositiveInt(value: string | null): number | null {
    if (!value) {
      return null;
    }

    const parsed = Number(value);
    if (!Number.isFinite(parsed) || parsed <= 0) {
      return null;
    }

    return Math.floor(parsed);
  }

  private toBooleanFlag(value: string | null): boolean {
    return value === '1' || value === 'true';
  }
}
