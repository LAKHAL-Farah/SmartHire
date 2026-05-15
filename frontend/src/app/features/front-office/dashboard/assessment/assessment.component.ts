import { CommonModule } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { catchError, finalize, of } from 'rxjs';
import {
  AssessmentAnswerDto,
  AssessmentQuestionDto,
  RoadmapGenerationRequestDto,
  AssessmentResultDto,
  CareerPathOptionDto,
  RoadmapApiService,
} from '../../../../services/roadmap-api.service';
import { resolveRoadmapUserId } from '../roadmap/roadmap-user-context';

@Component({
  selector: 'app-assessment',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './assessment.component.html',
  styleUrl: './assessment.component.scss',
})
export class AssessmentComponent implements OnInit {
  private readonly roadmapApi = inject(RoadmapApiService);
  private readonly router = inject(Router);

  loadingCareerPaths = signal(false);
  loadingQuestions = signal(false);
  submitting = signal(false);
  loadingLatest = signal(false);
  generatingRoadmap = signal(false);
  errorMessage = signal<string | null>(null);
  successMessage = signal<string | null>(null);

  userId = signal<number | null>(null);
  careerPathId = signal<number | null>(null);
  careerPaths = signal<CareerPathOptionDto[]>([]);

  questions = signal<AssessmentQuestionDto[]>([]);
  currentQuestionIndex = signal(0);

  answers = signal<Record<number, string>>({});
  confidence = signal<Record<number, number>>({});

  latestResult = signal<AssessmentResultDto | null>(null);
  submittedResult = signal<AssessmentResultDto | null>(null);

  startedAtMs = signal<number>(Date.now());

  currentQuestion = computed(
    () => this.questions()[this.currentQuestionIndex()] ?? null
  );

  answeredCount = computed(() => Object.keys(this.answers()).length);

  progressPercent = computed(() => {
    const total = this.questions().length;
    if (total === 0) {
      return 0;
    }
    return Math.round((this.answeredCount() / total) * 100);
  });

  canSubmit = computed(
    () => this.questions().length > 0 && this.answeredCount() === this.questions().length
  );

  normalizedScore = computed(() => {
    const score = this.activeResult()?.overallScore ?? 0;
    return score <= 1 ? Math.round(score * 100) : Math.round(score);
  });

  strongSkills = computed(() => this.parseStringList(this.activeResult()?.strongSkills));
  skillGaps = computed(() => this.parseStringList(this.activeResult()?.skillGaps));

  radarPoints = computed(() => {
    const score = this.clampPercent(this.normalizedScore());
    const avgConfidence = this.computeConfidencePercent();
    const coverage = this.clampPercent(this.progressPercent());
    const readiness = this.clampPercent(100 - this.skillGaps().length * 12);

    const values = [score, avgConfidence, coverage, readiness];
    const center = 100;
    const radius = 80;
    const startAngle = -Math.PI / 2;

    return values
      .map((value, index) => {
        const angle = startAngle + (index * (Math.PI * 2)) / values.length;
        const pointRadius = (radius * value) / 100;
        const x = center + pointRadius * Math.cos(angle);
        const y = center + pointRadius * Math.sin(angle);
        return `${x.toFixed(1)},${y.toFixed(1)}`;
      })
      .join(' ');
  });

  ngOnInit(): void {
    const userId = resolveRoadmapUserId();
    if (!userId) {
      this.errorMessage.set('No authenticated user found. Please sign in again.');
      return;
    }

    this.userId.set(userId);

    this.loadLatestResult();
    this.loadCareerPaths();
  }

