import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';
import { InterviewApiService } from '../interview-api.service';
import { InterviewReportDto, InterviewSessionDto, InterviewStreakDto, SessionStatus } from '../interview.models';
import { resolveCurrentUserId } from '../interview-user.util';

@Component({
  selector: 'app-interview-history',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './interview-history.component.html',
  styleUrl: './interview-history.component.scss',
})
export class InterviewHistoryComponent implements OnInit {
  private readonly api = inject(InterviewApiService);
  private readonly router = inject(Router);
  private readonly dateFormatter = new Intl.DateTimeFormat('en-US', { month: 'short', day: 'numeric', year: 'numeric' });

  readonly userId = signal(resolveCurrentUserId());
  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly sessions = signal<InterviewSessionDto[]>([]);
  readonly reports = signal<InterviewReportDto[]>([]);
  readonly leaderboard = signal<InterviewStreakDto[]>([]);
  readonly streak = signal<InterviewStreakDto | null>(null);
  readonly activeSession = signal<InterviewSessionDto | null>(null);
  readonly bookmarkCount = signal(0);

  readonly search = signal('');
  readonly roleFilter = signal<'ALL' | 'SE' | 'CLOUD' | 'AI'>('ALL');
  readonly modeFilter = signal<'ALL' | 'PRACTICE' | 'TEST' | 'LIVE'>('ALL');
  readonly statusFilter = signal<'ALL' | SessionStatus>('ALL');
  readonly activeSessionLabel = computed(() => {
    const active = this.activeSession();
    if (!active) {
      return 'No active session';
    }

    return `Active session #${active.id} (${active.status})`;
  });

  readonly reportBySession = computed(() => {
    const map = new Map<number, InterviewReportDto>();
    for (const report of this.reports()) {
      map.set(report.sessionId, report);
    }
    return map;
  });

  readonly sortedSessions = computed(() =>
    [...this.sessions()].sort(
      (a, b) => new Date(b.startedAt ?? 0).getTime() - new Date(a.startedAt ?? 0).getTime()
    )
  );

  readonly filteredSessions = computed(() => {
    const query = this.search().trim().toLowerCase();
    return this.sortedSessions().filter((session) => {
      if (this.roleFilter() !== 'ALL' && session.roleType !== this.roleFilter()) {
        return false;
      }
      if (this.modeFilter() !== 'ALL' && session.mode !== this.modeFilter()) {
        return false;
      }
      if (this.statusFilter() !== 'ALL' && session.status !== this.statusFilter()) {
        return false;
      }

      if (!query) {
        return true;
      }

      const haystack = [
        session.roleType,
        session.mode,
        session.status,
        session.type,
        this.formatDate(session.startedAt),
      ]
        .join(' ')
        .toLowerCase();

      return haystack.includes(query);
    });
  });

  readonly completedSessions = computed(() =>
    this.sessions().filter((session) => session.status === 'COMPLETED')
  );

  readonly avgCompletedScore = computed(() => {
    const scored = this.completedSessions()
      .map((session) => this.resolveSessionScore(session))
      .filter((score): score is number => typeof score === 'number');

    if (!scored.length) {
      return null;
    }

    const total = scored.reduce((sum, score) => sum + score, 0);
    return total / scored.length;
  });

  readonly bestScore = computed(() => {
    const scored = this.completedSessions()
      .map((session) => this.resolveSessionScore(session))
      .filter((score): score is number => typeof score === 'number');

    return scored.length ? Math.max(...scored) : null;
  });

  readonly completionRate = computed(() => {
    const total = this.sessions().length;
    if (!total) {
      return 0;
    }

    return (this.completedSessions().length / total) * 100;
  });

  readonly thisMonthCount = computed(() => {
    const now = new Date();
    const month = now.getMonth();
    const year = now.getFullYear();

    return this.sessions().filter((session) => {
      if (!session.startedAt) {
        return false;
      }
      const date = new Date(session.startedAt);
      return date.getMonth() === month && date.getFullYear() === year;
    }).length;
  });

  readonly currentUserLeaderboardRank = computed(() =>
    this.leaderboard().findIndex((row) => row.userId === this.userId()) + 1
  );

