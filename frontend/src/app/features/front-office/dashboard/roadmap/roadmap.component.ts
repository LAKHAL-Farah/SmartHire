import { CommonModule, DOCUMENT } from '@angular/common';
import { Component, computed, HostListener, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { catchError, finalize, forkJoin, map, Observable, of, switchMap, throwError } from 'rxjs';
import { LUCIDE_ICONS } from '../../../../shared/lucide-icons';
import {
  NodeCourseContentDto,
  NodeProjectLabDto,
  NodeProjectValidationResponseDto,
  NodeQuizResponseDto,
  NodeTutorPromptResponseDto,
  ProjectSubmissionDto,
  ProjectSuggestionDto,
  RoadmapApiService,
  RoadmapResponse,
  RoadmapVisualResponse,
  StepResourceDto,
  StepResponse,
} from '../../../../services/roadmap-api.service';
import { resolveRoadmapUserId } from './roadmap-user-context';

interface ResourceCard {
  type: 'video' | 'article' | 'course';
  title: string;
  source: string;
  url: string;
  isFree: boolean;
  origin: 'roadmap' | 'ai';
}

interface ResourceBuckets {
  free: ResourceCard[];
  premium: ResourceCard[];
  aiTutor: ResourceCard[];
}

interface Step {
  number: number;
  title: string;
  description: string;
  status: 'done' | 'in-progress' | 'pending';
  backendStatus: string;
  estimatedTime: string;
  resources: ResourceCard[];
  resourcesLoaded: boolean;
  resourcesLoading: boolean;
  challenges: ChallengeCard[];
  challengesLoading: boolean;
  projectLab: NodeProjectLabDto | null;
  projectLabLoading: boolean;
  projectLabHistory: NodeProjectLabDto[];
  projectLabHistoryLoading: boolean;
  projectSolutionDraft: string;
  projectValidation: NodeProjectValidationResponseDto | null;
  projectValidationLoading: boolean;
  tutorResponse: NodeTutorPromptResponseDto | null;
  tutorLoading: boolean;
  course: NodeCourseContentDto | null;
  courseLoading: boolean;
  courseHistory: NodeCourseContentDto[];
  courseHistoryLoading: boolean;
  nodeId?: number;
  completionType: 'node' | 'step';
}

interface ChallengeCard {
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

type FilterTab = 'all' | 'todo' | 'in-progress' | 'completed';
type ResourcePanelTab = 'resources' | 'ai-tutor';

interface NodeQuizQuestion {
  id: string;
  prompt: string;
  options: string[];
  correctIndex: number;
}

interface NodeQuizSession {
  stepNumber: number;
  stepTitle: string;
  questions: NodeQuizQuestion[];
  source: 'ai' | 'local';
  activeQuestionIndex: number;
  selectedAnswers: Record<string, number | null>;
  passThreshold: number;
  submitted: boolean;
  scorePercent: number | null;
  passed: boolean;
  badgeLabel: string | null;
  feedback: string | null;
}

interface NodeQuizStorageState {
  passedByStep: Record<string, boolean>;
  scoreByStep: Record<string, number>;
  seenQuestionIdsByStep: Record<string, string[]>;
  attemptByStep: Record<string, number>;
}

interface NodeQuizTemplate {
  id: string;
  prompt: string;
  correct: string;
  distractors: string[];
}

interface RoadmapHubCard {
  id: number;
  title: string;
  statusLabel: string;
  statusTone: 'active' | 'completed' | 'paused' | 'other';
  totalSteps: number;
  completedSteps: number;
  scorePercent: number;
  startedLabel: string;
}

@Component({
  selector: 'app-roadmap',
  standalone: true,
  imports: [CommonModule, RouterLink, LUCIDE_ICONS],
  templateUrl: './roadmap.component.html',
  styleUrl: './roadmap.component.scss',
})
export class RoadmapComponent implements OnInit, OnDestroy {
  private readonly roadmapApi = inject(RoadmapApiService);
  private readonly document = inject(DOCUMENT);
  private readonly router = inject(Router);
  private readonly quizPassThreshold = 70;
  private readonly quizQuestionCount = 5;
  readonly maxSubmissionRetries = 3;
  private previousBodyOverflow: string | null = null;

  isLoading = signal(false);
  errorMessage = signal<string | null>(null);

  activeFilter = signal<FilterTab>('all');
  activeResourceTab = signal<ResourcePanelTab>('resources');
  expandedStep = signal<number | null>(null);
  quizSession = signal<NodeQuizSession | null>(null);
  tutorPromptDraft = signal('');

  private readonly quizPassedState = signal<Record<string, boolean>>({});
  private readonly quizScoresState = signal<Record<string, number>>({});
  private readonly quizSeenQuestionIdsState = signal<Record<string, string[]>>({});
  private readonly quizAttemptCountState = signal<Record<string, number>>({});

  private readonly activeRoadmap = signal<RoadmapResponse | null>(null);
  private readonly roadmapCatalog = signal<RoadmapResponse[]>([]);
  private readonly selectedRoadmapId = signal<number | null>(null);
  private readonly currentUserId = signal<number | null>(null);
  private readonly userSubmissionsBySuggestion = signal<Record<number, ProjectSubmissionDto>>({});
  private readonly stepsState = signal<Step[]>([]);

  filterTabs: { label: string; value: FilterTab }[] = [
    { label: 'All Steps', value: 'all' },
    { label: 'To Do', value: 'todo' },
    { label: 'In Progress', value: 'in-progress' },
    { label: 'Completed', value: 'completed' },
  ];

  get steps(): Step[] {
    return this.stepsState();
  }

  get completedCount(): number {
    return this.stepsState().filter((step) => step.status === 'done').length;
  }

  roadmapTitle = computed(
    () => this.activeRoadmap()?.title || 'My Learning Roadmap'
  );

  roadmapSubtitle = computed(() => {
    const roadmap = this.activeRoadmap();
    if (!roadmap) {
      return 'Loading your personalized roadmap...';
    }

    const parts: string[] = [];
    if (roadmap.careerPath?.title) {
      parts.push(roadmap.careerPath.title);
    }
    if (roadmap.estimatedWeeks && roadmap.estimatedWeeks > 0) {
      parts.push(`Estimated ${roadmap.estimatedWeeks} weeks`);
    }
    if (roadmap.createdAt) {
      parts.push(`Started ${this.formatMonthYear(roadmap.createdAt)}`);
    }

    return parts.length > 0
      ? parts.join(' · ')
      : 'Live roadmap data from backend';
  });

  roadmapCards = computed<RoadmapHubCard[]>(() => {
    return this.roadmapCatalog().map((roadmap) => {
      const totalSteps = Math.max(0, roadmap.totalSteps ?? roadmap.steps?.length ?? 0);
      const completedSteps = Math.max(0, roadmap.completedSteps ?? 0);
      const scorePercent =
        totalSteps > 0 ? Math.round((Math.min(completedSteps, totalSteps) / totalSteps) * 100) : 0;

      return {
        id: roadmap.id,
        title: roadmap.title || `Roadmap #${roadmap.id}`,
        statusLabel: this.toRoadmapStatusLabel(roadmap.status),
        statusTone: this.toRoadmapStatusTone(roadmap.status),
        totalSteps,
        completedSteps,
        scorePercent,
        startedLabel: this.formatRoadmapStartedAt(roadmap.createdAt),
      };
    });
  });

  roadmapHubStats = computed(() => {
    const cards = this.roadmapCards();
    const completed = cards.filter((card) => card.statusTone === 'completed').length;
    const active = cards.filter((card) => card.statusTone === 'active').length;

    return {
      total: cards.length,
      completed,
      active,
    };
  });

  miniPanelCareer = computed(() => {
    const roadmap = this.activeRoadmap();
    if (!roadmap) {
      return 'Roadmap';
    }
    return roadmap.careerPath?.title || roadmap.title || 'Roadmap';
  });

  completionEstimate = computed(() => {
    const roadmap = this.activeRoadmap();
    if (!roadmap?.createdAt || !roadmap.estimatedWeeks || roadmap.estimatedWeeks <= 0) {
      return 'Estimate unavailable';
    }

    const startedAt = new Date(roadmap.createdAt);
    if (Number.isNaN(startedAt.getTime())) {
      return 'Estimate unavailable';
    }

    const estimateDate = new Date(startedAt);
    estimateDate.setDate(estimateDate.getDate() + roadmap.estimatedWeeks * 7);
    return `Est. completion: ${estimateDate.toLocaleDateString('en-US', {
      month: 'short',
      year: 'numeric',
    })}`;
  });

  progressPct = computed(() => {
    const steps = this.stepsState();
    if (!steps.length) {
      return 0;
    }
    const done = steps.filter((step) => step.status === 'done').length;
    return Math.round((done / steps.length) * 100);
  });

  filteredSteps = computed(() => {
    const filter = this.activeFilter();
    const steps = this.stepsState();

    if (filter === 'all') {
      return steps;
    }
    if (filter === 'todo') {
      return steps.filter((step) => step.status === 'pending');
    }
    if (filter === 'in-progress') {
      return steps.filter((step) => step.status === 'in-progress');
    }
    return steps.filter((step) => step.status === 'done');
  });

  ringCircum = 2 * Math.PI * 44;
  ringOffset = computed(() => this.ringCircum * (1 - this.progressPct() / 100));

  quizAnsweredCount = computed(() => {
    const session = this.quizSession();
    if (!session) {
      return 0;
    }

    return session.questions.reduce((count, question) => {
      return session.selectedAnswers[question.id] == null ? count : count + 1;
    }, 0);
  });

  quizProgressPercent = computed(() => {
    const session = this.quizSession();
    if (!session || session.questions.length === 0) {
      return 0;
    }

    return Math.round((this.quizAnsweredCount() / session.questions.length) * 100);
  });

  activeQuizQuestion = computed(() => {
    const session = this.quizSession();
    if (!session) {
      return null;
    }

    return session.questions[session.activeQuestionIndex] ?? null;
  });

  quizCanSubmit = computed(() => {
    const session = this.quizSession();
    if (!session || session.submitted || session.questions.length === 0) {
      return false;
    }

    return session.questions.every((question) => session.selectedAnswers[question.id] != null);
  });

  nextThreeSteps = computed(() =>
    this.stepsState().filter((step) => step.status !== 'done').slice(0, 3)
  );

  expandedStepData = computed(() => {
    const expanded = this.expandedStep();
    if (expanded == null) {
      return null;
    }
    return this.stepsState().find((step) => step.number === expanded) ?? null;
  });

  expandedStepBuckets = computed<ResourceBuckets>(() => {
    const empty: ResourceBuckets = { free: [], premium: [], aiTutor: [] };
    const step = this.expandedStepData();
    if (!step) {
      return empty;
    }

    return {
      free: step.resources.filter((resource) => resource.origin !== 'ai' && resource.isFree),
      premium: step.resources.filter((resource) => resource.origin !== 'ai' && !resource.isFree),
      aiTutor: step.resources.filter((resource) => resource.origin === 'ai'),
    };
  });

  expandedStepIndex = computed(() => {
    const expanded = this.expandedStep();
    if (expanded == null) {
      return -1;
    }

    return this.filteredSteps().findIndex((step) => step.number === expanded);
  });

  canOpenPreviousStep = computed(() => this.expandedStepIndex() > 0);

  canOpenNextStep = computed(() => {
    const index = this.expandedStepIndex();
    return index >= 0 && index < this.filteredSteps().length - 1;
  });

  emptyHeading = computed(() => {
    const filter = this.activeFilter();
    if (filter === 'in-progress') return 'No steps in progress yet';
    if (filter === 'completed') return 'No completed steps yet';
    if (filter === 'todo') return 'Nothing left to do';
    return 'No steps found';
  });

  emptySubtext = computed(() => {
    const filter = this.activeFilter();
    if (filter === 'in-progress') return 'Start your first step to see it here.';
    if (filter === 'completed') return 'Complete a step to track your progress.';
    if (filter === 'todo') return 'All steps are done or currently in progress.';
    return 'Your roadmap currently has no steps.';
  });

  ngOnInit(): void {
    this.loadRoadmap();
  }

  ngOnDestroy(): void {
    this.unlockExamMode();
  }

  @HostListener('window:beforeunload', ['$event'])
  onBeforeUnload(event: BeforeUnloadEvent): void {
    const quiz = this.quizSession();
    if (!quiz || quiz.submitted) {
      return;
    }

    event.preventDefault();
    event.returnValue = '';
  }

  toggleStep(stepNumber: number): void {
    if (this.quizSession()) {
      return;
    }

    const selected = this.stepsState().find((item) => item.number === stepNumber);
    if (selected && this.isStepLocked(selected)) {
      this.errorMessage.set('This node is locked. Complete required previous nodes first.');
      return;
    }

    const nextExpanded = this.expandedStep() === stepNumber ? null : stepNumber;
    this.expandedStep.set(nextExpanded);
    this.activeResourceTab.set('resources');

    if (nextExpanded == null) {
      return;
    }

    const step = this.stepsState().find((item) => item.number === nextExpanded);
    if (step) {
      this.loadStepResources(step);
      this.loadChallengeHistory(step);
      this.loadNodeProjectLabHistory(step);
      this.loadNodeCourseHistory(step);
      this.tutorPromptDraft.set('');
    }
  }

  openPreviousStep(): void {
    this.openAdjacentStep(-1);
  }

  openNextStep(): void {
    this.openAdjacentStep(1);
  }

  private openAdjacentStep(direction: -1 | 1): void {
    if (this.quizSession()) {
      return;
    }

    const currentIndex = this.expandedStepIndex();
    if (currentIndex < 0) {
      return;
    }

    const target = this.filteredSteps()[currentIndex + direction];
    if (!target) {
      return;
    }

    this.expandedStep.set(target.number);
    this.activeResourceTab.set('resources');
    this.loadStepResources(target);
    this.loadChallengeHistory(target);
    this.loadNodeProjectLabHistory(target);
    this.loadNodeCourseHistory(target);
    this.tutorPromptDraft.set('');
  }

  isRoadmapSelected(roadmapId: number): boolean {
    return this.selectedRoadmapId() === roadmapId;
  }

  openRoadmap(roadmapId: number): void {
    if (this.selectedRoadmapId() === roadmapId || this.isLoading()) {
      return;
    }

    if (this.quizSession()) {
      this.errorMessage.set('Finish or close the current quiz session before switching roadmaps.');
      return;
    }

    this.loadRoadmap(roadmapId);
  }

  openCourseWorkspace(
    step: Step,
    refresh = false,
    historyId?: number,
    generatedAt?: string
  ): void {
    this.openWorkspaceTab(step, 'course', {
      refresh: refresh ? 1 : undefined,
      historyId,
      generatedAt,
    });
  }

  openProjectLabWorkspace(
    step: Step,
    refresh = false,
    historyId?: number,
    generatedAt?: string
  ): void {
    this.openWorkspaceTab(step, 'lab', {
      refresh: refresh ? 1 : undefined,
      historyId,
      generatedAt,
    });
  }

  openChallengeWorkspace(
    step: Step,
    challengeId?: number,
    generate = false
  ): void {
    this.openWorkspaceTab(step, 'challenge', {
      challengeId,
      generate: generate ? 1 : undefined,
    });
  }

  private openWorkspaceTab(
    step: Step,
    mode: 'course' | 'lab' | 'challenge',
    extras: Record<string, string | number | undefined> = {}
  ): void {
    const roadmapId = this.activeRoadmap()?.id;
    const nodeId = step.nodeId;
    const userId = this.currentUserId() ?? resolveRoadmapUserId();

    if (!roadmapId || !nodeId || !userId) {
      this.errorMessage.set('Workspace context is unavailable. Please refresh and try again.');
      return;
    }

    if (!this.currentUserId()) {
      this.currentUserId.set(userId);
    }

    const queryParams: Record<string, string | number> = {
      mode,
      roadmapId,
      userId,
      nodeId,
      stepOrder: step.number,
      stepTitle: step.title,
    };

    Object.entries(extras).forEach(([key, value]) => {
      const normalized = typeof value === 'string' ? value.trim() : value;
      if (normalized == null || normalized === '') {
        return;
      }
      queryParams[key] = normalized;
    });

    const url = this.router.serializeUrl(
      this.router.createUrlTree(['/dashboard/roadmap/workspace'], {
        queryParams,
      })
    );

    const view = this.document.defaultView;
    if (!view) {
      return;
    }

    const target = url.startsWith('http')
      ? url
      : `${view.location.origin}${url.startsWith('/') ? url : `/${url}`}`;

    view.open(target, '_blank', 'noopener');
  }

  markComplete(step: Step): void {
    if (!this.hasQuizPassed(step.number)) {
      this.errorMessage.set('Pass the node quiz before marking this step complete.');
      this.startNodeQuiz(step);
      return;
    }

    if (!this.isStepCompletable(step)) {
      this.errorMessage.set(
        'Node is not available for completion yet. Complete required previous nodes first.'
      );
      return;
    }

    const userId = this.currentUserId();
    if (!step.nodeId || !userId) {
      this.errorMessage.set('Cannot complete this step without a valid roadmap node.');
      return;
    }

    const completion$ = this.completeStepRequest(step, userId);

    this.isLoading.set(true);
    this.errorMessage.set(null);

    completion$
      .pipe(finalize(() => this.isLoading.set(false)))
      .subscribe({
        next: (graph) => {
          const visualSteps =
            graph && (graph.nodes?.length ?? 0) > 0
              ? this.mapVisualRoadmapToSteps(graph)
              : [];

          if (visualSteps.length > 0) {
            this.stepsState.set(visualSteps);
            this.syncQuizGateState(visualSteps);
            this.expandInProgressStep();
            return;
          }

          this.loadRoadmap(this.selectedRoadmapId() ?? undefined);
        },
        error: (err: HttpErrorResponse) => {
          const backendMessage =
            (typeof err.error === 'string' ? err.error : err.error?.message) ||
            err.message;
          this.errorMessage.set(
            backendMessage
              ? `Could not sync completion with backend: ${backendMessage}`
              : 'Could not sync completion with backend. Please retry.'
          );
        },
      });
  }

  private completeStepRequest(
    step: Step,
    userId: number
  ): Observable<RoadmapVisualResponse | null> {
    if (step.completionType === 'step') {
      return this.roadmapApi
        .completeRoadmapStep(step.nodeId!, userId)
        .pipe(map(() => null as RoadmapVisualResponse | null));
    }

    return this.completeNodeWithFallback(step, userId);
  }

  private completeNodeWithFallback(
    step: Step,
    userId: number
  ): Observable<RoadmapVisualResponse | null> {
    const nodeId = step.nodeId!;

    return this.roadmapApi.completeNode(nodeId, userId).pipe(
      catchError((completeErr: HttpErrorResponse) => {
        const errorMessage = this.extractHttpErrorMessage(completeErr).toLowerCase();

        // Backend explicitly reports locked/unavailable nodes with 400.
        // In this case retries/fallbacks are invalid and only generate noisy errors.
        if (
          completeErr.status === 400 &&
          (errorMessage.includes('not available for completion') ||
            errorMessage.includes('cannot be started'))
        ) {
          return throwError(() => completeErr);
        }

        if (completeErr.status === 400 || completeErr.status === 409) {
          return this.roadmapApi.startNode(nodeId, userId).pipe(
            switchMap(() => this.roadmapApi.completeNode(nodeId, userId)),
            catchError(() =>
              this.fallbackToClassicStepCompletion(nodeId, userId, completeErr)
            )
          );
        }

        if (completeErr.status === 404) {
          return this.fallbackToClassicStepCompletion(nodeId, userId, completeErr);
        }

        return throwError(() => completeErr);
      })
    );
  }

  private fallbackToClassicStepCompletion(
    stepId: number,
    userId: number,
    originalError: HttpErrorResponse
  ): Observable<RoadmapVisualResponse | null> {
    return this.roadmapApi.completeRoadmapStep(stepId, userId).pipe(
      map(() => null as RoadmapVisualResponse | null),
      catchError(() => throwError(() => originalError))
    );
  }

  private loadRoadmap(preferredRoadmapId?: number): void {
    const resolvedUserId = this.currentUserId() ?? resolveRoadmapUserId();
    if (!resolvedUserId) {
      this.errorMessage.set('No authenticated user found. Please sign in again.');
      return;
    }

    this.currentUserId.set(resolvedUserId);
    this.loadUserProjectSubmissions(resolvedUserId);
    this.isLoading.set(true);
    this.errorMessage.set(null);

    const persistedRoadmapId = this.readSelectedRoadmapId(resolvedUserId);
    const candidateRoadmapId =
      preferredRoadmapId ?? this.selectedRoadmapId() ?? persistedRoadmapId;

    this.roadmapApi
      .getUserRoadmaps(resolvedUserId)
      .pipe(
        switchMap((roadmaps) => {
          const sortedRoadmaps = this.sortRoadmapsForHub(roadmaps ?? []);
          this.roadmapCatalog.set(sortedRoadmaps);

          if (sortedRoadmaps.length === 0) {
            this.activeRoadmap.set(null);
            this.selectedRoadmapId.set(null);
            this.stepsState.set([]);
            this.syncQuizGateState([]);
            return of({
              roadmap: null as RoadmapResponse | null,
              graph: null as RoadmapVisualResponse | null,
            });
          }

          const selectedRoadmap = this.resolveRoadmapSelection(
            sortedRoadmaps,
            candidateRoadmapId
          );

          this.activeRoadmap.set(selectedRoadmap);
          this.selectedRoadmapId.set(selectedRoadmap.id);
          this.persistSelectedRoadmapId(resolvedUserId, selectedRoadmap.id);

          return this.roadmapApi.getRoadmapGraph(selectedRoadmap.id).pipe(
            map((graph) => ({ roadmap: selectedRoadmap, graph })),
            catchError(() =>
              of({ roadmap: selectedRoadmap, graph: null as RoadmapVisualResponse | null })
            )
          );
        }),
        finalize(() => this.isLoading.set(false))
      )
      .subscribe({
        next: ({ roadmap, graph }) => {
          if (!roadmap) {
            this.errorMessage.set('No roadmap found for this user yet.');
            return;
          }

          const visualSteps =
            graph && (graph.nodes?.length ?? 0) > 0
              ? this.mapVisualRoadmapToSteps(graph)
              : [];

          if (visualSteps.length > 0) {
            this.stepsState.set(visualSteps);
            this.syncQuizGateState(visualSteps);
            this.expandInProgressStep();
            return;
          }

          const fallback = this.mapCrudRoadmapToSteps(roadmap);
          this.stepsState.set(fallback);
          this.syncQuizGateState(fallback);
          this.expandInProgressStep();

          if (fallback.length === 0) {
            this.errorMessage.set('Selected roadmap has no steps to display yet.');
          }
        },
        error: () => {
          this.roadmapCatalog.set([]);
          this.stepsState.set([]);
          this.syncQuizGateState([]);
          this.errorMessage.set('Could not load roadmap data from backend.');
        },
      });
  }

  private loadStepResources(step: Step): void {
    const nodeId = step.nodeId;
    if (step.resourcesLoaded || step.resourcesLoading || !nodeId) {
      return;
    }

    this.patchStep(step.number, { resourcesLoading: true });

    const linkedResources$ = this.roadmapApi
      .getStepResourcesByStep(nodeId)
      .pipe(
        switchMap((existing) => {
          if (existing.length > 0) {
            return of(existing);
          }

          return this.roadmapApi.syncStepResources(nodeId).pipe(
            catchError(() => of(void 0)),
            switchMap(() => this.roadmapApi.getStepResourcesByStep(nodeId)),
            catchError(() => of([] as StepResourceDto[]))
          );
        }),
        catchError(() => of([] as StepResourceDto[]))
      );

    const aiSuggestedResources$ = this.roadmapApi
      .getStepResources(step.title)
      .pipe(catchError(() => of([] as StepResourceDto[])));

    forkJoin({
      linked: linkedResources$,
      aiSuggested: aiSuggestedResources$,
    })
      .pipe(
        finalize(() =>
          this.patchStep(step.number, {
            resourcesLoading: false,
            resourcesLoaded: true,
          })
        )
      )
      .subscribe(({ linked, aiSuggested }) => {
        this.patchStep(step.number, {
          resources: this.toResourceCards(linked, aiSuggested, step.title),
        });
      });
  }

  private patchStep(stepNumber: number, patch: Partial<Step>): void {
    this.stepsState.update((steps) =>
      steps.map((step) =>
        step.number === stepNumber
          ? {
              ...step,
              ...patch,
            }
          : step
      )
    );
  }

  private mapVisualRoadmapToSteps(response: RoadmapVisualResponse): Step[] {
    return (response.nodes ?? [])
      .slice()
      .sort((a, b) => a.stepOrder - b.stepOrder)
      .map((node, index) => ({
        number: node.stepOrder || index + 1,
        title: node.title,
        description: node.objective || node.description || 'Complete this roadmap step.',
        status: this.mapStatus(node.status),
        backendStatus: this.normalizeBackendStatus(node.status),
        estimatedTime: this.toEstimatedTime(node.estimatedDays),
        resources: [],
        resourcesLoaded: false,
        resourcesLoading: false,
        challenges: [],
        challengesLoading: false,
        projectLab: null,
        projectLabLoading: false,
        projectLabHistory: [],
        projectLabHistoryLoading: false,
        projectSolutionDraft: '',
        projectValidation: null,
        projectValidationLoading: false,
        tutorResponse: null,
        tutorLoading: false,
        course: null,
        courseLoading: false,
        courseHistory: [],
        courseHistoryLoading: false,
        nodeId: node.id,
        completionType: 'node',
      }));
  }

  private mapCrudRoadmapToSteps(roadmap: RoadmapResponse | null): Step[] {
    if (!roadmap) {
      return [];
    }

    return (roadmap.steps ?? [])
      .slice()
      .sort((a, b) => (a.stepOrder || 0) - (b.stepOrder || 0))
      .map((step: StepResponse, index) => ({
        number: step.stepOrder || index + 1,
        title: step.title,
        description: step.objective || 'Complete this roadmap step.',
        status: this.mapStatus(step.status),
        backendStatus: this.normalizeBackendStatus(step.status),
        estimatedTime: this.toEstimatedTime(step.estimatedDays),
        resources: [],
        resourcesLoaded: false,
        resourcesLoading: false,
        challenges: [],
        challengesLoading: false,
        projectLab: null,
        projectLabLoading: false,
        projectLabHistory: [],
        projectLabHistoryLoading: false,
        projectSolutionDraft: '',
        projectValidation: null,
        projectValidationLoading: false,
        tutorResponse: null,
        tutorLoading: false,
        course: null,
        courseLoading: false,
        courseHistory: [],
        courseHistoryLoading: false,
        nodeId: step.id,
        completionType: 'step',
      }));
  }

  hasQuizPassed(stepNumber: number): boolean {
    return this.quizPassedState()[this.toStepKey(stepNumber)] === true;
  }

  getQuizScore(stepNumber: number): number | null {
    const score = this.quizScoresState()[this.toStepKey(stepNumber)];
    return typeof score === 'number' ? score : null;
  }

  startNodeQuiz(step: Step, force = false): void {
    if (this.quizSession() && !force) {
      return;
    }

    if (this.isStepLocked(step)) {
      this.errorMessage.set('This node is locked. Complete required previous nodes first.');
      return;
    }

    const userId = this.currentUserId();
    if (!step.nodeId || !userId) {
      const fallbackQuestions = this.buildNodeQuiz(step).slice(0, this.quizQuestionCount);
      this.openQuizSession(step, fallbackQuestions, 'local');
      return;
    }

    this.isLoading.set(true);
    this.roadmapApi
      .getNodeQuiz(step.nodeId, userId, this.quizQuestionCount)
      .pipe(
        map((payload) => this.mapBackendQuizQuestions(payload)),
        catchError(() => of(this.buildNodeQuiz(step).slice(0, this.quizQuestionCount))),
        finalize(() => this.isLoading.set(false))
      )
      .subscribe((questions) => {
        const source: 'ai' | 'local' =
          questions.length > 0 && questions[0].id.startsWith('ai-') ? 'ai' : 'local';

        this.openQuizSession(
          step,
          questions.length > 0 ? questions : this.buildNodeQuiz(step).slice(0, this.quizQuestionCount),
          source
        );
      });
  }

  selectQuizAnswer(questionId: string, optionIndex: number): void {
    const session = this.quizSession();
    if (!session || session.submitted) {
      return;
    }

    const currentQuestion = session.questions[session.activeQuestionIndex];
    const shouldAdvance =
      currentQuestion?.id === questionId &&
      session.activeQuestionIndex < session.questions.length - 1;

    this.quizSession.set({
      ...session,
      activeQuestionIndex: shouldAdvance
        ? session.activeQuestionIndex + 1
        : session.activeQuestionIndex,
      selectedAnswers: {
        ...session.selectedAnswers,
        [questionId]: optionIndex,
      },
      feedback: null,
    });
  }

  setQuizQuestionIndex(index: number): void {
    const session = this.quizSession();
    if (!session || session.submitted) {
      return;
    }

    const nextIndex = Math.max(0, Math.min(session.questions.length - 1, index));
    this.quizSession.set({
      ...session,
      activeQuestionIndex: nextIndex,
      feedback: null,
    });
  }

  nextQuizQuestion(): void {
    const session = this.quizSession();
    if (!session || session.submitted) {
      return;
    }

    this.setQuizQuestionIndex(session.activeQuestionIndex + 1);
  }

  previousQuizQuestion(): void {
    const session = this.quizSession();
    if (!session || session.submitted) {
      return;
    }

    this.setQuizQuestionIndex(session.activeQuestionIndex - 1);
  }

  isQuizQuestionAnswered(index: number): boolean {
    const session = this.quizSession();
    if (!session) {
      return false;
    }

    const question = session.questions[index];
    if (!question) {
      return false;
    }

    return session.selectedAnswers[question.id] != null;
  }

  submitNodeQuiz(): void {
    const session = this.quizSession();
    if (!session || session.submitted) {
      return;
    }

    const hasUnanswered = session.questions.some(
      (question) => session.selectedAnswers[question.id] == null
    );

    if (hasUnanswered) {
      this.quizSession.set({
        ...session,
        feedback: 'Answer all questions before submitting this quiz.',
      });
      return;
    }

    let correctCount = 0;
    session.questions.forEach((question) => {
      if (session.selectedAnswers[question.id] === question.correctIndex) {
        correctCount += 1;
      }
    });

    const scorePercent = Math.round((correctCount / session.questions.length) * 100);
    const passed = scorePercent >= session.passThreshold;

    if (passed) {
      this.setQuizResult(session.stepNumber, scorePercent);
    } else {
      this.registerFailedQuizAttempt(session.stepNumber, session.questions);
    }

    this.quizSession.set({
      ...session,
      submitted: true,
      scorePercent,
      passed,
      badgeLabel: passed ? this.computeQuizBadge(scorePercent) : null,
      feedback: passed
        ? `Passed with ${scorePercent}%. You can now mark this node complete.`
        : `Score ${scorePercent}%. Minimum required is ${session.passThreshold}%. Retake to unlock completion.`,
    });
  }

  retakeNodeQuiz(): void {
    const session = this.quizSession();
    if (!session) {
      return;
    }

    const step = this.stepsState().find((item) => item.number === session.stepNumber);
    if (!step) {
      return;
    }

    this.startNodeQuiz(step, true);
  }

  closeQuizSession(): void {
    const session = this.quizSession();
    if (!session || !session.submitted) {
      return;
    }

    this.quizSession.set(null);
    this.unlockExamMode();
  }

  quizChoiceLabel(index: number): string {
    return String.fromCharCode(65 + index);
  }

  getTutorPrompts(step: Step): string[] {
    return [
      `Explain ${step.title} in beginner terms with a practical example.`,
      `What are the top mistakes in ${step.title} and how can I avoid them?`,
      `Give me a 3-day practice plan to become confident in ${step.title}.`,
    ];
  }

  triggerTutorPrompt(step: Step, prompt: string): void {
    const userId = this.currentUserId();
    if (!userId || !step.nodeId) {
      this.errorMessage.set('AI tutor is unavailable for this node right now.');
      return;
    }

    const normalizedPrompt = (prompt || '').trim();
    if (!normalizedPrompt) {
      this.errorMessage.set('Please enter a prompt for the AI tutor.');
      return;
    }

    this.patchStep(step.number, {
      tutorLoading: true,
    });

    this.roadmapApi
      .askNodeTutor(step.nodeId, userId, {
        prompt: normalizedPrompt,
      })
      .pipe(
        finalize(() => {
          this.patchStep(step.number, { tutorLoading: false });
        })
      )
      .subscribe({
        next: (response) => {
          this.patchStep(step.number, {
            tutorResponse: response,
          });
        },
        error: () => {
          this.patchStep(step.number, {
            tutorResponse: this.buildLocalTutorResponse(step, normalizedPrompt),
          });
        },
      });
  }

  submitCustomTutorPrompt(step: Step): void {
    const prompt = this.tutorPromptDraft().trim();
    if (!prompt) {
      this.errorMessage.set('Write a tutor question first.');
      return;
    }

    this.triggerTutorPrompt(step, prompt);
  }

  generateCodingChallenges(step: Step): void {
    const roadmapId = this.activeRoadmap()?.id;
    if (!roadmapId) {
      this.errorMessage.set('Roadmap context is unavailable. Please refresh and try again.');
      return;
    }

    this.patchStep(step.number, { challengesLoading: true });

    this.roadmapApi
      .generateProjectSuggestionsByRoadmapStep(
        roadmapId,
        step.number,
        step.title,
        this.resolveChallengeLevel(step)
      )
      .pipe(
        switchMap((generated) =>
          this.roadmapApi.getProjectSuggestionsByRoadmapStep(roadmapId, step.number).pipe(
            map((history) => (history.length > 0 ? history : generated)),
            catchError(() => of(generated))
          )
        ),
        finalize(() => {
          this.patchStep(step.number, { challengesLoading: false });
        })
      )
      .subscribe({
        next: (suggestions) => {
          this.patchStep(step.number, {
            challenges: this.toChallengeCards(suggestions, step.challenges),
          });
        },
        error: () => {
          this.errorMessage.set('Could not generate coding challenges right now. Please try again.');
        },
      });
  }

  loadChallengeHistory(step: Step): void {
    const roadmapId = this.activeRoadmap()?.id;
    if (!roadmapId || step.challengesLoading) {
      return;
    }

    this.patchStep(step.number, { challengesLoading: true });

    this.roadmapApi
      .getProjectSuggestionsByRoadmapStep(roadmapId, step.number)
      .pipe(
        finalize(() => {
          this.patchStep(step.number, { challengesLoading: false });
        })
      )
      .subscribe({
        next: (suggestions) => {
          if (!suggestions || suggestions.length === 0) {
            return;
          }

          this.patchStep(step.number, {
            challenges: this.toChallengeCards(suggestions, step.challenges),
          });
        },
        error: () => {},
      });
  }

  setChallengeRepoUrl(step: Step, challenge: ChallengeCard, value: string): void {
    this.patchChallenge(step.number, challenge.id, { repoUrlDraft: value });
  }

  canSubmitChallenge(challenge: ChallengeCard): boolean {
    if (!challenge.submission) {
      return true;
    }
    return challenge.submission.retryCount < this.maxSubmissionRetries;
  }

  formatSubmissionStatus(status: string | undefined): string {
    const normalized = (status || 'PENDING_REVIEW').toLowerCase().replace(/_/g, ' ');
    return normalized.charAt(0).toUpperCase() + normalized.slice(1);
  }

  hasChallengeScores(challenge: ChallengeCard): boolean {
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

  submitChallengeProject(step: Step, challenge: ChallengeCard): void {
    const userId = this.currentUserId();
    if (!userId) {
      this.errorMessage.set('No authenticated user found. Please sign in again.');
      return;
    }

    if (!this.canSubmitChallenge(challenge)) {
      this.errorMessage.set('Retry limit reached for this challenge submission.');
      return;
    }

    const repoUrl = (challenge.repoUrlDraft || challenge.submission?.repoUrl || '').trim();
    if (!repoUrl) {
      this.errorMessage.set('Please provide a repository URL before submitting this project.');
      return;
    }

    const request$ = challenge.submission
      ? this.roadmapApi.retryProjectSubmission(challenge.submission.id, { repoUrl })
      : this.roadmapApi.submitProject({
          userId,
          projectSuggestionId: challenge.id,
          repoUrl,
        });

    this.patchChallenge(step.number, challenge.id, { submitting: true });

    request$
      .pipe(
        finalize(() => {
          this.patchChallenge(step.number, challenge.id, { submitting: false });
        })
      )
      .subscribe({
        next: (submission) => {
          this.userSubmissionsBySuggestion.update((state) => ({
            ...state,
            [challenge.id]: submission,
          }));

          const updatedChallenge: ChallengeCard = {
            ...challenge,
            submission,
            repoUrlDraft: submission.repoUrl || repoUrl,
            reviewText: submission.aiFeedback || challenge.reviewText,
          };

          this.patchChallenge(step.number, challenge.id, {
            submission,
            repoUrlDraft: submission.repoUrl || repoUrl,
            reviewText: submission.aiFeedback || challenge.reviewText,
          });

          if (!(submission.aiFeedback || '').trim()) {
            this.loadChallengeReview(step, updatedChallenge);
          }
        },
        error: (err: HttpErrorResponse) => {
          const message =
            (typeof err.error === 'string' ? err.error : err.error?.message) ||
            err.message ||
            'Could not submit this challenge right now.';
          this.errorMessage.set(message);
        },
      });
  }

  loadChallengeReview(step: Step, challenge: ChallengeCard): void {
    const submission = challenge.submission;
    if (!submission?.id) {
      this.errorMessage.set('Submit a project first before requesting AI review.');
      return;
    }

    this.patchChallenge(step.number, challenge.id, { reviewLoading: true });

    this.roadmapApi
      .getProjectSubmissionReview(submission.id)
      .pipe(
        finalize(() => {
          this.patchChallenge(step.number, challenge.id, { reviewLoading: false });
        })
      )
      .subscribe({
        next: (payload) => {
          const review = (payload.review || '').trim();
          const updatedSubmission: ProjectSubmissionDto = {
            ...submission,
            status: payload.status || submission.status,
            score: payload.score ?? submission.score,
            readmeScore: payload.readmeScore ?? submission.readmeScore,
            structureScore: payload.structureScore ?? submission.structureScore,
            testScore: payload.testScore ?? submission.testScore,
            ciScore: payload.ciScore ?? submission.ciScore,
            recommendations: payload.recommendations ?? submission.recommendations,
            reviewedAt: payload.reviewedAt || submission.reviewedAt,
            aiFeedback: review || submission.aiFeedback,
          };

          this.userSubmissionsBySuggestion.update((state) => ({
            ...state,
            [challenge.id]: updatedSubmission,
          }));

          this.patchChallenge(step.number, challenge.id, {
            submission: updatedSubmission,
            reviewText: review || 'No AI review available yet for this submission.',
          });
        },
        error: (err: HttpErrorResponse) => {
          const message =
            (typeof err.error === 'string' ? err.error : err.error?.message) ||
            err.message ||
            'Could not load AI review right now.';
          this.errorMessage.set(message);
        },
      });
  }

  generateNodeProjectLab(step: Step): void {
    const userId = this.currentUserId();
    if (!userId || !step.nodeId) {
      this.errorMessage.set('Node project lab is unavailable for this step right now.');
      return;
    }

    this.patchStep(step.number, { projectLabLoading: true });

    this.roadmapApi
      .getNodeProjectLab(step.nodeId, userId)
      .pipe(
        finalize(() => {
          this.patchStep(step.number, { projectLabLoading: false });
        })
      )
      .subscribe({
        next: (projectLab) => {
          const history = [projectLab, ...(step.projectLabHistory || [])].filter(
            (entry, index, all) =>
              all.findIndex((candidate) => this.sameProjectLabHistoryEntry(candidate, entry)) === index
          );

          this.patchStep(step.number, {
            projectLab,
            projectLabHistory: history,
            projectSolutionDraft: projectLab.starterCode || '',
            projectValidation: null,
          });
        },
        error: (err: HttpErrorResponse) => {
          const message =
            (typeof err.error === 'string' ? err.error : err.error?.message) ||
            err.message ||
            'Could not generate a node project lab right now.';
          this.errorMessage.set(message);
        },
      });
  }

  loadNodeProjectLabHistory(step: Step): void {
    const userId = this.currentUserId();
    if (!userId || !step.nodeId || step.projectLabHistoryLoading) {
      return;
    }

    this.patchStep(step.number, { projectLabHistoryLoading: true });

    this.roadmapApi
      .getNodeProjectLabHistory(step.nodeId, userId)
      .pipe(
        finalize(() => {
          this.patchStep(step.number, { projectLabHistoryLoading: false });
        })
      )
      .subscribe({
        next: (history) => {
          if (!history || history.length === 0) {
            return;
          }

          const activeProject = step.projectLab || history[0];
          this.patchStep(step.number, {
            projectLab: activeProject,
            projectLabHistory: history,
            projectSolutionDraft: step.projectSolutionDraft || activeProject.starterCode || '',
          });
        },
        error: () => {},
      });
  }

  restoreProjectLabFromHistory(step: Step, projectLab: NodeProjectLabDto): void {
    this.patchStep(step.number, {
      projectLab,
      projectSolutionDraft: projectLab.starterCode || '',
      projectValidation: null,
    });
  }

  setProjectSolutionDraft(step: Step, code: string): void {
    this.patchStep(step.number, {
      projectSolutionDraft: code,
    });
  }

  validateNodeProjectSolution(step: Step): void {
    const userId = this.currentUserId();
    if (!userId || !step.nodeId) {
      this.errorMessage.set('Node project validation is unavailable for this step right now.');
      return;
    }

    if (!step.projectLab) {
      this.errorMessage.set('Generate the node project lab first.');
      return;
    }

    const code = (step.projectSolutionDraft || '').trim();
    if (!code) {
      this.errorMessage.set('Please write or paste your solution code before validation.');
      return;
    }

    this.patchStep(step.number, { projectValidationLoading: true });

    this.roadmapApi
      .validateNodeProject(step.nodeId, userId, {
        projectTitle: step.projectLab.projectTitle,
        language: step.projectLab.language,
        acceptanceCriteria: step.projectLab.acceptanceCriteria,
        code,
      })
      .pipe(
        finalize(() => {
          this.patchStep(step.number, { projectValidationLoading: false });
        })
      )
      .subscribe({
        next: (validation) => {
          this.patchStep(step.number, { projectValidation: validation });
        },
        error: (err: HttpErrorResponse) => {
          const message =
            (typeof err.error === 'string' ? err.error : err.error?.message) ||
            err.message ||
            'Could not validate your node project right now.';
          this.errorMessage.set(message);
        },
      });
  }

  loadNodeCourse(step: Step, refresh = false): void {
    const userId = this.currentUserId();
    if (!userId || !step.nodeId) {
      this.errorMessage.set('Node course is unavailable for this step right now.');
      return;
    }

    this.patchStep(step.number, { courseLoading: true });

    this.roadmapApi
      .getNodeCourse(step.nodeId, userId, refresh)
      .pipe(
        finalize(() => {
          this.patchStep(step.number, { courseLoading: false });
        })
      )
      .subscribe({
        next: (course) => {
          const normalizedCourse = this.normalizeCoursePayload(course);

          const history = [normalizedCourse, ...(step.courseHistory || [])].filter(
            (entry, index, all) =>
              all.findIndex(
                (candidate) =>
                  (candidate.historyId && entry.historyId && candidate.historyId === entry.historyId) ||
                  (!!candidate.generatedAt && candidate.generatedAt === entry.generatedAt)
              ) === index
          );

          this.patchStep(step.number, {
            course: normalizedCourse,
            courseHistory: history,
          });
        },
        error: (err: HttpErrorResponse) => {
          const message =
            (typeof err.error === 'string' ? err.error : err.error?.message) ||
            err.message ||
            'Could not load the node course right now.';
          this.errorMessage.set(message);
        },
      });
  }

  loadNodeCourseHistory(step: Step): void {
    const userId = this.currentUserId();
    if (!userId || !step.nodeId || step.courseHistoryLoading) {
      return;
    }

    this.patchStep(step.number, { courseHistoryLoading: true });

    this.roadmapApi
      .getNodeCourseHistory(step.nodeId, userId)
      .pipe(
        finalize(() => {
          this.patchStep(step.number, { courseHistoryLoading: false });
        })
      )
      .subscribe({
        next: (history) => {
          if (!history || history.length === 0) {
            return;
          }

          const normalizedHistory = history.map((entry) => this.normalizeCoursePayload(entry));

          this.patchStep(step.number, {
            course: step.course || normalizedHistory[0],
            courseHistory: normalizedHistory,
          });
        },
        error: () => {},
      });
  }

  restoreNodeCourseFromHistory(step: Step, course: NodeCourseContentDto): void {
    this.patchStep(step.number, {
      course,
    });
  }

  private openQuizSession(
    step: Step,
    questions: NodeQuizQuestion[],
    source: 'ai' | 'local'
  ): void {
    const selectedAnswers: Record<string, number | null> = {};
    questions.forEach((question) => {
      selectedAnswers[question.id] = null;
    });

    this.quizSession.set({
      stepNumber: step.number,
      stepTitle: step.title,
      questions,
      source,
      activeQuestionIndex: 0,
      selectedAnswers,
      passThreshold: this.quizPassThreshold,
      submitted: false,
      scorePercent: null,
      passed: false,
      badgeLabel: null,
      feedback: null,
    });

    this.lockExamMode();
  }

  private mapBackendQuizQuestions(payload: NodeQuizResponseDto): NodeQuizQuestion[] {
    if (!payload?.questions || payload.questions.length === 0) {
      return [];
    }

    return payload.questions
      .map((question, index) => {
        const options = (question.options || [])
          .map((option) => (option || '').trim())
          .filter((option) => option.length > 0)
          .slice(0, 4);

        if (options.length < 2) {
          return null;
        }

        const correctIndex = Number.isInteger(question.correctIndex)
          ? Math.max(0, Math.min(options.length - 1, question.correctIndex))
          : 0;

        const id = question.id?.trim() || `ai-${Date.now()}-${index + 1}`;
        const prompt = question.prompt?.trim() || `Question ${index + 1}`;

        return {
          id: id.startsWith('ai-') ? id : `ai-${id}`,
          prompt,
          options,
          correctIndex,
        } as NodeQuizQuestion;
      })
      .filter((question): question is NodeQuizQuestion => question !== null)
      .slice(0, this.quizQuestionCount);
  }

  private computeQuizBadge(scorePercent: number): string {
    if (scorePercent >= 95) {
      return 'Elite Master Badge';
    }
    if (scorePercent >= 85) {
      return 'Proficiency Badge';
    }
    return 'Qualified Badge';
  }

  private toChallengeCards(
    suggestions: ProjectSuggestionDto[],
    existingChallenges: ChallengeCard[] = []
  ): ChallengeCard[] {
    const existingById = new Map(existingChallenges.map((challenge) => [challenge.id, challenge]));
    const submissionsBySuggestion = this.userSubmissionsBySuggestion();

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
      const existing = existingById.get(suggestion.id);
      const submission = submissionsBySuggestion[suggestion.id] || existing?.submission || null;
      const repoUrlDraft = existing?.repoUrlDraft || submission?.repoUrl || '';

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
        submitting: existing?.submitting ?? false,
        reviewLoading: existing?.reviewLoading ?? false,
        reviewText: existing?.reviewText || submission?.aiFeedback || null,
      };
    });
  }

  private loadUserProjectSubmissions(userId: number): void {
    this.roadmapApi
      .getUserProjectSubmissions(userId)
      .pipe(catchError(() => of([] as ProjectSubmissionDto[])))
      .subscribe((submissions) => {
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

        this.userSubmissionsBySuggestion.set(bySuggestion);

        this.stepsState.update((steps) =>
          steps.map((step) => ({
            ...step,
            challenges: step.challenges.map((challenge) => {
              const submission = bySuggestion[challenge.id] || challenge.submission;
              return {
                ...challenge,
                submission,
                repoUrlDraft: challenge.repoUrlDraft || submission?.repoUrl || '',
                reviewText: challenge.reviewText || submission?.aiFeedback || null,
              };
            }),
          }))
        );
      });
  }

  private patchChallenge(
    stepNumber: number,
    challengeId: number,
    patch: Partial<ChallengeCard>
  ): void {
    this.patchStep(stepNumber, {
      challenges: (this.stepsState().find((step) => step.number === stepNumber)?.challenges || []).map(
        (challenge) =>
          challenge.id === challengeId
            ? {
                ...challenge,
                ...patch,
              }
            : challenge
      ),
    });
  }

  private resolveChallengeLevel(step: Step): string {
    const roadmapDifficulty = (this.activeRoadmap()?.difficulty || '').toUpperCase();
    if (roadmapDifficulty.includes('BEGINNER')) {
      return 'BEGINNER';
    }
    if (roadmapDifficulty.includes('ADVANCED')) {
      return 'ADVANCED';
    }

    const backendStatus = this.normalizeBackendStatus(step.backendStatus);
    if (backendStatus === 'LOCKED' || backendStatus === 'AVAILABLE') {
      return 'BEGINNER';
    }

    return 'INTERMEDIATE';
  }

  private toResourceCards(
    linkedResources: StepResourceDto[],
    aiSuggestedResources: StepResourceDto[],
    topic: string
  ): ResourceCard[] {
    const cards: ResourceCard[] = [];
    const seen = new Set<string>();

    const pushCard = (resource: StepResourceDto, origin: ResourceCard['origin']): void => {
      const url = (resource.url || '').trim();
      if (!url) {
        return;
      }

      const dedupeKey = this.resourceDedupeKey(url);
      if (seen.has(dedupeKey)) {
        return;
      }

      seen.add(dedupeKey);
      cards.push({
        type: this.normalizeResourceType(resource.type),
        title: resource.title || this.fallbackResourceTitle(origin),
        source: this.toProviderLabel(resource.provider, origin),
        url,
        isFree: this.isFreeResource(resource),
        origin,
      });
    };

    linkedResources.forEach((resource) => pushCard(resource, 'roadmap'));
    aiSuggestedResources.forEach((resource) => pushCard(resource, 'ai'));

    if (cards.length > 0) {
      return cards;
    }

    return this.buildDiscoveryFallback(topic);
  }

  private syncQuizGateState(steps: Step[]): void {
    const persisted = this.readQuizState();
    const validKeys = new Set(steps.map((step) => this.toStepKey(step.number)));

    const nextPassed: Record<string, boolean> = {};
    const nextScores: Record<string, number> = {};
    const nextSeenQuestionIds: Record<string, string[]> = {};
    const nextAttemptByStep: Record<string, number> = {};

    Object.entries(persisted.passedByStep).forEach(([key, value]) => {
      if (validKeys.has(key) && value) {
        nextPassed[key] = true;
      }
    });

    Object.entries(persisted.scoreByStep).forEach(([key, value]) => {
      if (!validKeys.has(key)) {
        return;
      }
      const numeric = Number(value);
      if (!Number.isFinite(numeric)) {
        return;
      }
      nextScores[key] = Math.max(0, Math.min(100, Math.round(numeric)));
    });

    Object.entries(persisted.seenQuestionIdsByStep).forEach(([key, value]) => {
      if (!validKeys.has(key) || !Array.isArray(value)) {
        return;
      }

      const normalized = Array.from(
        new Set(
          value
            .map((item) => (typeof item === 'string' ? item.trim() : ''))
            .filter((item) => item.length > 0)
        )
      ).slice(-300);

      if (normalized.length > 0) {
        nextSeenQuestionIds[key] = normalized;
      }
    });

    Object.entries(persisted.attemptByStep).forEach(([key, value]) => {
      if (!validKeys.has(key)) {
        return;
      }

      const numeric = Number(value);
      if (!Number.isFinite(numeric) || numeric < 0) {
        return;
      }

      nextAttemptByStep[key] = Math.floor(numeric);
    });

    steps.forEach((step) => {
      if (step.status === 'done') {
        const key = this.toStepKey(step.number);
        nextPassed[key] = true;
        if (nextScores[key] == null) {
          nextScores[key] = 100;
        }
      }
    });

    this.quizPassedState.set(nextPassed);
    this.quizScoresState.set(nextScores);
    this.quizSeenQuestionIdsState.set(nextSeenQuestionIds);
    this.quizAttemptCountState.set(nextAttemptByStep);
    this.persistQuizState();
  }

  private setQuizResult(stepNumber: number, scorePercent: number): void {
    const key = this.toStepKey(stepNumber);

    this.quizPassedState.update((state) => ({
      ...state,
      [key]: true,
    }));

    this.quizScoresState.update((state) => ({
      ...state,
      [key]: Math.max(0, Math.min(100, Math.round(scorePercent))),
    }));

    this.persistQuizState();
  }

  private registerFailedQuizAttempt(
    stepNumber: number,
    questions: NodeQuizQuestion[]
  ): void {
    const key = this.toStepKey(stepNumber);
    const failedQuestionIds = questions.map((question) => question.id);

    this.quizSeenQuestionIdsState.update((state) => {
      const existing = state[key] ?? [];
      const merged = Array.from(new Set([...existing, ...failedQuestionIds])).slice(-300);
      return {
        ...state,
        [key]: merged,
      };
    });

    this.quizAttemptCountState.update((state) => ({
      ...state,
      [key]: (state[key] ?? 0) + 1,
    }));

    this.persistQuizState();
  }

  private buildNodeQuiz(step: Step): NodeQuizQuestion[] {
    const stepKey = this.toStepKey(step.number);
    const seenQuestionIds = new Set(this.quizSeenQuestionIdsState()[stepKey] ?? []);
    const attemptCount = this.quizAttemptCountState()[stepKey] ?? 0;

    const topicTemplates = this.buildTopicQuestionTemplates(step);
    let availableTemplates = topicTemplates.filter((template) => !seenQuestionIds.has(template.id));

    if (availableTemplates.length < this.quizQuestionCount) {
      const dynamicTemplates = this.buildDynamicQuestionTemplates(step, seenQuestionIds);
      availableTemplates = [...availableTemplates, ...dynamicTemplates];
    }

    if (availableTemplates.length === 0) {
      availableTemplates = topicTemplates;
    }

    const selectedTemplates = this.seededShuffle(
      availableTemplates,
      step.number * 131 + attemptCount * 197 + this.quizQuestionCount
    ).slice(0, this.quizQuestionCount);

    return selectedTemplates.map((template, index) =>
      this.createQuizQuestion(
        template.id,
        template.prompt,
        template.correct,
        template.distractors,
        step.number * 37 + attemptCount * 53 + index * 11
      )
    );
  }

  private buildTopicQuestionTemplates(step: Step): NodeQuizTemplate[] {
    const topicLabel = this.resolveQuizTopicLabel(step);
    const topicKey = this.resolveQuizTopicKey(step);
    const keywords = this.extractKeywords(`${step.title} ${step.description}`);
    const keywordA = keywords[0] || topicLabel;
    const keywordB = keywords[1] || topicLabel;
    const keywordC = keywords[2] || 'implementation';
    const resourceHint =
      step.resources.find((resource) => resource.origin !== 'ai')?.title ||
      `${topicLabel} official documentation`;

    const templates: NodeQuizTemplate[] = [
      {
        id: this.composeQuizTemplateId(step.number, 'core-outcome'),
        prompt: `What best demonstrates practical mastery of ${topicLabel}?`,
        correct: `You can explain and apply ${topicLabel} to solve a real implementation task.`,
        distractors: [
          `You memorize definitions of ${topicLabel} without building anything.`,
          `You skip fundamentals and jump to unrelated advanced topics.`,
          'You only watch short summaries without hands-on practice.',
        ],
      },
      {
        id: this.composeQuizTemplateId(step.number, 'first-step'),
        prompt: `Which starting approach is strongest for learning ${topicLabel}?`,
        correct: `Understand ${keywordA} basics first, then build a small working example.`,
        distractors: [
          'Start with optimization and scaling before understanding fundamentals.',
          'Avoid official references and rely only on random snippets.',
          `Postpone practice on ${topicLabel} until every other node is done.`,
        ],
      },
      {
        id: this.composeQuizTemplateId(step.number, 'resource-usage'),
        prompt: `How should resources be used while studying ${topicLabel}?`,
        correct: `Begin with a trusted source like ${resourceHint}, then verify by practicing.`,
        distractors: [
          'Pick only the shortest resource and skip exercises.',
          'Use only social posts and ignore documentation completely.',
          'Collect many resources in parallel without finishing any.',
        ],
      },
      {
        id: this.composeQuizTemplateId(step.number, 'readiness-signal'),
        prompt: `Which signal shows you are ready to progress beyond this ${topicLabel} node?`,
        correct: `You can complete a focused task using ${keywordB} with minimal guidance.`,
        distractors: [
          `You can repeat ${topicLabel} definitions but cannot apply them.`,
          'You finish content consumption but do not test your understanding.',
          'You avoid implementation and rely on theory alone.',
        ],
      },
      {
        id: this.composeQuizTemplateId(step.number, 'debugging-path'),
        prompt: `When your ${topicLabel} solution fails, what is the best troubleshooting path?`,
        correct: `Reproduce the issue, isolate ${keywordC}, and verify fixes incrementally.`,
        distractors: [
          'Rewrite everything immediately without finding root cause.',
          'Ignore failing behavior and continue to the next node.',
          'Apply random fixes until output changes.',
        ],
      },
      {
        id: this.composeQuizTemplateId(step.number, 'practice-loop'),
        prompt: `What is the most effective practice loop for ${topicLabel}?`,
        correct: `Study a concept, implement it, review mistakes, and iterate with harder tasks.`,
        distractors: [
          'Read once and move on without implementation.',
          'Practice only easy tasks and avoid challenging scenarios.',
          'Repeat the same solved example without variation.',
        ],
      },
      {
        id: this.composeQuizTemplateId(step.number, 'quality-check'),
        prompt: `Which review habit improves long-term retention in ${topicLabel}?`,
        correct: `Regularly explain decisions, test assumptions, and refactor your solutions.`,
        distractors: [
          'Skip review as soon as code compiles once.',
          'Depend on copy-paste patterns without understanding.',
          'Wait until the final exam before revisiting concepts.',
        ],
      },
      {
        id: this.composeQuizTemplateId(step.number, 'scenario-transfer'),
        prompt: `Why is scenario variation important while learning ${topicLabel}?`,
        correct: `It validates that you can transfer ${topicLabel} skills to new contexts.`,
        distractors: [
          'It is unnecessary once a single demo works.',
          'It slows progress and should be skipped.',
          'It only matters for unrelated advanced domains.',
        ],
      },
    ];

    if (topicKey === 'java') {
      templates.push(
        {
          id: this.composeQuizTemplateId(step.number, 'java-jvm-role'),
          prompt: 'In Java, what is the primary role of the JVM?',
          correct: 'To execute Java bytecode and manage runtime services like memory and GC.',
          distractors: [
            'To replace the compiler and write Java source code automatically.',
            'To store Java files permanently instead of using a filesystem.',
            'To run only frontend JavaScript in the browser.',
          ],
        },
        {
          id: this.composeQuizTemplateId(step.number, 'java-jdk-jre'),
          prompt: 'Which statement correctly compares JDK and JRE in Java?',
          correct: 'JDK includes development tools; JRE mainly provides runtime to run Java apps.',
          distractors: [
            'JRE includes compiler tools while JDK only runs applications.',
            'They are identical packages with different names only.',
            'JDK is only for databases and JRE is only for web browsers.',
          ],
        },
        {
          id: this.composeQuizTemplateId(step.number, 'java-checked-exception'),
          prompt: 'What is true about checked exceptions in Java?',
          correct: 'They are verified at compile time and must be handled or declared.',
          distractors: [
            'They are ignored by the compiler and handled only at runtime.',
            'They can only occur in JavaScript interoperability code.',
            'They automatically terminate the JVM with no handling option.',
          ],
        },
        {
          id: this.composeQuizTemplateId(step.number, 'java-string-immutable'),
          prompt: 'Why is String immutability useful in Java?',
          correct: 'It improves safety and predictability because String values cannot be changed after creation.',
          distractors: [
            'It makes Strings mutable for faster in-place edits.',
            'It prevents Strings from being used as keys in collections.',
            'It disables garbage collection for String objects.',
          ],
        },
        {
          id: this.composeQuizTemplateId(step.number, 'java-equals-hashcode'),
          prompt: 'What is the key rule for equals and hashCode in Java objects?',
          correct: 'Equal objects must return the same hashCode value.',
          distractors: [
            'hashCode must always be unique for every object instance.',
            'equals should compare only memory addresses in all cases.',
            'hashCode is unrelated and should not be overridden with equals.',
          ],
        }
      );
    }

    return templates;
  }

  private buildDynamicQuestionTemplates(
    step: Step,
    seenQuestionIds: Set<string>
  ): NodeQuizTemplate[] {
    const topicLabel = this.resolveQuizTopicLabel(step);
    const keywords = this.extractKeywords(`${step.title} ${step.description}`);
    const anchors = keywords.length > 0 ? keywords : [topicLabel.toLowerCase()];
    const templates: NodeQuizTemplate[] = [];

    for (let index = 0; index < 160; index += 1) {
      const focus = anchors[index % anchors.length] || topicLabel;
      const id = this.composeQuizTemplateId(step.number, `dynamic-${index}`);
      if (seenQuestionIds.has(id)) {
        continue;
      }

      templates.push({
        id,
        prompt: `Scenario ${index + 1}: In ${topicLabel}, which decision best improves reliability around ${focus}?`,
        correct: `Implement ${focus} incrementally, validate with tests, and review results before scaling.`,
        distractors: [
          `Skip validating ${focus} and continue with assumptions.`,
          `Change many ${topicLabel} components at once without checkpoints.`,
          `Ignore failing outputs and move to the next roadmap topic.`,
        ],
      });

      if (templates.length >= this.quizQuestionCount * 8) {
        break;
      }
    }

    return templates;
  }

  private resolveQuizTopicLabel(step: Step): string {
    const title = step.title.trim();
    if (!title) {
      return 'this topic';
    }

    const firstSegment = title.split(/[:|\-]/)[0]?.trim();
    if (firstSegment && firstSegment.length >= 3) {
      return firstSegment;
    }

    return title;
  }

  private resolveQuizTopicKey(step: Step): string {
    const source = `${step.title} ${step.description}`.toLowerCase();

    if (/\bjava\b/.test(source)) return 'java';
    if (/\bdocker\b|\bcontainer\b/.test(source)) return 'docker';
    if (/\bkubernetes\b|\bk8s\b/.test(source)) return 'kubernetes';
    if (/\bci\s*\/\s*cd\b|\bpipeline\b/.test(source)) return 'cicd';
    if (/\btest\b|\btesting\b|\bintegration\b/.test(source)) return 'testing';
    if (/\bgit\b|\bversion control\b/.test(source)) return 'git';
    if (/\bsql\b|\bdatabase\b/.test(source)) return 'database';

    return 'generic';
  }

  private composeQuizTemplateId(stepNumber: number, key: string): string {
    return `step-${stepNumber}:${key}`;
  }

  private createQuizQuestion(
    id: string,
    prompt: string,
    correct: string,
    distractors: string[],
    seed: number
  ): NodeQuizQuestion {
    const uniqueDistractors = Array.from(
      new Set(distractors.map((item) => item.trim()).filter((item) => item.length > 0))
    ).filter((item) => item !== correct);

    const options = [correct, ...uniqueDistractors.slice(0, 3)];
    while (options.length < 4) {
      options.push('Pick the option that best matches the node objective and practical outcome.');
    }

    const shuffled = this.seededShuffle(options, seed);

    return {
      id,
      prompt,
      options: shuffled,
      correctIndex: shuffled.findIndex((option) => option === correct),
    };
  }

  private seededShuffle<T>(values: T[], seed: number): T[] {
    const result = [...values];
    let state = (seed || 1) >>> 0;

    for (let i = result.length - 1; i > 0; i -= 1) {
      state = (1664525 * state + 1013904223) >>> 0;
      const j = state % (i + 1);
      [result[i], result[j]] = [result[j], result[i]];
    }

    return result;
  }

  private extractKeywords(text: string): string[] {
    const stopwords = new Set([
      'the',
      'and',
      'for',
      'with',
      'from',
      'into',
      'this',
      'that',
      'your',
      'you',
      'are',
      'using',
      'build',
      'learn',
      'step',
      'node',
      'roadmap',
    ]);

    return text
      .toLowerCase()
      .replace(/[^a-z0-9\s]/g, ' ')
      .split(/\s+/)
      .filter((token) => token.length >= 4 && !stopwords.has(token))
      .slice(0, 8);
  }

  private readQuizState(): NodeQuizStorageState {
    const key = this.quizStorageKey();
    if (!key) {
      return {
        passedByStep: {},
        scoreByStep: {},
        seenQuestionIdsByStep: {},
        attemptByStep: {},
      };
    }

    try {
      const raw = window.sessionStorage.getItem(key);
      if (!raw) {
        return {
          passedByStep: {},
          scoreByStep: {},
          seenQuestionIdsByStep: {},
          attemptByStep: {},
        };
      }

      const parsed = JSON.parse(raw) as Partial<NodeQuizStorageState>;
      return {
        passedByStep: parsed.passedByStep ?? {},
        scoreByStep: parsed.scoreByStep ?? {},
        seenQuestionIdsByStep: parsed.seenQuestionIdsByStep ?? {},
        attemptByStep: parsed.attemptByStep ?? {},
      };
    } catch {
      return {
        passedByStep: {},
        scoreByStep: {},
        seenQuestionIdsByStep: {},
        attemptByStep: {},
      };
    }
  }

  private persistQuizState(): void {
    const key = this.quizStorageKey();
    if (!key) {
      return;
    }

    const payload: NodeQuizStorageState = {
      passedByStep: this.quizPassedState(),
      scoreByStep: this.quizScoresState(),
      seenQuestionIdsByStep: this.quizSeenQuestionIdsState(),
      attemptByStep: this.quizAttemptCountState(),
    };

    try {
      window.sessionStorage.setItem(key, JSON.stringify(payload));
    } catch {
      // Ignore storage write failures in restricted browsing contexts.
    }
  }

  private quizStorageKey(): string | null {
    const userId = this.currentUserId();
    const roadmapId = this.activeRoadmap()?.id;
    if (!userId || !roadmapId) {
      return null;
    }
    return `smarthire-node-quiz:${userId}:${roadmapId}`;
  }

  private toStepKey(stepNumber: number): string {
    return String(stepNumber);
  }

  private lockExamMode(): void {
    if (this.previousBodyOverflow === null) {
      this.previousBodyOverflow = this.document.body.style.overflow;
    }
    this.document.body.style.overflow = 'hidden';
  }

  private unlockExamMode(): void {
    if (this.previousBodyOverflow === null) {
      return;
    }

    this.document.body.style.overflow = this.previousBodyOverflow;
    this.previousBodyOverflow = null;
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

  private buildLocalTutorResponse(step: Step, prompt: string): NodeTutorPromptResponseDto {
    return {
      nodeId: step.nodeId || 0,
      nodeTitle: step.title,
      prompt,
      answer:
        `Focus on one practical slice of ${step.title} first. Build a tiny example, test it with edge cases, and ` +
        'refactor only after it works end-to-end.',
      keyTakeaways: [
        'Start small and executable.',
        'Validate assumptions early.',
        'Document one lesson learned before moving on.',
      ],
      nextActions: [
        `Build a 20-30 minute mini exercise for ${step.title}.`,
        'Write one test or check for expected output.',
        'List one common mistake and how you will prevent it.',
      ],
      aiGenerated: false,
      respondedAt: new Date().toISOString(),
    };
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

  private fallbackResourceTitle(origin: ResourceCard['origin']): string {
    return origin === 'ai' ? 'AI suggested resource' : 'Roadmap resource';
  }

  private toProviderLabel(
    provider: string | undefined,
    origin: ResourceCard['origin']
  ): string {
    if (provider && provider.trim()) {
      return provider;
    }
    return origin === 'ai' ? 'AI Tutor' : 'Roadmap';
  }

  private resourceDedupeKey(url: string): string {
    return url.trim().toLowerCase().replace(/^https?:\/\//, '');
  }

  private isFreeResource(resource: StepResourceDto): boolean {
    if (resource.isFree != null) {
      return resource.isFree;
    }
    if ((resource.price ?? 0) > 0) {
      return false;
    }
    return true;
  }

  private buildDiscoveryFallback(topic: string): ResourceCard[] {
    const q = encodeURIComponent(topic);
    return [
      {
        type: 'course',
        title: `Find structured courses for ${topic}`,
        source: 'Coursera Search',
        url: `https://www.coursera.org/search?query=${q}`,
        isFree: false,
        origin: 'ai',
      },
      {
        type: 'video',
        title: `${topic} full video tutorials`,
        source: 'YouTube Search',
        url: `https://www.youtube.com/results?search_query=${q}+full+course`,
        isFree: true,
        origin: 'ai',
      },
      {
        type: 'article',
        title: `${topic} docs and practical guides`,
        source: 'Documentation Search',
        url: `https://www.google.com/search?q=${q}+official+documentation+guide`,
        isFree: true,
        origin: 'ai',
      },
    ];
  }

  private normalizeResourceType(type: string | undefined): 'video' | 'article' | 'course' {
    const normalized = (type || '').toUpperCase();
    if (normalized === 'VIDEO') {
      return 'video';
    }
    if (normalized === 'COURSE') {
      return 'course';
    }
    return 'article';
  }

  isStepCompletable(step: Step): boolean {
    const status = this.normalizeBackendStatus(step.backendStatus || step.status);
    return status === 'AVAILABLE' || status === 'IN_PROGRESS';
  }

  isStepLocked(step: Step): boolean {
    const status = this.normalizeBackendStatus(step.backendStatus || step.status);
    return status === 'LOCKED';
  }

  private normalizeBackendStatus(status: string | undefined): string {
    return (status || '').toUpperCase().trim();
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

  private sortRoadmapsForHub(roadmaps: RoadmapResponse[]): RoadmapResponse[] {
    return [...roadmaps].sort((left, right) => {
      const statusDelta = this.roadmapStatusRank(right.status) - this.roadmapStatusRank(left.status);
      if (statusDelta !== 0) {
        return statusDelta;
      }

      const rightDate = this.roadmapRecencyTimestamp(right);
      const leftDate = this.roadmapRecencyTimestamp(left);
      if (rightDate !== leftDate) {
        return rightDate - leftDate;
      }

      return (right.id ?? 0) - (left.id ?? 0);
    });
  }

  private resolveRoadmapSelection(
    roadmaps: RoadmapResponse[],
    preferredRoadmapId: number | null
  ): RoadmapResponse {
    if (preferredRoadmapId != null) {
      const matched = roadmaps.find((roadmap) => roadmap.id === preferredRoadmapId);
      if (matched) {
        return matched;
      }
    }

    return roadmaps[0];
  }

  private roadmapStatusRank(status: string | undefined): number {
    const normalized = (status || '').toUpperCase();
    if (normalized === 'ACTIVE') return 5;
    if (normalized === 'COMPLETED') return 4;
    if (normalized === 'PAUSED') return 3;
    if (normalized === 'GENERATING') return 2;
    if (normalized === 'ARCHIVED') return 1;
    return 0;
  }

  private roadmapRecencyTimestamp(roadmap: RoadmapResponse): number {
    const source = roadmap.createdAt;
    if (!source) {
      return 0;
    }

    const parsed = new Date(source).getTime();
    return Number.isFinite(parsed) ? parsed : 0;
  }

  private toRoadmapStatusLabel(status: string | undefined): string {
    const normalized = (status || '').toUpperCase();
    if (!normalized) {
      return 'Unknown';
    }

    return normalized
      .split('_')
      .map((part) => part.charAt(0) + part.slice(1).toLowerCase())
      .join(' ');
  }

  private toRoadmapStatusTone(
    status: string | undefined
  ): 'active' | 'completed' | 'paused' | 'other' {
    const normalized = (status || '').toUpperCase();
    if (normalized === 'ACTIVE' || normalized === 'GENERATING') {
      return 'active';
    }
    if (normalized === 'COMPLETED') {
      return 'completed';
    }
    if (normalized === 'PAUSED') {
      return 'paused';
    }
    return 'other';
  }

  private formatRoadmapStartedAt(value: string | undefined): string {
    if (!value) {
      return 'Start date unknown';
    }

    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) {
      return 'Start date unknown';
    }

    return `Started ${parsed.toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
    })}`;
  }

  private readSelectedRoadmapId(userId: number): number | null {
    try {
      const raw = window.localStorage.getItem(this.selectedRoadmapStorageKey(userId));
      if (!raw) {
        return null;
      }

      const candidate = Number(raw);
      return Number.isFinite(candidate) && candidate > 0 ? candidate : null;
    } catch {
      return null;
    }
  }

  private persistSelectedRoadmapId(userId: number, roadmapId: number): void {
    try {
      window.localStorage.setItem(this.selectedRoadmapStorageKey(userId), String(roadmapId));
    } catch {
      // Ignore storage failures in restricted browsing contexts.
    }
  }

  private selectedRoadmapStorageKey(userId: number): string {
    return `smarthire-selected-roadmap:${userId}`;
  }

  private mapStatus(status: string | undefined): Step['status'] {
    const normalized = (status || '').toUpperCase();
    if (normalized === 'COMPLETED' || normalized === 'DONE' || normalized === 'SKIPPED') {
      return 'done';
    }
    if (normalized === 'IN_PROGRESS') {
      return 'in-progress';
    }
    return 'pending';
  }

  private toEstimatedTime(value: number | undefined): string {
    if (!value || value <= 0) {
      return '~N/A';
    }
    return `~${value}d`;
  }

  private expandInProgressStep(): void {
    const inProgress = this.stepsState().find((step) => step.status === 'in-progress');
    this.expandedStep.set(inProgress ? inProgress.number : null);
  }

  private formatMonthYear(value: string): string {
    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) {
      return 'recently';
    }
    return parsed.toLocaleDateString('en-US', { month: 'short', year: 'numeric' });
  }
}