  loadCareerPaths(): void {
    this.loadingCareerPaths.set(true);
    this.errorMessage.set(null);

    this.roadmapApi
      .getPublishedCareerPaths()
      .pipe(finalize(() => this.loadingCareerPaths.set(false)))
      .subscribe({
        next: (paths) => {
          this.careerPaths.set(paths);

          const selected = this.resolveInitialCareerPath(paths);
          if (!selected) {
            this.careerPathId.set(null);
            this.questions.set([]);
            this.errorMessage.set(
              'No published career paths are available in the database. Please ask an admin to publish one.'
            );
            return;
          }

          this.careerPathId.set(selected);
          localStorage.setItem('careerPathId', String(selected));
          this.loadQuestions();
        },
        error: () => {
          this.errorMessage.set('Unable to load career paths from the database.');
        },
      });
  }

  onCareerPathChange(value: number | string | null): void {
    const parsed = Number(value);
    if (!Number.isFinite(parsed) || parsed <= 0) {
      this.careerPathId.set(null);
      return;
    }
    this.careerPathId.set(parsed);
    localStorage.setItem('careerPathId', String(parsed));
  }

  loadQuestions(): void {
    const careerPathId = this.careerPathId();
    if (!careerPathId) {
      this.errorMessage.set('Please select a valid career path.');
      return;
    }

    this.loadingQuestions.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.submittedResult.set(null);
    this.currentQuestionIndex.set(0);
    this.answers.set({});
    this.confidence.set({});

    this.roadmapApi
      .getAssessmentQuestions(careerPathId)
      .pipe(finalize(() => this.loadingQuestions.set(false)))
      .subscribe({
        next: (questions) => {
          const normalized = questions.map((question, index) => ({
            ...question,
            id: Number(question.id ?? index + 1),
            options: question.options ?? [],
          }));

          if (normalized.length === 0) {
            this.errorMessage.set(
              'No assessment questions were found for the selected career path in the database.'
            );
            this.questions.set([]);
            return;
          }

          this.questions.set(normalized);
          this.startedAtMs.set(Date.now());
        },
        error: () => {
          this.errorMessage.set('Unable to load assessment questions from the database.');
        },
      });
  }

  selectOption(questionId: number, option: string): void {
    this.answers.update((state) => ({ ...state, [questionId]: option }));

    if (!this.confidence()[questionId]) {
      this.confidence.update((state) => ({ ...state, [questionId]: 3 }));
    }
  }

  setConfidence(questionId: number, value: number): void {
    this.confidence.update((state) => ({ ...state, [questionId]: value }));
  }

  getSelectedOption(questionId: number): string | undefined {
    return this.answers()[questionId];
  }

  getConfidence(questionId: number): number {
    return this.confidence()[questionId] ?? 3;
  }

  previousQuestion(): void {
    this.currentQuestionIndex.update((index) => Math.max(0, index - 1));
  }

  nextQuestion(): void {
    this.currentQuestionIndex.update((index) =>
      Math.min(this.questions().length - 1, index + 1)
    );
  }

  submitAssessment(): void {
    const userId = this.userId();
    const careerPathId = this.careerPathId();

    if (!userId || !careerPathId) {
      this.errorMessage.set('Missing user or career path context. Please refresh and try again.');
      return;
    }

    if (!this.canSubmit() || this.submitting()) {
      this.errorMessage.set('Please answer all questions before submitting.');
      return;
    }

    const questions = this.questions();
    const answers = this.answers();
    const confidence = this.confidence();

    const elapsedSeconds = Math.max(
      30,
      Math.round((Date.now() - this.startedAtMs()) / 1000)
    );
    const perQuestionSeconds = Math.max(10, Math.round(elapsedSeconds / questions.length));

    const payload: AssessmentAnswerDto[] = questions.map((question) => ({
      questionId: question.id,
      selectedOption: answers[question.id],
      confidenceLevel: confidence[question.id] ?? 3,
      timeSpentSeconds: perQuestionSeconds,
    }));

    this.submitting.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);

