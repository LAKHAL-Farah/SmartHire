import { CommonModule } from '@angular/common';
import { Component, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, forkJoin, of } from 'rxjs';
import { LUCIDE_ICONS } from '../../../../../shared/lucide-icons';
import { BookmarkButtonComponent } from '../components/bookmark-button/bookmark-button.component';
import { InterviewApiService } from '../interview-api.service';
import { resolveCurrentUserId } from '../interview-user.util';
import {
  DifficultyLevel,
  InterviewQuestionDto,
  InterviewSessionDto,
  QuestionType,
  RoleType,
} from '../interview.models';
import { BookmarkService } from '../services/bookmark.service';
import { DiscoverSort, QuestionFeedService } from '../services/question-feed.service';
import { StreakService } from '../services/streak.service';

interface TopTagEntry {
  label: string;
  count: number;
}

@Component({
  selector: 'app-interview-discover',
  standalone: true,
  imports: [CommonModule, FormsModule, LUCIDE_ICONS, BookmarkButtonComponent],
  templateUrl: './interview-discover.component.html',
  styleUrl: './interview-discover.component.scss',
})
export class InterviewDiscoverComponent implements OnInit {
  private readonly feedService = inject(QuestionFeedService);
  private readonly bookmarkService = inject(BookmarkService);
  private readonly streakService = inject(StreakService);
  private readonly api = inject(InterviewApiService);
  private readonly router = inject(Router);

