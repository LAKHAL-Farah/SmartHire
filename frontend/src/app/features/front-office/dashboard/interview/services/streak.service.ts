import { inject, Injectable } from '@angular/core';
import { BehaviorSubject, Observable, of, Subject } from 'rxjs';
import { tap } from 'rxjs/operators';
import { InterviewApiService } from '../interview-api.service';
import { InterviewStreakDto } from '../interview.models';

export interface StreakIncreaseEvent {
  previous: number;
  current: number;
}

@Injectable({ providedIn: 'root' })
export class StreakService {
  private readonly api = inject(InterviewApiService);
  private readonly streakSubject = new BehaviorSubject<InterviewStreakDto | null>(null);
  private readonly streakIncreaseSubject = new Subject<StreakIncreaseEvent>();

  private loadedUserId: number | null = null;

  readonly streak$ = this.streakSubject.asObservable();
  readonly streakIncrease$ = this.streakIncreaseSubject.asObservable();

  getSnapshot(): InterviewStreakDto | null {
    return this.streakSubject.value;
  }

  ensureLoaded(userId: number): Observable<InterviewStreakDto | null> {
    if (this.loadedUserId === userId && this.streakSubject.value) {
      return of(this.streakSubject.value);
    }

    return this.refresh(userId);
  }

  refresh(userId: number): Observable<InterviewStreakDto> {
    return this.api.getStreak(userId).pipe(
      tap((streak) => {
        const previous = this.streakSubject.value?.currentStreak;

        this.loadedUserId = userId;
        this.streakSubject.next(streak);

        if (previous !== undefined && streak.currentStreak > previous) {
          this.streakIncreaseSubject.next({ previous, current: streak.currentStreak });
        }
      })
    );
  }
}
