import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';

export interface RoadmapAdminStatsResponse {
  totalRoadmaps: number;
  completedRoadmaps: number;
  completionRatePercent: number;
  averageProgressPercent: number;
}

export interface RoadmapCompletionRateRow {
  roadmapId: number;
  title: string;
  completedSteps: number;
  totalSteps: number;
  progressPercent: number;
}

@Injectable({ providedIn: 'root' })
export class RoadmapAdminApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl.replace(/\/$/, '')}/admin/roadmaps`;

  getStats(): Observable<RoadmapAdminStatsResponse> {
    return this.http.get<RoadmapAdminStatsResponse>(`${this.base}/stats`);
  }

  getCompletionRates(): Observable<RoadmapCompletionRateRow[]> {
    return this.http.get<RoadmapCompletionRateRow[]>(`${this.base}/completion-rates`);
  }

  getAverageProgress(): Observable<{ averageProgress: number }> {
    return this.http.get<{ averageProgress: number }>(`${this.base}/avg-progress`);
  }
}
