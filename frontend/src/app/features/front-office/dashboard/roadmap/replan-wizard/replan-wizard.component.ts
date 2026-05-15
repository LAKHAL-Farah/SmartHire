import { CommonModule } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { catchError, finalize, of, switchMap } from 'rxjs';
import {
  AssessmentResultDto,
  RoadmapResponse,
  ReplanRequestDto,
  RoadmapApiService,
  RoadmapVisualResponse,
} from '../../../../../services/roadmap-api.service';
import { resolveRoadmapUserId } from '../roadmap-user-context';

@Component({
  selector: 'app-replan-wizard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './replan-wizard.component.html',
  styleUrl: './replan-wizard.component.scss',
})
export class ReplanWizardComponent implements OnInit {
  private readonly roadmapApi = inject(RoadmapApiService);
  private readonly router = inject(Router);

  userId = signal<number | null>(null);
  roadmapId = signal<number | null>(null);

  loading = signal(false);
  submitting = signal(false);
  autoFilledFromAssessment = signal(false);
  errorMessage = signal<string | null>(null);
  successMessage = signal<string | null>(null);

  currentGraph = signal<RoadmapVisualResponse | null>(null);
  replannedGraph = signal<RoadmapVisualResponse | null>(null);

  skillGapInput = '';
  strongSkillInput = '';

  skillGaps = signal<string[]>([]);
  strongSkills = signal<string[]>([]);
  experienceLevel = signal<'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED'>('INTERMEDIATE');

  ngOnInit(): void {
    this.userId.set(resolveRoadmapUserId());
    if (!this.userId()) {
      this.errorMessage.set('No authenticated user found. Please sign in again.');
      return;
    }
    this.prefillFromLatestAssessment(this.userId()!);
    this.loadRoadmap();
  }

  addSkillGap(): void {
    const normalized = this.normalizeToken(this.skillGapInput);
    if (!normalized) {
      return;
    }

    this.skillGaps.update((list) => (list.includes(normalized) ? list : [...list, normalized]));
    this.skillGapInput = '';
  }

  removeSkillGap(index: number): void {
    this.skillGaps.update((list) => list.filter((_, itemIndex) => itemIndex !== index));
  }

  addStrongSkill(): void {
    const normalized = this.normalizeToken(this.strongSkillInput);
    if (!normalized) {
      return;
    }

    this.strongSkills.update((list) => (list.includes(normalized) ? list : [...list, normalized]));
    this.strongSkillInput = '';
  }

  removeStrongSkill(index: number): void {
    this.strongSkills.update((list) => list.filter((_, itemIndex) => itemIndex !== index));
  }

  submitReplan(): void {
    const roadmapId = this.roadmapId();

    if (this.submitting()) {
      return;
    }

    if (!roadmapId) {
      this.errorMessage.set('No active roadmap found. Please generate one from assessment first.');
      return;
    }

    this.errorMessage.set(null);
    this.successMessage.set(null);

    const payload: ReplanRequestDto = {
      roadmapId,
      newSkillGaps: this.skillGaps(),
      newStrongSkills: this.strongSkills(),
      experienceLevel: this.experienceLevel(),
    };

    this.submitting.set(true);
    this.roadmapApi
      .replanVisualRoadmap(roadmapId, payload)
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: (response) => {
          this.replannedGraph.set(response);
          this.successMessage.set('Roadmap replanned successfully. Review your updated path below.');
          void this.router.navigate(['/dashboard/roadmap/visual']);
        },
        error: () => {
          this.errorMessage.set('Failed to replan your roadmap. Please review your inputs and try again.');
        },
      });
  }

  trackByValue(_index: number, value: string): string {
    return value;
  }

  private loadRoadmap(): void {
    const userId = this.userId();
    if (!userId) {
      this.errorMessage.set('No authenticated user found. Please sign in again.');
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    this.roadmapApi
      .getUserRoadmap(userId)
      .pipe(
        switchMap((roadmap) => {
          this.roadmapId.set(roadmap.id);
          return this.roadmapApi.getRoadmapGraph(roadmap.id).pipe(
            catchError(() => of(this.toFallbackGraph(roadmap)))
          );
        }),
        catchError((err) => {
          this.roadmapId.set(null);
          if (err?.status === 404) {
            this.errorMessage.set('No active roadmap found. Please complete assessment to generate your first roadmap.');
          } else {
            this.errorMessage.set('Unable to load roadmap context for replanning.');
          }
          return of(null);
        }),
        finalize(() => this.loading.set(false))
      )
      .subscribe({
        next: (graph) => {
          if (!graph) {
            return;
          }
          this.currentGraph.set(graph);
        },
      });
  }

  private toFallbackGraph(roadmap: RoadmapResponse): RoadmapVisualResponse {
    const totalNodes = roadmap.totalSteps ?? 0;
    const completedNodes = roadmap.completedSteps ?? 0;
    const progressPercent = totalNodes > 0 ? (completedNodes * 100) / totalNodes : 0;

    return {
      roadmapId: roadmap.id,
      title: roadmap.title || 'Roadmap',
      description: roadmap.difficulty || '',
      status: roadmap.status || 'ACTIVE',
      totalNodes,
      completedNodes,
      progressPercent,
      streakDays: roadmap.streakDays ?? 0,
      longestStreak: roadmap.longestStreak ?? 0,
      nodes: [],
      edges: [],
    };
  }

  private normalizeToken(value: string): string {
    return value
      .trim()
      .replace(/\s+/g, ' ')
      .replace(/^./, (character) => character.toUpperCase());
  }

  private prefillFromLatestAssessment(userId: number): void {
    this.roadmapApi
      .getLatestAssessment(userId)
      .pipe(catchError(() => of(null as AssessmentResultDto | null)))
      .subscribe((result) => {
        if (!result) {
          return;
        }

        const gaps = this.parseStringList(result.skillGaps);
        const strengths = this.parseStringList(result.strongSkills);

        if (gaps.length > 0) {
          this.skillGaps.set(gaps);
        }
        if (strengths.length > 0) {
          this.strongSkills.set(strengths);
        }

        if (result.experienceLevel) {
          this.experienceLevel.set(this.normalizeExperienceLevel(result.experienceLevel));
        }

        if (gaps.length > 0 || strengths.length > 0 || !!result.experienceLevel) {
          this.autoFilledFromAssessment.set(true);
        }
      });
  }

  private parseStringList(value: string | string[] | undefined): string[] {
    if (!value) {
      return [];
    }

    if (Array.isArray(value)) {
      return value
        .map((item) => item.trim())
        .filter((item) => item.length > 0);
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
      // backend can return comma-separated text
    }

    return trimmed
      .split(/[\n,;]+/)
      .map((item) => item.trim())
      .filter((item) => item.length > 0);
  }

  private normalizeExperienceLevel(value: string): 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED' {
    const normalized = value.trim().toUpperCase();
    if (normalized === 'BEGINNER' || normalized === 'JUNIOR') {
      return 'BEGINNER';
    }
    if (normalized === 'ADVANCED' || normalized === 'SENIOR') {
      return 'ADVANCED';
    }
    return 'INTERMEDIATE';
  }
}
