import { fakeAsync, flushMicrotasks, TestBed } from '@angular/core/testing';
import { convertToParamMap, ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { InterviewApiService } from '../interview-api.service';
import { InterviewSetupComponent } from './interview-setup.component';

describe('InterviewSetupComponent', () => {
  let component: InterviewSetupComponent;
  let apiSpy: jasmine.SpyObj<InterviewApiService>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    apiSpy = jasmine.createSpyObj<InterviewApiService>('InterviewApiService', ['startSession', 'startLiveSession']);
    routerSpy = jasmine.createSpyObj<Router>('Router', ['navigateByUrl']);

    await TestBed.configureTestingModule({
      imports: [InterviewSetupComponent],
      providers: [
        { provide: InterviewApiService, useValue: apiSpy },
        { provide: Router, useValue: routerSpy },
        {
          provide: ActivatedRoute,
          useValue: {
            queryParamMap: of(convertToParamMap({})),
          },
        },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(InterviewSetupComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should initialize default selections', () => {
    expect(component.selectedRole()).toBe('SE');
    expect(component.selectedType()).toBe('TECHNICAL');
    expect(component.selectedMode()).toBe('PRACTICE');
    expect(component.canStart()).toBeTrue();
  });

  it('should navigate to session on successful start', fakeAsync(() => {
    apiSpy.startSession.and.returnValue(of({ id: 42 } as any));
    routerSpy.navigateByUrl.and.returnValue(Promise.resolve(true));

    component.startInterview();
    flushMicrotasks();

    expect(apiSpy.startSession).toHaveBeenCalled();
    expect(routerSpy.navigateByUrl).toHaveBeenCalledWith('/dashboard/interview/session/42', { replaceUrl: true });
  }));

  it('should show backend message on start error', () => {
    apiSpy.startSession.and.returnValue(
      throwError(() => ({ error: { message: 'Backend failed to start session' } }))
    );

    component.startInterview();

    expect(component.errorMessage()).toContain('Backend failed to start session');
  });

  it('should start live session when LIVE mode is selected', fakeAsync(() => {
    routerSpy.navigateByUrl.and.returnValue(Promise.resolve(true));

    component.setMode('LIVE');
    component.setLiveSubMode('TEST_LIVE');
    component.startInterview();
    flushMicrotasks();

    expect(apiSpy.startLiveSession).not.toHaveBeenCalled();
    expect(routerSpy.navigateByUrl).toHaveBeenCalledWith('/dashboard/interview/live/start?subMode=TEST_LIVE&questionCount=8&company=Tech+Company&targetRole=Software+Engineer', {
      replaceUrl: true,
    });
  }));
});