  readonly userId = resolveCurrentUserId();
  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);

  readonly search = signal('');
  readonly selectedRole = signal<RoleType | 'ALL'>('ALL');
  readonly selectedType = signal<QuestionType | 'ALL'>('ALL');
  readonly selectedDifficulty = signal<DifficultyLevel | 'ALL'>('ALL');
  readonly selectedSort = signal<DiscoverSort>('newest');
  readonly onlyBookmarked = signal(false);

  readonly visibleCount = signal(8);
  readonly pageSize = 8;

  readonly weeklyTarget = 5;
  readonly completedThisWeek = signal(0);

  readonly roleFilters: Array<RoleType | 'ALL'> = ['ALL', 'SE', 'CLOUD', 'AI'];
  readonly typeFilters: Array<QuestionType | 'ALL'> = ['ALL', 'TECHNICAL', 'BEHAVIORAL', 'SITUATIONAL', 'CODING'];
  readonly difficultyFilters: Array<DifficultyLevel | 'ALL'> = ['ALL', 'BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT'];
  readonly sortOptions: Array<{ value: DiscoverSort; label: string }> = [
    { value: 'newest', label: 'Newest' },
    { value: 'bookmarked', label: 'Bookmarked first' },
    { value: 'difficultyAsc', label: 'Easier first' },
    { value: 'difficultyDesc', label: 'Harder first' },
    { value: 'alphabetical', label: 'A-Z' },
  ];

  readonly skeletonRows = Array.from({ length: 6 }, (_, index) => index);

  private readonly questions = toSignal(this.feedService.questions$, { initialValue: [] as InterviewQuestionDto[] });
  private readonly bookmarks = toSignal(this.bookmarkService.bookmarks$, { initialValue: [] });
  readonly streak = toSignal(this.streakService.streak$, { initialValue: null });

  readonly bookmarkedQuestionIds = computed(() =>
    new Set(this.bookmarks().map((bookmark) => bookmark.questionId))
  );

  readonly filteredQuestions = computed(() =>
    this.feedService.filterAndSort(
      {
        search: this.search(),
        role: this.selectedRole(),
        type: this.selectedType(),
        difficulty: this.selectedDifficulty(),
        sort: this.selectedSort(),
        onlyBookmarked: this.onlyBookmarked(),
        bookmarkedQuestionIds: this.bookmarkedQuestionIds(),
      },
      this.questions()
    )
  );

  readonly visibleQuestions = computed(() => this.filteredQuestions().slice(0, this.visibleCount()));
  readonly canLoadMore = computed(() => this.visibleCount() < this.filteredQuestions().length);
  readonly weekProgressPercent = computed(() =>
    Math.min(100, (this.completedThisWeek() / this.weeklyTarget) * 100)
  );

  readonly topTags = computed<TopTagEntry[]>(() => {
    const counts = new Map<string, number>();

    for (const question of this.filteredQuestions()) {
      for (const tag of this.extractTags(question)) {
        counts.set(tag, (counts.get(tag) ?? 0) + 1);
      }
    }

    return [...counts.entries()]
      .map(([label, count]) => ({ label, count }))
      .sort((left, right) => right.count - left.count)
      .slice(0, 5);
  });

  ngOnInit(): void {
    this.loadFeed();
  }

  setRole(role: RoleType | 'ALL'): void {
    this.selectedRole.set(role);
    this.resetPagination();
  }

  setType(type: QuestionType | 'ALL'): void {
    this.selectedType.set(type);
    this.resetPagination();
  }

  setDifficulty(level: DifficultyLevel | 'ALL'): void {
    this.selectedDifficulty.set(level);
    this.resetPagination();
  }

  setSort(value: DiscoverSort): void {
    this.selectedSort.set(value);
    this.resetPagination();
  }

  setSearch(value: string): void {
    this.search.set(value);
    this.resetPagination();
  }

  toggleOnlyBookmarked(): void {
    this.onlyBookmarked.update((value) => !value);
    this.resetPagination();
  }

  clearFilters(): void {
    this.search.set('');
    this.selectedRole.set('ALL');
    this.selectedType.set('ALL');
    this.selectedDifficulty.set('ALL');
    this.selectedSort.set('newest');
    this.onlyBookmarked.set(false);
    this.resetPagination();
  }

  practiceQuestion(question: InterviewQuestionDto): void {
    this.router.navigate(['/dashboard/interview/setup'], {
      queryParams: {
        role: question.roleType,
        type: this.mapQuestionType(question.type),
      },
    });
  }

  @HostListener('window:scroll')
  onWindowScroll(): void {
    if (!this.canLoadMore() || this.loading()) {
      return;
    }

    const scrolledTo = window.innerHeight + window.scrollY;
    const maxHeight = document.documentElement.scrollHeight;

    if (maxHeight - scrolledTo < 260) {
      this.loadMore();
    }
  }

  private loadFeed(): void {
    const userId = this.userId;
    this.loading.set(true);
    this.loadError.set(null);

    forkJoin({
      questions: this.feedService.loadQuestions().pipe(catchError(() => of([]))),
      sessions: userId
        ? this.api.getSessionsByUser(userId).pipe(catchError(() => of([] as InterviewSessionDto[])))
        : of([] as InterviewSessionDto[]),
      bookmarks: userId
        ? this.bookmarkService.ensureLoaded(userId).pipe(catchError(() => of([])))
        : of([]),
      streak: userId
        ? this.streakService.ensureLoaded(userId).pipe(catchError(() => of(null)))
        : of(null),
    }).subscribe(({ questions, sessions }) => {
      this.computeWeeklyProgress(sessions);
      this.loading.set(false);

      if (!questions.length && !this.feedService.getSnapshot().length) {
        this.loadError.set('Question feed is unavailable right now.');
      }
    });
  }

  private loadMore(): void {
    this.visibleCount.update((value) => Math.min(value + this.pageSize, this.filteredQuestions().length));
  }

  private resetPagination(): void {
    this.visibleCount.set(this.pageSize);
  }

  private computeWeeklyProgress(sessions: InterviewSessionDto[]): void {
    const now = new Date();
    const day = now.getDay();
    const distanceToMonday = day === 0 ? 6 : day - 1;

    const weekStart = new Date(now);
    weekStart.setDate(now.getDate() - distanceToMonday);
    weekStart.setHours(0, 0, 0, 0);

    const completed = sessions.filter((session) => {
      if (session.status !== 'COMPLETED' || !session.endedAt) {
        return false;
      }

      return new Date(session.endedAt).getTime() >= weekStart.getTime();
    }).length;

    this.completedThisWeek.set(completed);
  }

  private extractTags(question: InterviewQuestionDto): string[] {
    const collected = new Set<string>();

    if (question.tags) {
      const raw = question.tags.trim();
      if (raw.startsWith('[')) {
        try {
          const parsed = JSON.parse(raw);
          if (Array.isArray(parsed)) {
            parsed.forEach((entry) => {
              const text = String(entry).trim();
              if (text) {
                collected.add(text);
              }
            });
          }
        } catch {
          // Ignore malformed tag payloads and fallback to split mode.
        }
      }

      if (!collected.size) {
        raw.split(',').forEach((piece) => {
          const text = piece.trim();
          if (text) {
            collected.add(text);
          }
        });
      }
    }

    if (question.category) {
      collected.add(question.category);
    }

    if (question.domain) {
      collected.add(question.domain);
    }

    collected.add(question.type);

    return [...collected];
  }

  private mapQuestionType(type: QuestionType): 'TECHNICAL' | 'BEHAVIORAL' {
    if (type === 'BEHAVIORAL' || type === 'SITUATIONAL') {
      return 'BEHAVIORAL';
    }

    return 'TECHNICAL';
  }
}
