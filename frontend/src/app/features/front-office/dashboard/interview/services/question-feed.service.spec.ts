import { TestBed } from '@angular/core/testing';
import { InterviewApiService } from '../interview-api.service';
import { QuestionFeedService } from './question-feed.service';

describe('QuestionFeedService', () => {
  let service: QuestionFeedService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        QuestionFeedService,
        {
          provide: InterviewApiService,
          useValue: jasmine.createSpyObj<InterviewApiService>('InterviewApiService', ['getQuestions']),
        },
      ],
    });

    service = TestBed.inject(QuestionFeedService);
  });

  it('filters by role and search query, then sorts by newest', () => {
    const questions = [
      { id: 1, roleType: 'SE', type: 'TECHNICAL', difficulty: 'BEGINNER', questionText: 'Array reverse', domain: 'algorithms', category: 'arrays', tags: 'arrays', active: true },
      { id: 3, roleType: 'AI', type: 'TECHNICAL', difficulty: 'ADVANCED', questionText: 'Transformer attention', domain: 'ml', category: 'nlp', tags: 'ai,nlp', active: true },
      { id: 2, roleType: 'SE', type: 'CODING', difficulty: 'INTERMEDIATE', questionText: 'Array rotation', domain: 'algorithms', category: 'arrays', tags: 'arrays', active: true },
    ] as any;

    const result = service.filterAndSort({
      search: 'array',
      role: 'SE',
      type: 'ALL',
      difficulty: 'ALL',
      sort: 'newest',
      onlyBookmarked: false,
      bookmarkedQuestionIds: new Set<number>(),
    }, questions);

    expect(result.length).toBe(2);
    expect(result[0].id).toBe(2);
    expect(result[1].id).toBe(1);
  });

  it('sorts bookmarked questions first in bookmarked sort mode', () => {
    const questions = [
      { id: 5, roleType: 'SE', type: 'TECHNICAL', difficulty: 'BEGINNER', questionText: 'Q5', active: true },
      { id: 7, roleType: 'SE', type: 'TECHNICAL', difficulty: 'BEGINNER', questionText: 'Q7', active: true },
      { id: 6, roleType: 'SE', type: 'TECHNICAL', difficulty: 'BEGINNER', questionText: 'Q6', active: true },
    ] as any;

    const result = service.filterAndSort({
      search: '',
      role: 'ALL',
      type: 'ALL',
      difficulty: 'ALL',
      sort: 'bookmarked',
      onlyBookmarked: false,
      bookmarkedQuestionIds: new Set<number>([6]),
    }, questions);

    expect(result[0].id).toBe(6);
  });
});
