import { CommonModule } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { catchError, finalize, forkJoin, of, switchMap } from 'rxjs';
import {
  MilestoneDto,
  PaceSnapshotDto,
  ProgressSummaryDto,
  RoadmapApiService,
} from '../../../../../services/roadmap-api.service';
import { resolveRoadmapUserId } from '../roadmap-user-context';

interface PaceView {
  status: string;
  expectedCompletionDays?: number;
  projectedCompletionDays?: number;
  note?: string;
}

@Component({
  selector: 'app-progress-analytics',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './progress-analytics.component.html',
  styleUrl: './progress-analytics.component.scss',
})
export class ProgressAnalyticsComponent implements OnInit {
  private readonly roadmapApi = inject(RoadmapApiService);

  loading = signal(false);
  errorMessage = signal<string | null>(null);

  userId = signal<number | null>(null);
  roadmapId = signal<number | null>(null);

  summary = signal<ProgressSummaryDto | null>(null);
  milestones = signal<MilestoneDto[]>([]);
  streak = signal<{ currentStreak: number; longestStreak: number }>({ currentStreak: 0, longestStreak: 0 });
  pace = signal<PaceView | null>(null);

  achievedMilestones = computed(
    () => this.milestones().filter((milestone) => !!milestone.reachedAt).length
  );

  milestonePercent = computed(() => {
    const total = this.milestones().length;
    if (total === 0) {
      return 0;
    }
    return Math.round((this.achievedMilestones() / total) * 100);
  });

  momentumScore = computed(() => {
    const progress = this.summary()?.progressPercent ?? 0;
    const streakScore = Math.min(100, (this.streak().currentStreak / 14) * 100);
    const milestoneScore = this.milestonePercent();
    return Math.round(progress * 0.55 + streakScore * 0.25 + milestoneScore * 0.2);
  });

  ngOnInit(): void {
    this.userId.set(resolveRoadmapUserId());
    if (!this.userId()) {
      this.errorMessage.set('No authenticated user found. Please sign in again.');
      return;
    }
    this.loadAnalytics();
  }

  trackMilestone(_index: number, milestone: MilestoneDto): number {
    return milestone.id;
  }

  private loadAnalytics(): void {
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
          return forkJoin({
            summary: this.roadmapApi
              .getProgressSummary(roadmap.id)
              .pipe(catchError(() => of(null))),
            milestones: this.roadmapApi.getMilestones(roadmap.id).pipe(catchError(() => of([]))),
            streak: this.roadmapApi
              .getStreakInfo(userId, roadmap.id)
              .pipe(catchError(() => of({ currentStreak: 0, longestStreak: 0 }))),
            pace: this.roadmapApi
              .getCurrentPace(roadmap.id)
              .pipe(catchError(() => of(null))),
          });
        }),
        finalize(() => this.loading.set(false))
      )
      .subscribe({
        next: ({ summary, milestones, streak, pace }) => {
          this.summary.set(summary);
          this.streak.set(streak);
          this.milestones.set(
            milestones.slice().sort((a, b) => a.stepThreshold - b.stepThreshold)
          );
          this.pace.set(this.mapPace(pace));
        },
        error: () => {
          this.errorMessage.set('Unable to load progress analytics at this time.');
        },
      });
  }

  private mapPace(payload: PaceSnapshotDto | null): PaceView | null {
    if (!payload || typeof payload !== 'object') {
      return null;
    }

    const planned = Number(payload.plannedSteps ?? 0);
    const completed = Number(payload.completedSteps ?? 0);
    const remaining = Math.max(0, planned - completed);

    const status = this.asString(payload.paceStatus) || 'Unknown';

    return {
      status,
      expectedCompletionDays: planned || undefined,
      projectedCompletionDays: remaining,
      note: payload.catchUpPlanNote,
    };
  }

  private asString(value: unknown): string | undefined {
    return typeof value === 'string' && value.trim().length > 0 ? value : undefined;
  }

}
