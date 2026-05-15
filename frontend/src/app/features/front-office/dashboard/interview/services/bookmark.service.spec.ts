import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { InterviewApiService } from '../interview-api.service';
import { BookmarkService } from './bookmark.service';

describe('BookmarkService', () => {
  let service: BookmarkService;
  let apiSpy: jasmine.SpyObj<InterviewApiService>;

  beforeEach(() => {
    apiSpy = jasmine.createSpyObj<InterviewApiService>('InterviewApiService', [
      'getBookmarksByUser',
      'addBookmark',
      'updateBookmarkNote',
      'removeBookmark',
    ]);

    TestBed.configureTestingModule({
      providers: [
        BookmarkService,
        { provide: InterviewApiService, useValue: apiSpy },
      ],
    });

    service = TestBed.inject(BookmarkService);
  });

  it('loads bookmarks for a user and exposes snapshot', () => {
    apiSpy.getBookmarksByUser.and.returnValue(of([{ id: 11, userId: 99, questionId: 5, note: 'a', tagLabel: 'tag' }] as any));

    service.ensureLoaded(99).subscribe();

    expect(apiSpy.getBookmarksByUser).toHaveBeenCalledWith(99);
    expect(service.getSnapshot().length).toBe(1);
    expect(service.isBookmarked(5)).toBeTrue();
  });

  it('updates existing bookmark note when question is already saved', () => {
    apiSpy.getBookmarksByUser.and.returnValue(of([{ id: 11, userId: 99, questionId: 5, note: 'a', tagLabel: 'tag' }] as any));
    apiSpy.updateBookmarkNote.and.returnValue(of({ id: 11, userId: 99, questionId: 5, note: 'updated', tagLabel: 'tag' } as any));

    service.ensureLoaded(99).subscribe();
    service.saveBookmark(99, 5, 'updated', 'ignored').subscribe();

    expect(apiSpy.updateBookmarkNote).toHaveBeenCalledWith(11, 'updated');
    expect(service.getBookmarkByQuestionId(5)?.note).toBe('updated');
  });

  it('removes bookmark from local store after API delete', () => {
    apiSpy.getBookmarksByUser.and.returnValue(of([{ id: 11, userId: 99, questionId: 5, note: 'a', tagLabel: 'tag' }] as any));
    apiSpy.removeBookmark.and.returnValue(of(void 0));

    service.ensureLoaded(99).subscribe();
    service.removeBookmark(99, 5).subscribe();

    expect(apiSpy.removeBookmark).toHaveBeenCalledWith(99, 5);
    expect(service.getSnapshot().length).toBe(0);
    expect(service.isBookmarked(5)).toBeFalse();
  });
});
