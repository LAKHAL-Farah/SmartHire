import { inject, Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { InterviewApiService } from '../interview-api.service';
import {
  DifficultyLevel,
  InterviewQuestionDto,
  QuestionType,
  RoleType,
} from '../interview.models';

export type DiscoverSort = 'newest' | 'alphabetical' | 'difficultyAsc' | 'difficultyDesc' | 'bookmarked';

export interface DiscoverFilters {
  search: string;
  role: RoleType | 'ALL';
  type: QuestionType | 'ALL';
  difficulty: DifficultyLevel | 'ALL';
  sort: DiscoverSort;
  onlyBookmarked: boolean;
  bookmarkedQuestionIds: Set<number>;
}

const difficultyWeight: Record<DifficultyLevel, number> = {
  BEGINNER: 1,
  INTERMEDIATE: 2,
  ADVANCED: 3,
  EXPERT: 4,
};

@Injectable({ providedIn: 'root' })
export class QuestionFeedService {
  private readonly api = inject(InterviewApiService);
  private readonly questionsSubject = new BehaviorSubject<InterviewQuestionDto[]>([]);

  readonly questions$ = this.questionsSubject.asObservable();

  getSnapshot(): InterviewQuestionDto[] {
    return this.questionsSubject.value;
  }

  loadQuestions(): Observable<InterviewQuestionDto[]> {
    return this.api.getQuestions().pipe(
      tap((questions) => {
        this.questionsSubject.next(questions);
      })
    );
  }

  filterAndSort(filters: DiscoverFilters, source = this.questionsSubject.value): InterviewQuestionDto[] {
    const query = filters.search.trim().toLowerCase();

    const filtered = source.filter((question) => {
      if (filters.role !== 'ALL' && question.roleType !== filters.role) {
        return false;
      }

      if (filters.type !== 'ALL' && question.type !== filters.type) {
        return false;
      }

      if (filters.difficulty !== 'ALL' && question.difficulty !== filters.difficulty) {
        return false;
      }

      if (filters.onlyBookmarked && !filters.bookmarkedQuestionIds.has(question.id)) {
        return false;
      }

      if (!query) {
        return true;
      }

      const haystack = [
        question.questionText,
        question.domain ?? '',
        question.category ?? '',
        question.tags ?? '',
      ]
        .join(' ')
        .toLowerCase();

      return haystack.includes(query);
    });

    return [...filtered].sort((left, right) => {
      if (filters.sort === 'alphabetical') {
        return left.questionText.localeCompare(right.questionText);
      }

      if (filters.sort === 'difficultyAsc') {
        return difficultyWeight[left.difficulty] - difficultyWeight[right.difficulty];
      }

      if (filters.sort === 'difficultyDesc') {
        return difficultyWeight[right.difficulty] - difficultyWeight[left.difficulty];
      }

      if (filters.sort === 'bookmarked') {
        const leftMarked = filters.bookmarkedQuestionIds.has(left.id) ? 1 : 0;
        const rightMarked = filters.bookmarkedQuestionIds.has(right.id) ? 1 : 0;
        if (leftMarked !== rightMarked) {
          return rightMarked - leftMarked;
        }
      }

      return right.id - left.id;
    });
  }
}