  ngOnInit(): void {
    this.loadHistory();
  }

  loadHistory(): void {
    const userId = this.userId();
    if (!userId) {
      this.loading.set(false);
      this.loadError.set('No active user found.');
      return;
    }

    this.loading.set(true);
    this.loadError.set(null);

    forkJoin({
      sessions: this.api.getSessionsByUser(userId).pipe(catchError(() => of([]))),
      reports: this.api.getReportsByUser(userId).pipe(catchError(() => of([]))),
      leaderboard: this.api.getLeaderboard(8).pipe(catchError(() => of([]))),
      streak: this.api.getStreak(userId).pipe(catchError(() => of(null))),
      activeSession: this.api.getActiveSession(userId).pipe(catchError(() => of(null))),
      bookmarks: this.api.getBookmarksByUser(userId).pipe(catchError(() => of([]))),
    }).subscribe({
      next: ({ sessions, reports, leaderboard, streak, activeSession, bookmarks }) => {
        this.sessions.set(sessions);
        this.reports.set(reports);
        this.leaderboard.set(leaderboard);
        this.streak.set(streak);
        this.activeSession.set(activeSession);
        this.bookmarkCount.set(bookmarks.length);
        this.loading.set(false);
      },
      error: () => {
        this.loadError.set('Unable to load interview history.');
        this.loading.set(false);
      },
    });
  }

  openReport(reportId: number | null): void {
    if (!reportId) {
      return;
    }

    this.router.navigate(['/dashboard/interview/report', reportId]);
  }

  openSession(session: InterviewSessionDto): void {
    if (session.mode === 'LIVE') {
      const subMode = session.liveSubMode ?? 'TEST_LIVE';
      this.router
        .navigate(['/dashboard/interview/live', session.id], {
          queryParams: { subMode },
        })
        .catch(() => {
          this.router.navigate(['/interview/live', session.id], {
            queryParams: { subMode },
          });
        });
      return;
    }

    const target = `/dashboard/interview/session/${session.id}`;
    this.router.navigateByUrl(target).catch(() => {
      globalThis.location.assign(target);
    });
  }

  clearFilters(): void {
    this.search.set('');
    this.roleFilter.set('ALL');
    this.modeFilter.set('ALL');
    this.statusFilter.set('ALL');
  }

  getDisplayScore(session: InterviewSessionDto): string {
    const score = this.resolveSessionScore(session);
    return score === null ? '—' : `${score.toFixed(1)} / 10`;
  }

  getSessionDurationLabel(session: InterviewSessionDto): string {
    const seconds = session.durationSeconds;
    if (!seconds || seconds < 0) {
      return '—';
    }
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}m ${String(secs).padStart(2, '0')}s`;
  }

  getReportId(session: InterviewSessionDto): number | null {
    return session.report?.id ?? this.reportBySession().get(session.id)?.id ?? null;
  }

  formatDate(value: string | null): string {
    if (!value) {
      return '—';
    }
    return this.dateFormatter.format(new Date(value));
  }

  getStatusClass(status: SessionStatus): string {
    switch (status) {
      case 'COMPLETED':
        return 'status-completed';
      case 'IN_PROGRESS':
        return 'status-progress';
      case 'PAUSED':
        return 'status-paused';
      case 'ABANDONED':
        return 'status-abandoned';
      default:
        return 'status-evaluating';
    }
  }

  getLeaderboardMedal(index: number): string {
    if (index === 0) {
      return '🥇';
    }
    if (index === 1) {
      return '🥈';
    }
    if (index === 2) {
      return '🥉';
    }
    return `#${index + 1}`;
  }

  backToHub(): void {
    this.router.navigate(['/dashboard/interview']);
  }

  private resolveSessionScore(session: InterviewSessionDto): number | null {
    if (typeof session.totalScore === 'number') {
      return session.totalScore;
    }

    if (typeof session.report?.finalScore === 'number') {
      return session.report.finalScore;
    }

    const reportScore = this.reportBySession().get(session.id)?.finalScore;
    return typeof reportScore === 'number' ? reportScore : null;
  }
}
