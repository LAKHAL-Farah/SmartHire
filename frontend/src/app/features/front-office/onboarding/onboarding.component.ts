import { Component, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { isLocalDemoMode } from '../profile/profile-user-id';
import { StepSituationComponent } from './steps/step-situation.component';
import { StepCareerGoalComponent } from './steps/step-career-goal.component';
import { LUCIDE_ICONS } from '../../../shared/lucide-icons';
import { ProfileApiService } from '../profile/profile-api.service';
import { ACCOUNT_ROLE_KEY, getAssessmentUserId } from '../profile/profile-user-id';
import { CandidateAssignmentApiService } from '../assessments/candidate-assignment-api.service';

@Component({
  selector: 'app-onboarding',
  standalone: true,
  imports: [CommonModule, RouterLink, StepSituationComponent, StepCareerGoalComponent, LUCIDE_ICONS],
  templateUrl: './onboarding.component.html',
  styleUrl: './onboarding.component.scss',
})
export class OnboardingComponent implements OnInit {
  totalSteps = 2;

  localDemoBanner = signal(false);

  currentStep = signal(1);

  situationSelection = signal<string | null>(null);
  careerSelection = signal<string | null>(null);

  stepMeta = [
    { num: 1, label: 'Who you are' },
    { num: 2, label: 'Your target' },
  ];

  saving = signal(false);
  saveError = signal<string | null>(null);

  constructor(
    private readonly profileApi: ProfileApiService,
    private readonly assignmentApi: CandidateAssignmentApiService,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    this.localDemoBanner.set(isLocalDemoMode());
  }

  canProceed = computed(() => {
    const step = this.currentStep();
    if (step === 1) return this.situationSelection() !== null;
    if (step === 2) return this.careerSelection() !== null;
    return false;
  });

  goNext(): void {
    const step = this.currentStep();
    if (step === 1) {
      this.currentStep.set(2);
      window.scrollTo({ top: 0, behavior: 'smooth' });
      return;
    }
    if (step === 2) {
      void this.savePreferencesAndFinish();
    }
  }

  goBack(): void {
    if (this.currentStep() > 1) {
      this.currentStep.set(1);
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
  }

  skip(): void {
    if (this.currentStep() === 1) {
      this.currentStep.set(2);
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
  }

  private savePreferencesAndFinish(): void {
    const situation = this.situationSelection();
    const careerPath = this.careerSelection();

    if (!situation || !careerPath) {
      this.saveError.set('Please complete all onboarding selections before continuing.');
      return;
    }

    this.saveError.set(null);
    this.saving.set(true);
    this.profileApi
      .completeOnboarding({
        situation,
        careerPath,
        answers: [],
        skillScores: {},
        developmentPlanNotes: 'Preferences saved. You can extend your profile from the dashboard.',
      })
      .subscribe({
        next: () => {
          const role = (localStorage.getItem(ACCOUNT_ROLE_KEY) || 'candidate').toLowerCase();
          if (role === 'recruiter') {
            this.saving.set(false);
            void this.router.navigate(['/dashboard']);
            return;
          }
          const uid = getAssessmentUserId();
          this.assignmentApi.register(uid, situation, careerPath).subscribe({
            next: () => {
              this.saving.set(false);
              void this.router.navigate(['/dashboard/assessments']);
            },
            error: () => {
              this.saving.set(false);
              void this.router.navigate(['/dashboard/assessments']);
            },
          });
        },
        error: (err: unknown) => {
          this.saving.set(false);
          const msg =
            err && typeof err === 'object' && 'error' in err
              ? JSON.stringify((err as { error: unknown }).error)
              : 'Could not save preferences. Is MS-User running?';
          this.saveError.set(msg);
        },
      });
  }
}
