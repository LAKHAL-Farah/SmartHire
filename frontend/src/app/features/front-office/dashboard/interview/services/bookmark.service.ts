import { inject, Injectable } from '@angular/core';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { finalize, tap } from 'rxjs/operators';
import { InterviewApiService } from '../interview-api.service';
import { QuestionBookmarkDto } from '../interview.models';

@Injectable({ providedIn: 'root' })
export class BookmarkService {
  private readonly api = inject(InterviewApiService);
  private readonly bookmarksSubject = new BehaviorSubject<QuestionBookmarkDto[]>([]);

  private loadedUserId: number | null = null;
  private loading = false;

  readonly bookmarks$ = this.bookmarksSubject.asObservable();

  getSnapshot(): QuestionBookmarkDto[] {
    return this.bookmarksSubject.value;
  }

  getBookmarkByQuestionId(questionId: number): QuestionBookmarkDto | null {
    return this.bookmarksSubject.value.find((bookmark) => bookmark.questionId === questionId) ?? null;
  }

  isBookmarked(questionId: number): boolean {
    return this.getBookmarkByQuestionId(questionId) !== null;
  }

  ensureLoaded(userId: number): Observable<QuestionBookmarkDto[]> {
    if (this.loadedUserId === userId && !this.loading) {
      return of(this.bookmarksSubject.value);
    }

    return this.loadForUser(userId);
  }

  loadForUser(userId: number): Observable<QuestionBookmarkDto[]> {
    this.loading = true;
    this.loadedUserId = userId;

    return this.api.getBookmarksByUser(userId).pipe(
      tap((bookmarks) => this.bookmarksSubject.next(bookmarks)),
      finalize(() => {
        this.loading = false;
      })
    );
  }

  saveBookmark(userId: number, questionId: number, note: string, tagLabel: string): Observable<QuestionBookmarkDto> {
    const existing = this.getBookmarkByQuestionId(questionId);

    if (existing) {
      return this.api.updateBookmarkNote(existing.id, note).pipe(
        tap((updated) => {
          this.bookmarksSubject.next(
            this.bookmarksSubject.value.map((bookmark) =>
              bookmark.id === updated.id
                ? {
                    ...updated,
                    tagLabel: bookmark.tagLabel,
                  }
                : bookmark
            )
          );
        })
      );
    }

    const payload = {
      userId,
      questionId,
      ...(note ? { note } : {}),
      ...(tagLabel ? { tagLabel } : {}),
    };

    return this.api.addBookmark(payload).pipe(
      tap((created) => {
        const withoutQuestion = this.bookmarksSubject.value.filter((bookmark) => bookmark.questionId !== questionId);
        this.bookmarksSubject.next([created, ...withoutQuestion]);
      })
    );
  }

  removeBookmark(userId: number, questionId: number): Observable<void> {
    return this.api.removeBookmark(userId, questionId).pipe(
      tap(() => {
        this.bookmarksSubject.next(
          this.bookmarksSubject.value.filter((bookmark) => bookmark.questionId !== questionId)
        );
      })
    );
  }
}
