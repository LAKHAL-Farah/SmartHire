import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { catchError, of } from 'rxjs';
import { InterviewType, QuestionBookmarkDto } from '../interview.models';
import { BookmarkService } from '../services/bookmark.service';
import { resolveCurrentUserId } from '../interview-user.util';

@Component({
  selector: 'app-interview-bookmarks',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './interview-bookmarks.component.html',
  styleUrl: './interview-bookmarks.component.scss',
})
export class InterviewBookmarksComponent implements OnInit {
  private readonly bookmarkStore = inject(BookmarkService);
  private readonly router = inject(Router);
  private readonly bookmarksState = toSignal(this.bookmarkStore.bookmarks$, {
    initialValue: [] as QuestionBookmarkDto[],
  });

  readonly userId = resolveCurrentUserId();
  readonly noteMaxLength = 600;

  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);

  readonly bookmarks = computed(() => this.bookmarksState());
  readonly tags = computed(() => {
    const set = new Set(
      this.bookmarks()
        .map((bookmark) => bookmark.tagLabel ?? 'NONE')
        .filter((tag) => tag && tag !== 'NONE')
    );
    return [...set].sort((left, right) => left.localeCompare(right));
  });

  readonly search = signal('');
  readonly selectedTag = signal<string>('ALL');

  readonly editingBookmarkId = signal<number | null>(null);
  readonly noteDraft = signal('');
  readonly noteError = signal<string | null>(null);
  readonly noteSaving = signal(false);

  readonly deletingQuestionId = signal<number | null>(null);

  readonly filteredBookmarks = computed(() => {
    const query = this.search().trim().toLowerCase();
    return this.bookmarks().filter((bookmark) => {
      if (this.selectedTag() !== 'ALL' && (bookmark.tagLabel ?? 'NONE') !== this.selectedTag()) {
        return false;
      }

      if (!query) {
        return true;
      }

      const haystack = [
        bookmark.question?.questionText ?? '',
        bookmark.tagLabel ?? '',
        bookmark.note ?? '',
        bookmark.question?.roleType ?? '',
        bookmark.question?.type ?? '',
      ]
        .join(' ')
        .toLowerCase();

      return haystack.includes(query);
    });
  });

  ngOnInit(): void {
    this.loadBookmarks();
  }

  loadBookmarks(): void {
    const userId = this.userId;
    if (!userId) {
      this.loading.set(false);
      this.loadError.set('No active user found. Please sign in to view bookmarks.');
      return;
    }

    this.loading.set(true);
    this.loadError.set(null);

    this.bookmarkStore
      .ensureLoaded(userId)
      .pipe(catchError(() => of([])))
      .subscribe({
      next: (bookmarks) => {
        if (!bookmarks.length && !this.bookmarkStore.getSnapshot().length) {
          this.loadError.set(null);
        }
        this.loading.set(false);
      },
      error: () => {
        this.loadError.set('Unable to load bookmarks right now.');
        this.loading.set(false);
      },
    });
  }

  setTag(tag: string): void {
    this.selectedTag.set(tag);
  }

  clearFilters(): void {
    this.search.set('');
    this.selectedTag.set('ALL');
  }

  openNoteEditor(bookmark: QuestionBookmarkDto): void {
    this.editingBookmarkId.set(bookmark.id);
    this.noteDraft.set(bookmark.note ?? '');
    this.noteError.set(null);
  }

  closeNoteEditor(): void {
    this.editingBookmarkId.set(null);
    this.noteDraft.set('');
    this.noteError.set(null);
  }

  setNoteDraft(value: string): void {
    this.noteDraft.set(value.slice(0, this.noteMaxLength));
    this.noteError.set(null);
  }

  saveNote(): void {
    const bookmarkId = this.editingBookmarkId();
    const userId = this.userId;
    const activeBookmark = this.bookmarks().find((bookmark) => bookmark.id === bookmarkId);
    if (!bookmarkId || !userId || !activeBookmark || this.noteSaving()) {
      return;
    }

    const note = this.noteDraft().trim();
    if (note.length > this.noteMaxLength) {
      this.noteError.set(`Note cannot exceed ${this.noteMaxLength} characters.`);
      return;
    }

    if (note.length > 0 && note.length < 3) {
      this.noteError.set('Note must be at least 3 characters or empty.');
      return;
    }

    this.noteSaving.set(true);
    this.bookmarkStore
      .saveBookmark(userId, activeBookmark.questionId, note, activeBookmark.tagLabel ?? '')
      .pipe(catchError(() => of(null)))
      .subscribe((updated) => {
        this.noteSaving.set(false);
        if (!updated) {
          this.noteError.set('Unable to save note right now.');
          return;
        }
        this.closeNoteEditor();
      });
  }

  removeBookmark(bookmark: QuestionBookmarkDto): void {
    const userId = this.userId;
    if (!userId) {
      return;
    }

    if (this.deletingQuestionId() === bookmark.questionId) {
      return;
    }

    this.deletingQuestionId.set(bookmark.questionId);
    this.bookmarkStore
      .removeBookmark(userId, bookmark.questionId)
      .pipe(catchError(() => of(null)))
      .subscribe((result) => {
        this.deletingQuestionId.set(null);
        if (result === null) {
          return;
        }
      });
  }

  practiceFromBookmark(bookmark: QuestionBookmarkDto): void {
    const role = bookmark.question?.roleType;
    const interviewType = this.toInterviewType(bookmark.question?.type);

    this.router.navigate(['/dashboard/interview/setup'], {
      queryParams: {
        ...(role ? { role } : {}),
        ...(interviewType ? { type: interviewType } : {}),
      },
    });
  }

  backToHub(): void {
    this.router.navigate(['/dashboard/interview']);
  }

  bookmarkDate(value: string | null): string {
    if (!value) {
      return '—';
    }

    return new Date(value).toLocaleDateString();
  }

  private toInterviewType(questionType: string | null | undefined): InterviewType | null {
    if (questionType === 'BEHAVIORAL' || questionType === 'SITUATIONAL') {
      return 'BEHAVIORAL';
    }
    if (questionType === 'TECHNICAL' || questionType === 'CODING') {
      return 'TECHNICAL';
    }
    return null;
  }
}
