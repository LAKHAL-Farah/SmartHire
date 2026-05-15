import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { animate, state, style, transition, trigger } from '@angular/animations';
import { Router } from '@angular/router';
import { finalize } from 'rxjs/operators';
import {
  GitHubProfileDto,
  GitHubProfileFeedback,
  GitHubRepoDto,
  ProfileTipDto,
} from '../../../../core/models/profile-optimizer.models';
import { ProfileOptimizerService } from '../../../../core/services/profile-optimizer.service';
import { LUCIDE_ICONS } from '../../../../shared/lucide-icons';
import { ToastComponent } from '../../../../shared/components/toast/toast.component';
import { ToastService } from '../../../../shared/components/toast/toast.service';

@Component({
  selector: 'app-github',
  standalone: true,
  imports: [CommonModule, LUCIDE_ICONS, ToastComponent],
  templateUrl: './github.component.html',
  styleUrl: './github.component.scss',
  animations: [
    trigger('phaseSwap', [
      state('input', style({ opacity: 1, transform: 'translateY(0)' })),
      state('results', style({ opacity: 1, transform: 'translateY(0)' })),
      transition('input <=> results', [
        style({ opacity: 0, transform: 'translateY(12px)' }),
        animate('280ms ease-out'),
      ]),
    ]),
    trigger('accordion', [
      state('closed', style({ height: '0px', opacity: 0, overflow: 'hidden' })),
      state('open', style({ height: '*', opacity: 1, overflow: 'hidden' })),
      transition('closed <=> open', animate('280ms ease-out')),
    ]),
  ],
})
export class GithubComponent implements OnInit, OnDestroy {
  private readonly optimizerApi = inject(ProfileOptimizerService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly router = inject(Router);
  private readonly toastService = inject(ToastService);

  readonly profile = signal<GitHubProfileDto | null>(null);
  readonly repositories = signal<GitHubRepoDto[]>([]);
  readonly phase = signal<'input' | 'results'>('input');
  readonly githubUrlInput = signal('');
  readonly expandedRepoId = signal<string | null>(null);
  readonly loading = signal<{ page: boolean; audit: boolean }>({ page: true, audit: false });
  readonly error = signal<string | null>(null);
  readonly statusMessage = signal('Fetching public repositories...');
  readonly whatAuditOpen = signal(false);
  readonly githubTips = signal<ProfileTipDto[]>([]);

  readonly searchQuery = signal('');
  readonly filterForked = signal<boolean | null>(null);
  readonly sortBy = signal<'score' | 'stars' | 'recent' | 'worst'>('score');

  private statusIntervalId: number | null = null;
  private statusIndex = 0;
  private readonly statusMessages = [
    'Fetching public repositories...',
    'Analyzing repositories...',
    'Scoring README files...',
    'Generating AI assessments...',
    'Computing final scores...',
    'Almost done...',
  ];

  readonly topLanguages = computed(() => {
    const raw = this.profile()?.topLanguages;
    if (!raw) {
      return [] as string[];
    }
    try {
      return JSON.parse(raw) as string[];
    } catch {
      return [] as string[];
    }
  });

  readonly feedback = computed(() => {
    const raw = this.profile()?.feedback;
    if (!raw) {
      return null;
    }
    try {
      return JSON.parse(raw) as GitHubProfileFeedback;
    } catch {
      return null;
    }
  });

  readonly filteredRepos = computed(() => this.applyFilters());

  readonly avgReadmeScore = computed(() => {
    const repos = this.repositories();
    const values = repos
      .map((repo) => repo.readmeScore)
      .filter((score): score is number => score !== null && score !== undefined);

    if (!values.length) {
      return 0;
    }
    return Math.round(values.reduce((acc, score) => acc + score, 0) / values.length);
  });

  readonly recentRepos = computed(() => {
    const cutoff = Date.now() - 90 * 24 * 60 * 60 * 1000;
    return this.repositories().filter((repo) => {
      if (!repo.pushedAt) {
        return false;
      }
      return new Date(repo.pushedAt).getTime() >= cutoff;
    }).length;
  });

  ngOnInit(): void {
    this.loadInitialProfile();
  }

  ngOnDestroy(): void {
    this.stopStatusCycle();
  }

  loadInitialProfile(): void {
    this.loading.update((state) => ({ ...state, page: true }));
    this.error.set(null);

    this.optimizerApi
      .getGitHubProfile()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loading.update((state) => ({ ...state, page: false })))
      )
      .subscribe({
        next: (profile) => {
          this.profile.set(profile);
          this.repositories.set(profile.repositories ?? []);
          this.githubUrlInput.set(profile.profileUrl || profile.githubUsername);
          this.phase.set(profile.auditStatus === 'COMPLETED' ? 'results' : 'input');
          this.loadGitHubTips();
        },
        error: (error: unknown) => {
          if (error instanceof HttpErrorResponse && error.status === 404) {
            this.phase.set('input');
            this.profile.set(null);
            this.repositories.set([]);
            return;
          }
          this.phase.set('input');
          this.error.set('Unable to load GitHub profile right now.');
        },
      });
  }

  startAudit(): void {
    const input = this.githubUrlInput().trim();
    if (!input || this.loading().audit) {
      return;
    }

    this.error.set(null);
    this.loading.update((state) => ({ ...state, audit: true }));
    this.startStatusCycle();

    this.optimizerApi
      .auditGitHub({ githubProfileUrl: input })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.stopStatusCycle();
          this.loading.update((state) => ({ ...state, audit: false }));
        })
      )
      .subscribe({
        next: (profile) => {
          this.profile.set(profile);
          this.repositories.set(profile.repositories ?? []);
          this.phase.set('results');
          this.expandedRepoId.set(null);
          this.loadGitHubTips();
          this.toastService.show('success', 'GitHub audit completed');
        },
        error: (error: Error) => {
          this.error.set(error.message || 'GitHub audit failed.');
          this.toastService.show('error', 'GitHub audit failed');
        },
      });
  }

  reAudit(): void {
    const candidate = this.githubUrlInput().trim() || this.profile()?.profileUrl || this.profile()?.githubUsername || '';
    if (!candidate || this.loading().audit) {
      return;
    }

    this.error.set(null);
    this.loading.update((state) => ({ ...state, audit: true }));
    this.startStatusCycle();

    this.optimizerApi
      .reauditGitHub({ githubProfileUrl: candidate })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.stopStatusCycle();
          this.loading.update((state) => ({ ...state, audit: false }));
        })
      )
      .subscribe({
        next: (profile) => {
          this.profile.set(profile);
          this.repositories.set(profile.repositories ?? []);
          this.phase.set('results');
          this.expandedRepoId.set(null);
          this.loadGitHubTips();
          this.toastService.show('success', 'GitHub re-audit completed');
        },
        error: (error: Error) => {
          this.error.set(error.message || 'GitHub re-audit failed.');
          this.toastService.show('error', 'GitHub re-audit failed');
        },
      });
  }

  editAuditedProfile(): void {
    if (this.loading().audit) {
      return;
    }
    this.error.set(null);
    this.githubUrlInput.set(this.profile()?.profileUrl || this.profile()?.githubUsername || this.githubUrlInput());
    this.phase.set('input');
    this.whatAuditOpen.set(false);
  }

  toggleAuditInfo(): void {
    this.whatAuditOpen.update((current) => !current);
  }

  toggleRepoExpand(repoId: string): void {
    this.expandedRepoId.set(this.expandedRepoId() === repoId ? null : repoId);
  }

  clearFilters(): void {
    this.searchQuery.set('');
    this.filterForked.set(null);
    this.sortBy.set('score');
  }

  setForkFilter(value: boolean | null): void {
    this.filterForked.set(value);
  }

  openRepo(url: string): void {
    window.open(url, '_blank', 'noopener,noreferrer');
  }

  openProfile(): void {
    const url = this.profile()?.profileUrl;
    if (!url) {
      return;
    }
    window.open(url, '_blank', 'noopener,noreferrer');
  }

  viewAllTips(): void {
    void this.router.navigate(['/dashboard/optimizer']);
  }

  parseFixSuggestions(raw: string | null): string[] {
    if (!raw) {
      return [];
    }
    try {
      const parsed = JSON.parse(raw) as unknown;
      return Array.isArray(parsed) ? parsed.map((item) => String(item)).slice(0, 3) : [];
    } catch {
      return [];
    }
  }

  scoreBarWidth(value: number | null | undefined): number {
    const score = value ?? 0;
    return Math.max(0, Math.min(100, score));
  }

  scoreTone(score: number | null | undefined): 'high' | 'mid' | 'low' | 'none' {
    if (score === null || score === undefined) {
      return 'none';
    }
    if (score >= 80) {
      return 'high';
    }
    if (score >= 60) {
      return 'mid';
    }
    if (score >= 40) {
      return 'low';
    }
    return 'low';
  }

  scoreAccent(score: number | null | undefined): string {
    if (score === null || score === undefined) {
      return '#6b7280';
    }
    if (score >= 80) {
      return '#22c55e'; // Green for high
    }
    if (score >= 50) {
      return '#f59e0b'; // Amber for mid
    }
    if (score >= 40) {
      return '#f97316'; // Orange for low-mid
    }
    return '#ef4444'; // Red for very low
  }

  languageColor(language: string | null): string {
    if (!language) {
      return '#7C3AED';
    }
    const map: Record<string, string> = {
      // Blue-tinted
      python: '#3b82f6',
      // Cyan-tinted
      typescript: '#06b6d4',
      javascript: '#14b8a6',
      // Warm colors
      java: '#f59e0b',
      kotlin: '#f59e0b',
      // Cool colors
      go: '#06b6d4',
      rust: '#f97316',
      cpp: '#7c3aed',
      csharp: '#8b5cf6',
      // Default
      html: '#f97316',
      css: '#06b6d4',
      sql: '#f59e0b',
      ruby: '#ef4444',
      php: '#a78bfa',
      swift: '#f97316',
    };
    return map[language.toLowerCase()] ?? '#7C3AED';
  }

  relativeTime(isoDate: string | null): string {
    if (!isoDate) {
      return 'Unknown activity';
    }

    const diff = Date.now() - new Date(isoDate).getTime();
    const days = Math.floor(diff / (24 * 60 * 60 * 1000));
    if (days <= 0) {
      return 'today';
    }
    if (days === 1) {
      return '1 day ago';
    }
    if (days < 30) {
      return `${days} days ago`;
    }

    const months = Math.floor(days / 30);
    if (months === 1) {
      return '1 month ago';
    }
    if (months < 12) {
      return `${months} months ago`;
    }

    const years = Math.floor(months / 12);
    return years === 1 ? '1 year ago' : `${years} years ago`;
  }

  getStaggerDelay(index: number): string {
    return `${index * 60}ms`;
  }

  private applyFilters(): GitHubRepoDto[] {
    let list = [...this.repositories()];

    const query = this.searchQuery().trim().toLowerCase();
    if (query) {
      list = list.filter((repo) => repo.repoName.toLowerCase().includes(query));
    }

    const forkFilter = this.filterForked();
    if (forkFilter !== null) {
      list = list.filter((repo) => repo.isForked === forkFilter);
    }

    switch (this.sortBy()) {
      case 'stars':
        list.sort((left, right) => (right.stars ?? 0) - (left.stars ?? 0));
        break;
      case 'recent':
        list.sort((left, right) => {
          if (!left.pushedAt && !right.pushedAt) {
            return 0;
          }
          if (!left.pushedAt) {
            return 1;
          }
          if (!right.pushedAt) {
            return -1;
          }
          return new Date(right.pushedAt).getTime() - new Date(left.pushedAt).getTime();
        });
        break;
      case 'worst':
        list.sort((left, right) => {
          const l = left.overallScore;
          const r = right.overallScore;
          if (l === null && r === null) {
            return 0;
          }
          if (l === null) {
            return 1;
          }
          if (r === null) {
            return -1;
          }
          return l - r;
        });
        break;
      default:
        list.sort((left, right) => {
          const l = left.overallScore;
          const r = right.overallScore;
          if (l === null && r === null) {
            return 0;
          }
          if (l === null) {
            return 1;
          }
          if (r === null) {
            return -1;
          }
          return r - l;
        });
        break;
    }

    return list;
  }

  private startStatusCycle(): void {
    this.stopStatusCycle();
    this.statusIndex = 0;
    this.statusMessage.set(this.statusMessages[this.statusIndex]);

    this.statusIntervalId = window.setInterval(() => {
      this.statusIndex = (this.statusIndex + 1) % this.statusMessages.length;
      this.statusMessage.set(this.statusMessages[this.statusIndex]);
    }, 4000);
  }

  private stopStatusCycle(): void {
    if (this.statusIntervalId !== null) {
      window.clearInterval(this.statusIntervalId);
      this.statusIntervalId = null;
    }
    this.statusMessage.set(this.statusMessages[0]);
  }

  private loadGitHubTips(): void {
    this.optimizerApi
      .getTips('GITHUB')
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (tips) => this.githubTips.set(tips.slice(0, 3)),
        error: () => this.githubTips.set([]),
      });
  }
}
