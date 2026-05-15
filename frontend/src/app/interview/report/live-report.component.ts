import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { LiveSessionService } from '../services/live-session.service';
import { InterviewReportDto, QuestionStressScoreDto } from '../../features/front-office/dashboard/interview/interview.models';

@Component({
  selector: 'app-live-report',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './live-report.component.html',
  styleUrl: './live-report.component.scss',
})
export class LiveReportComponent implements OnInit {
  sessionId = 0;
  report: InterviewReportDto | null = null;
  isLoading = true;
  reportError = '';

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly liveService: LiveSessionService
  ) {}

  ngOnInit(): void {
    this.sessionId = Number(this.route.snapshot.paramMap.get('sessionId') ?? 0);
    this.isLoading = true;

    this.liveService.getReport(this.sessionId).subscribe({
      next: (report) => {
        this.report = report;
        this.isLoading = false;
      },
      error: (error: unknown) => {
        console.error('[LiveReport] Failed to load report:', error);
        this.reportError = 'Report is being generated. Please refresh in a moment.';
        this.isLoading = false;
      },
    });
  }

  goToDashboard(): void {
    void this.router.navigate(['/dashboard']);
  }

  get communicationScore(): number {
    const score = this.report?.voiceAvg;
    return typeof score === 'number' ? score : 0;
  }

  get overallStressLevel(): 'low' | 'medium' | 'high' | null {
    const level = this.report?.overallStressLevel;
    if (level === 'low' || level === 'medium' || level === 'high') {
      return level;
    }
    return null;
  }

  get avgStressScore(): number | null {
    const value = this.report?.avgStressScore;
    return typeof value === 'number' && Number.isFinite(value) ? value : null;
  }

  get questionStressScores(): QuestionStressScoreDto[] {
    if (!this.report?.questionStressScores || !Array.isArray(this.report.questionStressScores)) {
      return [];
    }
    return this.report.questionStressScores;
  }

  numberField(key: string): number {
    if (!this.report) {
      return 0;
    }

    const value = this.report[key as keyof InterviewReportDto] as unknown;
    if (typeof value === 'number' && Number.isFinite(value)) {
      return value;
    }
    if (typeof value === 'string') {
      const parsed = Number(value);
      return Number.isFinite(parsed) ? parsed : 0;
    }
    return 0;
  }

  textField(key: string): string {
    if (!this.report) {
      return '';
    }

    const value = this.report[key as keyof InterviewReportDto] as unknown;
    return typeof value === 'string' ? value : '';
  }
}
