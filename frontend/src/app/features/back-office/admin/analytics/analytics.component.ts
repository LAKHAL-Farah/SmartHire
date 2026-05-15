import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { catchError, forkJoin, of } from 'rxjs';
import {
  RoadmapAdminApiService,
  RoadmapAdminStatsResponse,
  RoadmapCompletionRateRow,
} from '../../service/roadmap-admin-api.service';

@Component({
  selector: 'app-analytics',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './analytics.component.html',
  styleUrl: './analytics.component.scss'
})
export class AnalyticsComponent implements OnInit {
  private readonly roadmapAdminApi = inject(RoadmapAdminApiService);

  roadmapLoading = false;
  roadmapError: string | null = null;
  roadmapStats: RoadmapAdminStatsResponse | null = null;
  roadmapCompletionRows: RoadmapCompletionRateRow[] = [];
  roadmapAverageProgress = 0;

  ngOnInit(): void {
    this.loadRoadmapAdminMetrics();
  }

  refreshRoadmapAdminMetrics(): void {
    this.loadRoadmapAdminMetrics();
  }

  hasRoadmapData(): boolean {
    return !!this.roadmapStats || this.roadmapCompletionRows.length > 0;
  }

  private loadRoadmapAdminMetrics(): void {
    this.roadmapLoading = true;
    this.roadmapError = null;

    forkJoin({
      stats: this.roadmapAdminApi.getStats().pipe(catchError(() => of(null))),
      completionRates: this.roadmapAdminApi.getCompletionRates().pipe(catchError(() => of([]))),
      avgProgress: this.roadmapAdminApi.getAverageProgress().pipe(catchError(() => of(null))),
    }).subscribe({
      next: ({ stats, completionRates, avgProgress }) => {
        this.roadmapStats = stats;
        this.roadmapCompletionRows = completionRates;
        this.roadmapAverageProgress = avgProgress?.averageProgress ?? stats?.averageProgressPercent ?? 0;

        if (!stats && completionRates.length === 0 && !avgProgress) {
          this.roadmapError = 'No roadmap analytics data is currently available in the database.';
        }

        this.roadmapLoading = false;
      },
      error: () => {
        this.roadmapLoading = false;
        this.roadmapError = 'Unable to load roadmap admin analytics.';
      },
    });
  }
}
