import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';
import { LUCIDE_ICONS } from '../../../../shared/lucide-icons';
import { InterviewApiService } from './interview-api.service';
import {
  InterviewReportDto,
  InterviewSessionDto,
  InterviewStreakDto,
  QuestionBookmarkDto,
  SessionStatus,
} from './interview.models';
import { isCurrentInterviewUser, resolveCurrentUserId } from './interview-user.util';

interface PastSessionRow {
  id: number;
  roleLabel: string;
  modeLabel: string;
  modeClass: 'mode-practice' | 'mode-test';
  liveSubMode?: 'PRACTICE_LIVE' | 'TEST_LIVE' | null;
  scoreLabel: string;
  dateLabel: string;
  status: SessionStatus;
  reportId: number | null;
}

interface DerivedStreakStats {
  currentStreak: number;
  longestStreak: number;
  totalSessionsCompleted: number;
}

@Component({
  selector: 'app-interview',
  standalone: true,
  imports: [CommonModule, LUCIDE_ICONS],
  templateUrl: './interview.component.html',
  styleUrl: './interview.component.scss'
})
export class InterviewComponent implements OnInit {
  private readonly interviewApi = inject(InterviewApiService);
  private readonly router = inject(Router);

  private readonly shortDateFormatter = new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: 'numeric',
  });

  readonly userId = resolveCurrentUserId();
  readonly isLoading = signal(true);
  readonly loadError = signal<string | null>(null);

  readonly streak = signal<InterviewStreakDto | null>(null);
  readonly sessions = signal<InterviewSessionDto[]>([]);
  readonly activeSession = signal<InterviewSessionDto | null>(null);
  readonly reports = signal<InterviewReportDto[]>([]);
  readonly bookmarks = signal<QuestionBookmarkDto[]>([]);

  readonly reportBySession = computed(() => {
    const bySession = new Map<number, InterviewReportDto>();
    for (const report of this.reports()) {
      bySession.set(report.sessionId, report);
    }
    return bySession;
  });

  readonly completedSessions = computed(() =>
    [...this.sessions()]
      .filter((session) => session.status === 'COMPLETED')
      .sort((a, b) => this.dateValue(b.startedAt) - this.dateValue(a.startedAt))
  );

  readonly sortedSessions = computed(() =>
    [...this.sessions()].sort((a, b) => this.dateValue(b.startedAt) - this.dateValue(a.startedAt))
  );

  readonly derivedStreakStats = computed<DerivedStreakStats>(() => {
    const completed = this.completedSessions();
    if (!completed.length) {
      return {
        currentStreak: 0,
        longestStreak: 0,
        totalSessionsCompleted: 0,
      };
    }

    const completionDays = new Set<string>();
    for (const session of completed) {
      const raw = session.endedAt ?? session.startedAt;
      if (!raw) {
        continue;
      }

      const day = raw.split('T')[0];
      if (day) {
        completionDays.add(day);
      }
    }

    const sortedDays = [...completionDays].sort();
    if (!sortedDays.length) {
      return {
        currentStreak: 0,
        longestStreak: 0,
        totalSessionsCompleted: completed.length,
      };
    }

    let run = 0;
    let longest = 0;
    let previous: Date | null = null;

    for (const day of sortedDays) {
      const current = new Date(`${day}T00:00:00`);
      if (!previous) {
        run = 1;
      } else {
        const deltaDays = Math.round((current.getTime() - previous.getTime()) / 86_400_000);
        run = deltaDays === 1 ? run + 1 : 1;
      }

      if (run > longest) {
        longest = run;
      }
      previous = current;
    }

    return {
      currentStreak: run,
      longestStreak: longest,
      totalSessionsCompleted: completed.length,
    };
  });

  readonly averageScoreLastTen = computed(() => {
    const scored = this.completedSessions()
      .map((session) => this.resolveSessionScore(session))
      .filter((score): score is number => typeof score === 'number')
      .slice(0, 10);

    if (!scored.length) {
      return null;
    }

    const total = scored.reduce((sum, score) => sum + score, 0);
    return total / scored.length;
  });

  readonly pastSessionRows = computed<PastSessionRow[]>(() =>
    this.sortedSessions()
      .slice(0, 5)
      .map((session) => ({
        id: session.id,
        roleLabel: this.getRoleLabel(session),
        modeLabel: session.mode,
        modeClass: session.mode === 'PRACTICE' ? 'mode-practice' : 'mode-test',
        liveSubMode: session.liveSubMode ?? null,
        scoreLabel: this.resolveSessionScore(session) === null ? '—' : this.resolveSessionScore(session)!.toFixed(1),
        dateLabel: this.formatShortDate(session.startedAt),
        status: session.status,
        reportId: session.report?.id ?? this.reportBySession().get(session.id)?.id ?? null,
      }))
  );

  ngOnInit(): void {
    this.loadHubData();
  }

  goToSetup(mode?: 'PRACTICE' | 'TEST'): void {
    this.router.navigate(['/dashboard/interview/setup'], {
      queryParams: mode ? { mode } : undefined,
    });
  }

  resumeActiveSession(): void {
    const active = this.activeSession();
    if (!active) {
      return;
    }

    this.openSession(active);
  }

  resumeSessionById(sessionId: number): void {
    const session = this.sessions().find((item) => item.id === sessionId) ?? null;
    this.openSession(session ?? sessionId);
  }

  openHistory(): void {
    this.router.navigate(['/dashboard/interview/history']);
  }

  openBookmarks(): void {
    this.router.navigate(['/dashboard/interview/bookmarks']);
  }

  openDiscover(): void {
    this.router.navigate(['/dashboard/interview/discover']);
  }

  openReport(reportId: number | null): void {
    if (!reportId) {
      return;
    }

    this.router.navigate(['/dashboard/interview/report', reportId]);
  }

  getStatusClass(status: SessionStatus): string {
    switch (status) {
      case 'COMPLETED':
        return 'status-completed';
      case 'PAUSED':
        return 'status-paused';
      case 'IN_PROGRESS':
        return 'status-active';
      case 'ABANDONED':
        return 'status-abandoned';
      default:
        return 'status-evaluating';
    }
  }

  get currentStreak(): number {
    return Math.max(this.streak()?.currentStreak ?? 0, this.derivedStreakStats().currentStreak);
  }

  get longestStreak(): number {
    return Math.max(this.streak()?.longestStreak ?? 0, this.derivedStreakStats().longestStreak);
  }

  get totalSessionsCompleted(): number {
    return Math.max(this.streak()?.totalSessionsCompleted ?? 0, this.derivedStreakStats().totalSessionsCompleted);
  }

  get totalSessions(): number {
    return this.sessions().length;
  }

  private loadHubData(): void {
    const userId = this.userId;
    if (!userId) {
      this.isLoading.set(false);
      this.loadError.set('No active user found. Please sign in again to load your interview data.');
      return;
    }

    this.isLoading.set(true);
    this.loadError.set(null);

    forkJoin({
      streak: this.interviewApi.getStreak(userId).pipe(catchError(() => of(null))),
      sessions: this.interviewApi.getSessionsByUser(userId).pipe(catchError(() => of([]))),
      activeSession: this.interviewApi.getActiveSession(userId).pipe(catchError(() => of(null))),
      reports: this.interviewApi.getReportsByUser(userId).pipe(catchError(() => of([]))),
      bookmarks: this.interviewApi.getBookmarksByUser(userId).pipe(catchError(() => of([]))),
    }).subscribe({
      next: ({ streak, sessions, activeSession, reports, bookmarks }) => {
        const filteredSessions = sessions.filter((session) => isCurrentInterviewUser(session.userId));
        const filteredReports = reports.filter((report) => isCurrentInterviewUser(report.userId));
        const filteredBookmarks = bookmarks.filter((bookmark) => isCurrentInterviewUser(bookmark.userId));

        this.streak.set(streak && isCurrentInterviewUser(streak.userId) ? streak : null);
        this.sessions.set(filteredSessions);
        this.activeSession.set(activeSession && isCurrentInterviewUser(activeSession.userId) ? activeSession : null);
        this.reports.set(filteredReports);
        this.bookmarks.set(filteredBookmarks);

        if (!streak ||
            filteredSessions.length !== sessions.length ||
            filteredReports.length !== reports.length ||
            filteredBookmarks.length !== bookmarks.length) {
          this.loadError.set('Some interview data could not be loaded.');
        }

        this.isLoading.set(false);
      },
      error: () => {
        this.loadError.set('Failed to load interview data.');
        this.isLoading.set(false);
      },
    });
  }

  private getRoleLabel(session: InterviewSessionDto): string {
    switch (session.roleType) {
      case 'SE':
        return 'SE';
      case 'CLOUD':
        return 'CLOUD';
      case 'AI':
        return 'AI';
      default:
        return session.roleType;
    }
  }

  private formatShortDate(value: string | null): string {
    if (!value) {
      return '—';
    }

    return this.shortDateFormatter.format(new Date(value));
  }

  private dateValue(value: string | null): number {
    if (!value) {
      return 0;
    }

    return new Date(value).getTime();
  }

  private resolveSessionScore(session: InterviewSessionDto): number | null {
    const direct = session.totalScore;
    if (typeof direct === 'number') {
      return direct;
    }

    const nestedReport = session.report?.finalScore;
    if (typeof nestedReport === 'number') {
      return nestedReport;
    }

    const mappedReport = this.reportBySession().get(session.id)?.finalScore;
    return typeof mappedReport === 'number' ? mappedReport : null;
  }

  private openSession(sessionOrId: InterviewSessionDto | number): void {
    const session = typeof sessionOrId === 'number'
      ? this.sessions().find((item) => item.id === sessionOrId) ?? null
      : sessionOrId;

    const sessionId = typeof sessionOrId === 'number' ? sessionOrId : sessionOrId.id;
    const isLive = session?.mode === 'LIVE';
    const liveSubMode = session?.liveSubMode ?? 'TEST_LIVE';

    if (isLive) {
      this.router
        .navigate(['/dashboard/interview/live', sessionId], {
          queryParams: { subMode: liveSubMode },
        })
        .catch(() => {
          this.router.navigate(['/interview/live', sessionId], {
            queryParams: { subMode: liveSubMode },
          });
        });
      return;
    }

    const target = `/dashboard/interview/session/${sessionId}`;
    this.router.navigateByUrl(target).catch(() => {
      globalThis.location.assign(target);
    });
  }
}