import { CommonModule } from '@angular/common';
import { Component, Input, OnChanges, OnInit, SimpleChanges, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import { of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { LUCIDE_ICONS } from '../../../../../../shared/lucide-icons';
import { BookmarkService } from '../../services/bookmark.service';
import { QuestionBookmarkDto } from '../../interview.models';

type BookmarkAnimState = 'saved' | 'removed' | null;

@Component({
  selector: 'app-bookmark-button',
  standalone: true,
  imports: [CommonModule, FormsModule, LUCIDE_ICONS],
  templateUrl: './bookmark-button.component.html',
  styleUrl: './bookmark-button.component.scss',
})
export class BookmarkButtonComponent implements OnInit, OnChanges {
  private readonly bookmarks = inject(BookmarkService);

  private readonly questionIdState = signal<number | null>(null);
  private readonly bookmarksState = toSignal(this.bookmarks.bookmarks$, {
    initialValue: [] as QuestionBookmarkDto[],
  });

  @Input({ required: true }) questionId!: number;
  @Input() userId: number | null = null;

  readonly panelOpen = signal(false);
  readonly busy = signal(false);
  readonly noteDraft = signal('');
  readonly tagDraft = signal('');
  readonly requestError = signal<string | null>(null);
  readonly requestMessage = signal<string | null>(null);
  readonly animState = signal<BookmarkAnimState>(null);

  readonly currentBookmark = computed(() => {
    const questionId = this.questionIdState();
    if (!questionId) {
      return null;
    }

    return this.bookmarksState().find((bookmark) => bookmark.questionId === questionId) ?? null;
  });

  readonly isBookmarked = computed(() => this.currentBookmark() !== null);

  ngOnInit(): void {
    this.questionIdState.set(this.questionId);
    this.syncDraftFromCurrent();
    this.ensureLoaded();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['questionId']) {
      this.questionIdState.set(this.questionId);
      this.syncDraftFromCurrent();
    }

    if (changes['userId']) {
      this.ensureLoaded();
    }
  }

  togglePanel(): void {
    this.panelOpen.update((value) => !value);
    this.requestError.set(null);
    this.requestMessage.set(null);

    if (this.panelOpen()) {
      this.syncDraftFromCurrent();
    }
  }

  save(): void {
    const userId = this.userId;
    const questionId = this.questionIdState();
    if (!userId || !questionId || this.busy()) {
      return;
    }

    const note = this.noteDraft().trim();
    const tag = this.tagDraft().trim();

    this.busy.set(true);
    this.requestError.set(null);

    this.bookmarks
      .saveBookmark(userId, questionId, note, tag)
      .pipe(catchError(() => of(null)))
      .subscribe((saved) => {
        this.busy.set(false);
        if (!saved) {
          this.requestError.set('Unable to save bookmark right now.');
          return;
        }

        this.syncDraftFromCurrent();
        this.requestMessage.set(this.isBookmarked() ? 'Saved to your bookmark library.' : 'Bookmark saved.');
        this.flash('saved');
      });
  }

  remove(): void {
    const userId = this.userId;
    const questionId = this.questionIdState();
    if (!userId || !questionId || this.busy() || !this.isBookmarked()) {
      return;
    }

    this.busy.set(true);
    this.requestError.set(null);

    this.bookmarks
      .removeBookmark(userId, questionId)
      .pipe(catchError(() => of(null)))
      .subscribe((result) => {
        this.busy.set(false);
        if (result === null) {
          this.requestError.set('Unable to remove bookmark right now.');
          return;
        }

        this.noteDraft.set('');
        this.tagDraft.set('');
        this.panelOpen.set(false);
        this.requestMessage.set('Bookmark removed.');
        this.flash('removed');
      });
  }

  private ensureLoaded(): void {
    const userId = this.userId;
    if (!userId) {
      return;
    }

    this.bookmarks
      .ensureLoaded(userId)
      .pipe(catchError(() => of([])))
      .subscribe();
  }

  private syncDraftFromCurrent(): void {
    const bookmark = this.currentBookmark();
    this.noteDraft.set(bookmark?.note ?? '');
    this.tagDraft.set(bookmark?.tagLabel ?? '');
  }

  private flash(state: BookmarkAnimState): void {
    this.animState.set(state);
    setTimeout(() => {
      if (this.animState() === state) {
        this.animState.set(null);
      }
    }, 650);
  }
}
