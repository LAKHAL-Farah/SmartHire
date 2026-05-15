import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BehaviorSubject, of } from 'rxjs';
import { BookmarkButtonComponent } from './bookmark-button.component';
import { BookmarkService } from '../../services/bookmark.service';

class BookmarkServiceMock {
  readonly store = new BehaviorSubject<any[]>([]);
  readonly bookmarks$ = this.store.asObservable();

  ensureLoaded(): any {
    return of([]);
  }

  saveBookmark(userId: number, questionId: number, note: string, tagLabel: string): any {
    const created = { id: 1, userId, questionId, note, tagLabel };
    this.store.next([created]);
    return of(created);
  }

  removeBookmark(_userId: number, questionId: number): any {
    this.store.next(this.store.value.filter((bookmark) => bookmark.questionId !== questionId));
    return of(void 0);
  }
}

describe('BookmarkButtonComponent', () => {
  let fixture: ComponentFixture<BookmarkButtonComponent>;
  let component: BookmarkButtonComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BookmarkButtonComponent],
      providers: [{ provide: BookmarkService, useClass: BookmarkServiceMock }],
    }).compileComponents();

    fixture = TestBed.createComponent(BookmarkButtonComponent);
    component = fixture.componentInstance;
    component.questionId = 9;
    component.userId = 99;
    fixture.detectChanges();
  });

  it('opens panel and saves bookmark', () => {
    component.togglePanel();
    component.noteDraft.set('Keep this one');
    component.tagDraft.set('SystemDesign');
    component.save();

    expect(component.isBookmarked()).toBeTrue();
    expect(component.currentBookmark()?.note).toBe('Keep this one');
  });

  it('removes bookmark from store', () => {
    component.togglePanel();
    component.save();
    component.remove();

    expect(component.isBookmarked()).toBeFalse();
  });
});
