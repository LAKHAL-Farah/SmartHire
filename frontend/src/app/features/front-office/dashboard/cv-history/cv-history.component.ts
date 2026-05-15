import { CommonModule } from '@angular/common';
import { Component, DestroyRef, computed, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { resolveCurrentProfileUserId } from '../../../../core/services/current-user-id';
import { Router, RouterLink } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { LUCIDE_ICONS } from '../../../../shared/lucide-icons';
import {
  CandidateCvDto,
  ParsedCvContent,
} from '../../../../core/models/profile-optimizer.models';
import { ProfileOptimizerService } from '../../../../core/services/profile-optimizer.service';

@Component({
  selector: 'app-cv-history',
  standalone: true,
  imports: [CommonModule, RouterLink, LUCIDE_ICONS],
  templateUrl: './cv-history.component.html',
  styleUrl: './cv-history.component.scss',
})
export class CvHistoryComponent implements OnInit {
  private readonly optimizerApi = inject(ProfileOptimizerService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly cvs = signal<CandidateCvDto[]>([]);
  readonly selectedCvId = signal<string | null>(null);

  readonly selectedCv = computed(() => {
    const rows = this.cvs();
    if (rows.length === 0) {
      return null;
    }

    const selectedId = this.selectedCvId();
    if (selectedId) {
      return rows.find((row) => row.id === selectedId) ?? rows[0];
    }

    return rows[0];
  });

  readonly parsedContent = computed(() => this.parseContent(this.selectedCv()));
  readonly activeCount = computed(() => this.cvs().filter((cv) => cv.isActive).length);
  readonly completedCount = computed(() => this.cvs().filter((cv) => cv.parseStatus === 'COMPLETED').length);
  readonly failingCount = computed(() => this.cvs().filter((cv) => cv.parseStatus === 'FAILED').length);

  ngOnInit(): void {
    this.loadHistory();
  }

  loadHistory(): void {
    this.loading.set(true);
    this.error.set(null);

    const currentUserId = resolveCurrentProfileUserId();
    if (!currentUserId) {
      this.cvs.set([]);
      this.error.set('Session expired. Please log in again.');
      this.loading.set(false);
      return;
    }

    this.optimizerApi
      .getAllCvs()
      .pipe(
        map((rows) => rows.filter((cv) => cv.userId === currentUserId)),
        catchError((err: Error) => {
          this.error.set(err.message);
          return of([] as CandidateCvDto[]);
        }),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((rows) => {
        const sorted = [...rows].sort((left, right) => new Date(right.uploadedAt).getTime() - new Date(left.uploadedAt).getTime());
        this.cvs.set(sorted);
        this.selectedCvId.set(sorted[0]?.id ?? null);
        this.loading.set(false);
      });
  }

  selectCv(cvId: string): void {
    this.selectedCvId.set(cvId);
  }

  openDetail(cvId: string): void {
    void this.router.navigate(['/dashboard/cv-detail', cvId]);
  }

  scoreTone(score: number | null): 'high' | 'mid' | 'low' {
    const value = score ?? 0;
    if (value >= 80) {
      return 'high';
    }
    if (value >= 60) {
      return 'mid';
    }
    return 'low';
  }

  parseStatusLabel(status: CandidateCvDto['parseStatus']): string {
    switch (status) {
      case 'COMPLETED':
        return 'Parsed';
      case 'PENDING':
        return 'Pending';
      case 'IN_PROGRESS':
        return 'Processing';
      case 'FAILED':
      default:
        return 'Failed';
    }
  }

  formatDate(value: string): string {
    return new Date(value).toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
    });
  }

  parsedSummary(cv: CandidateCvDto | null): string {
    const parsed = this.parseContent(cv);
    if (!parsed) {
      return cv?.parsedContent ? 'Parsed content is available but could not be decoded into a structured preview.' : 'No structured parsed content is available yet.';
    }

    return parsed.summary || 'Structured parsing completed. Open the detail page to review the full CV analysis.';
  }

  private parseContent(cv: CandidateCvDto | null): ParsedCvContent | null {
    if (!cv?.parsedContent) {
      return null;
    }

    try {
      const parsed = JSON.parse(cv.parsedContent) as ParsedCvContent;
      return parsed && typeof parsed === 'object' ? parsed : null;
    } catch {
      return null;
    }
  }
}
