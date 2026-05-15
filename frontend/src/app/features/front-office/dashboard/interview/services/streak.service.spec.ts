import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { InterviewApiService } from '../interview-api.service';
import { StreakService } from './streak.service';

describe('StreakService', () => {
  let service: StreakService;
  let apiSpy: jasmine.SpyObj<InterviewApiService>;

  beforeEach(() => {
    apiSpy = jasmine.createSpyObj<InterviewApiService>('InterviewApiService', ['getStreak']);

    TestBed.configureTestingModule({
      providers: [
        StreakService,
        { provide: InterviewApiService, useValue: apiSpy },
      ],
    });

    service = TestBed.inject(StreakService);
  });

  it('emits streak increase event when current streak grows', () => {
    apiSpy.getStreak.and.returnValues(
      of({ id: 1, userId: 99, currentStreak: 2, longestStreak: 3, lastSessionDate: null, totalSessionsCompleted: 4 } as any),
      of({ id: 1, userId: 99, currentStreak: 3, longestStreak: 3, lastSessionDate: null, totalSessionsCompleted: 5 } as any)
    );

    const events: Array<{ previous: number; current: number }> = [];
    service.streakIncrease$.subscribe((event) => events.push(event));

    service.refresh(99).subscribe();
    service.refresh(99).subscribe();

    expect(events.length).toBe(1);
    expect(events[0]).toEqual({ previous: 2, current: 3 });
  });

  it('reuses cached value after first load', () => {
    apiSpy.getStreak.and.returnValue(
      of({ id: 1, userId: 99, currentStreak: 1, longestStreak: 1, lastSessionDate: null, totalSessionsCompleted: 1 } as any)
    );

    service.ensureLoaded(99).subscribe();
    service.ensureLoaded(99).subscribe();

    expect(apiSpy.getStreak).toHaveBeenCalledTimes(1);
    expect(service.getSnapshot()?.currentStreak).toBe(1);
  });
});