    this.roadmapApi
      .submitAssessment(userId, careerPathId, payload)
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: (result) => {
          this.submittedResult.set(result);
          this.latestResult.set(result);
          this.successMessage.set('Assessment submitted. Your personalized result is ready.');

          const generationRequest: RoadmapGenerationRequestDto = {
            userId,
            careerPathId,
            careerPathName: this.resolveCareerPathName(careerPathId),
            skillGaps: this.parseStringList(result.skillGaps),
            strongSkills: this.parseStringList(result.strongSkills),
            experienceLevel: this.normalizeExperienceLevel(result.experienceLevel),
            weeklyHoursAvailable: 10,
            preferredLanguage: 'EN',
          };

          this.generatingRoadmap.set(true);
          this.roadmapApi
            .generateVisualRoadmap(generationRequest)
            .pipe(finalize(() => this.generatingRoadmap.set(false)))
            .subscribe({
              next: () => {
                this.successMessage.set(
                  'Assessment submitted and roadmap generated. Redirecting to your roadmap...'
                );
                void this.router.navigate(['/dashboard/roadmap/visual']);
              },
              error: () => {
                this.errorMessage.set(
                  'Assessment saved, but roadmap generation failed. You can retry from the roadmap page.'
                );
              },
            });
        },
        error: () => {
          this.errorMessage.set('Assessment submission failed. Please try again.');
        },
      });
  }

  trackOption(_index: number, option: string): string {
    return option;
  }

  trackCareerPath(_index: number, path: CareerPathOptionDto): number {
    return path.id;
  }

  private loadLatestResult(): void {
    const userId = this.userId();
    if (!userId) {
      return;
    }

    this.loadingLatest.set(true);

    this.roadmapApi
      .getLatestAssessment(userId)
      .pipe(
        catchError(() => of(null)),
        finalize(() => this.loadingLatest.set(false))
      )
      .subscribe((result) => {
        this.latestResult.set(result);
      });
  }

  private activeResult(): AssessmentResultDto | null {
    return this.submittedResult() || this.latestResult();
  }

  private computeConfidencePercent(): number {
    const values = Object.values(this.confidence());
    if (values.length === 0) {
      return 50;
    }

    const average = values.reduce((sum, value) => sum + value, 0) / values.length;
    return this.clampPercent(Math.round((average / 5) * 100));
  }

  private clampPercent(value: number): number {
    if (!Number.isFinite(value)) {
      return 0;
    }
    return Math.max(0, Math.min(100, value));
  }

  private parseStringList(value: string | string[] | undefined): string[] {
    if (!value) {
      return [];
    }

    if (Array.isArray(value)) {
      return value.map((item) => item.trim()).filter((item) => item.length > 0);
    }

    const trimmed = value.trim();
    if (!trimmed) {
      return [];
    }

    try {
      const parsed = JSON.parse(trimmed);
      if (Array.isArray(parsed)) {
        return parsed
          .map((item) => String(item).trim())
          .filter((item) => item.length > 0);
      }
    } catch {
      // The backend may return comma-separated values instead of JSON arrays.
    }

    return trimmed
      .split(/[\n,;]+/)
      .map((item) => item.trim())
      .filter((item) => item.length > 0);
  }

  private resolveInitialCareerPath(paths: CareerPathOptionDto[]): number | null {
    if (!paths.length) {
      return null;
    }

    const fromStorage = localStorage.getItem('careerPathId');
    const parsed = Number(fromStorage);
    if (Number.isFinite(parsed) && parsed > 0 && paths.some((path) => path.id === parsed)) {
      return parsed;
    }

    return paths[0].id;
  }

  private resolveCareerPathName(careerPathId: number): string {
    return (
      this.careerPaths().find((path) => path.id === careerPathId)?.title ||
      `Career Path ${careerPathId}`
    );
  }

  private normalizeExperienceLevel(value: string | undefined): string {
    const normalized = (value || 'JUNIOR').trim().toUpperCase();
    if (normalized === 'BEGINNER' || normalized === 'JUNIOR') {
      return 'BEGINNER';
    }
    if (normalized === 'ADVANCED' || normalized === 'SENIOR') {
      return 'ADVANCED';
    }
    return 'INTERMEDIATE';
  }
}
