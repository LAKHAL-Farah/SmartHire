import { Routes } from '@angular/router';
import { adminCanMatch } from './core/guards/admin.guard';
import { onboardingCanMatch } from './core/guards/onboarding.guard';
import { authGuard } from './core/guards/auth.guard';
import { noAuthGuard } from './core/guards/no-auth.guard';
import { roleGuard } from './core/guards/role.guard';

export const routes: Routes = [
  {
    canActivate: [noAuthGuard],
    path: '',
    loadComponent: () =>
      import('./features/front-office/landing/landing-page.component').then(
        (m) => m.LandingPageComponent
      ),
  },
  {
    canActivate: [noAuthGuard],
    path: 'login-mfa',
    loadComponent: () =>
      import('./features/front-office/auth/login-mfa/login-mfa.component').then(
        (m) => m.LoginMfaComponent
      ),
  },
  {
    canActivate: [noAuthGuard],
    path: 'login',
    loadComponent: () =>
      import('./features/front-office/auth/login-mfa/login-mfa.component').then(
        (m) => m.LoginMfaComponent
      ),
  },
  {
    canActivate: [noAuthGuard],
    path: 'forgot-password',
    loadComponent: () =>
      import('./features/front-office/auth/forgot-password/forgot-password.component').then(
        (m) => m.ForgotPasswordComponent
      ),
  },
  {
    canActivate: [noAuthGuard],
    path: 'verify-face',
    loadComponent: () =>
      import('./features/front-office/auth/verify-face/verify-face.component').then(
        (m) => m.VerifyFaceComponent
      ),
  },
  {
    path: 'setup-face-recognition',
    loadComponent: () =>
      import('./features/front-office/auth/setup-face-recognition/setup-face-recognition.component').then(
        (m) => m.SetupFaceRecognitionComponent
      ),
  },
  {
    canActivate: [noAuthGuard],
    path: 'reset-password',
    loadComponent: () =>
      import('./features/front-office/auth/reset-password/reset-password.component').then(
        (m) => m.ResetPasswordComponent
      ),
  },
  {
    path: 'register',
    canActivate: [noAuthGuard],
    loadComponent: () =>
      import('./features/front-office/auth/register/register.component').then(
        (m) => m.RegisterComponent
      ),
  },
  {
    path: 'onboarding',
    canMatch: [onboardingCanMatch],
    loadComponent: () =>
      import('./features/front-office/onboarding/onboarding.component').then(
        (m) => m.OnboardingComponent
      ),
  },
  {
    path: 'interview/live/start',
    redirectTo: 'dashboard/interview/live/start',
    pathMatch: 'full',
  },
  {
    path: 'interview/live/:sessionId',
    redirectTo: 'dashboard/interview/live/:sessionId',
    pathMatch: 'full',
  },
  {
    canActivate: [authGuard],
    path: 'interview/report/:sessionId',
    loadComponent: () =>
      import('./interview/report/live-report.component').then(
        (m) => m.LiveReportComponent
      ),
  },
  {
    canActivate: [authGuard],
    path: 'dashboard/interview/live/start',
    loadComponent: () =>
      import('./interview/live-start/live-start.component').then(
        (m) => m.LiveStartComponent
      ),
  },
  {
    canActivate: [authGuard],
    path: 'dashboard/interview/live/:sessionId',
    loadComponent: () =>
      import('./interview/live-mode/live-mode.component').then(
        (m) => m.LiveModeComponent
      ),
  },
  {
    canActivate: [authGuard],
    path: 'dashboard',
    loadComponent: () =>
      import('./features/front-office/dashboard/dashboard-layout.component').then(
        (m) => m.DashboardLayoutComponent
      ),
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./features/front-office/dashboard/dashboard-home.component').then(
            (m) => m.DashboardHomeComponent
          ),
      },
      {
        path: 'roadmap/progress',
        loadComponent: () =>
          import('./features/front-office/dashboard/roadmap/progress-analytics/progress-analytics.component').then(
            (m) => m.ProgressAnalyticsComponent
          ),
      },
      {
        path: 'roadmap/milestones',
        loadComponent: () =>
          import('./features/front-office/dashboard/roadmap/milestones/milestones.component').then(
            (m) => m.MilestonesComponent
          ),
      },
      {
        path: 'roadmap/notifications',
        loadComponent: () =>
          import('./features/front-office/dashboard/roadmap/notifications/notifications.component').then(
            (m) => m.NotificationsComponent
          ),
      },
      {
        path: 'roadmap/replan',
        loadComponent: () =>
          import('./features/front-office/dashboard/roadmap/replan-wizard/replan-wizard.component').then(
            (m) => m.ReplanWizardComponent
          ),
      },
      {
        path: 'roadmap/step/:stepId',
        loadComponent: () =>
          import('./features/front-office/dashboard/roadmap/step-detail/step-detail.component').then(
            (m) => m.StepDetailComponent
          ),
      },
      {
        path: 'roadmap/workspace',
        loadComponent: () =>
          import('./features/front-office/dashboard/roadmap/workspace/roadmap-workspace.component').then(
            (m) => m.RoadmapWorkspaceComponent
          ),
      },
      {
        path: 'roadmap',
        pathMatch: 'full',
        loadComponent: () =>
          import('./features/front-office/dashboard/roadmap/roadmap.component').then(
            (m) => m.RoadmapComponent
          ),
      },
      {
        path: 'cv',
        loadComponent: () =>
          import('./features/front-office/dashboard/cv-optimizer').then((m) => m.CvOptimizerComponent),
      },
      {
        path: 'optimizer',
        loadComponent: () =>
          import('./features/front-office/dashboard/optimizer-home/optimizer-home.component').then(
            (m) => m.OptimizerHomeComponent
          ),
      },
      {
        path: 'linkedin',
        loadComponent: () =>
          import('./features/front-office/dashboard/linkedin/linkedin.component').then(
            (m) => m.LinkedinComponent
          ),
      },
      {
        path: 'github',
        loadComponent: () =>
          import('./features/front-office/dashboard/github/github.component').then(
            (m) => m.GithubComponent
          ),
      },
      {
        path: 'cv-manager',
        loadComponent: () =>
          import('./features/front-office/dashboard/cv-manager/cv-manager.component').then(
            (m) => m.CvManagerComponent
          ),
      },
      {
        path: 'cv-detail/:cvId',
        loadComponent: () =>
          import('./features/front-office/dashboard/cv-detail/cv-detail.component').then(
            (m) => m.CvDetailComponent
          ),
      },
      {
        path: 'job-offers',
        loadComponent: () =>
          import('./features/front-office/dashboard/job-offers/job-offers.component').then(
            (m) => m.JobOffersComponent
          ),
      },
      {
        path: 'job-offers/:jobId',
        loadComponent: () =>
          import('./features/front-office/dashboard/job-offer-detail/job-offer-detail.component').then(
            (m) => m.JobOfferDetailComponent
          ),
      },
      {
        path: 'profile-optimizer',
        redirectTo: 'optimizer',
      },
      {
        path: 'cv-history',
        redirectTo: 'cv-manager',
      },
      {
        path: 'jobs',
        loadComponent: () =>
          import('./features/front-office/dashboard/jobs/jobs.component').then(
            (m) => m.JobsComponent
          ),
      },
      {
        path: 'post-job',
        loadComponent: () =>
          import('./features/front-office/dashboard/post-job/post-job.component').then(
            (m) => m.PostJobComponent
          ),
      },
      {
        path: 'profile',
        loadComponent: () =>
          import('./features/front-office/dashboard/profile/profile.component').then(
            (m) => m.ProfileComponent
          ),
      },
      {
        path: 'settings',
        loadComponent: () =>
          import('./features/front-office/dashboard/settings/settings.component').then(
            (m) => m.SettingsComponent
          ),
      },
      {
        path: 'assessments',
        loadComponent: () =>
          import('./features/front-office/assessments/assessment-hub.component').then(
            (m) => m.AssessmentHubComponent
          ),
      },
      {
        path: 'assessments/session/:sessionId',
        loadComponent: () =>
          import('./features/front-office/assessments/mcq-session.component').then((m) => m.McqSessionComponent),
      },
      {
        path: 'assessments/session/:sessionId/review',
        loadComponent: () =>
          import('./features/front-office/assessments/assessment-review.component').then(
            (m) => m.AssessmentReviewComponent
          ),
      },
      {
        path: 'interview/setup',
        loadComponent: () =>
          import('./features/front-office/dashboard/interview/setup/interview-setup.component').then(
            (m) => m.InterviewSetupComponent
          ),
      },
      {
        path: 'interview/history',
        loadComponent: () =>
          import('./features/front-office/dashboard/interview/history/interview-history.component').then(
            (m) => m.InterviewHistoryComponent
          ),
      },
      {
        path: 'interview/bookmarks',
        loadComponent: () =>
          import('./features/front-office/dashboard/interview/bookmarks/interview-bookmarks.component').then(
            (m) => m.InterviewBookmarksComponent
          ),
      },
      {
        path: 'interview/discover',
        loadComponent: () =>
          import('./features/front-office/dashboard/interview/discover/interview-discover.component').then(
            (m) => m.InterviewDiscoverComponent
          ),
      },
      {
        path: 'interview',
        pathMatch: 'full',
        loadComponent: () =>
          import('./features/front-office/dashboard/interview/interview.component').then(
            (m) => m.InterviewComponent
          ),
      },
      {
        path: 'interview/session/:id',
        loadComponent: () =>
          import('./features/front-office/dashboard/interview/session/interview-session.component').then(
            (m) => m.InterviewSessionComponent
          ),
      },
      {
        path: 'interview/session/:id/code',
        loadComponent: () =>
          import('./features/front-office/dashboard/interview/code-screen/interview-code-screen.component').then(
            (m) => m.InterviewCodeScreenComponent
          ),
      },
      {
        path: 'interview/session/:id/cloud',
        loadComponent: () =>
          import('./features/front-office/dashboard/interview/cloud-screen/interview-cloud-screen.component').then(
            (m) => m.InterviewCloudScreenComponent
          ),
      },
      {
        path: 'interview/session/:id/ml',
        loadComponent: () =>
          import('./features/front-office/dashboard/interview/ml-screen/interview-ml-screen.component').then(
            (m) => m.InterviewMlScreenComponent
          ),
      },
      {
        path: 'interview/report/:id',
        loadComponent: () =>
          import('./features/front-office/dashboard/interview/report/interview-report.component').then(
            (m) => m.InterviewReportComponent
          ),
      },
    ],
  },

  {
    path: 'admin',
    //canMatch: [adminCanMatch],
    canActivate: [authGuard, roleGuard],
    data: { requiredRoles: ['recruiter'] },
    loadComponent: () =>
      import('./features/back-office/admin/layout/admin-layout.component').then(
        (m) => m.AdminLayoutComponent
      ),
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./features/back-office/admin/dashboard/admin-dashboard.component').then(
            (m) => m.AdminDashboardComponent
          ),
      },
      {
        path: 'users',
        //canActivate: [roleGuard],
      // data: { requiredRoles: ['recruiter'] },
        loadComponent: () =>
          import('./features/back-office/admin/users/user-management.component').then(
            (m) => m.UserManagementComponent
          ),
      },
      {
        path: 'recruiters',
        loadComponent: () =>
          import('./features/back-office/admin/recruiters/recruiter-management.component').then(
            (m) => m.RecruiterManagementComponent
          ),
      },
      {
        path: 'jobs',
        loadComponent: () =>
          import('./features/back-office/admin/jobs/job-management.component').then(
            (m) => m.JobManagementComponent
          ),
      },
      {
        path: 'questions',
        loadComponent: () =>
          import('./features/back-office/admin/questions/question-management.component').then(
            (m) => m.QuestionManagementComponent
          ),
      },
      {
        path: 'skill-assessments',
        loadComponent: () =>
          import('./features/back-office/admin/skill-assessments/skill-assessment-admin.component').then(
            (m) => m.SkillAssessmentAdminComponent
          ),
      },
      {
        path: 'interview',
        loadComponent: () =>
          import('./admin/interview-dashboard/interview-dashboard.component').then(
            (m) => m.InterviewDashboardComponent
          ),
      },
      {
        path: 'ai-monitor',
        loadComponent: () =>
          import('./features/back-office/admin/ai-monitor/ai-monitor.component').then(
            (m) => m.AiMonitorComponent
          ),
      },
      {
        path: 'analytics',
        loadComponent: () =>
          import('./features/back-office/admin/analytics/analytics.component').then(
            (m) => m.AnalyticsComponent
          ),
      },
      {
        path: 'careers',
        loadComponent: () =>
          import('./features/back-office/admin/career-paths/career-paths.component').then(
            (m) => m.CareerPathsComponent
          ),
      },
      {
        path: 'health',
        loadComponent: () =>
          import('./features/back-office/admin/system-health/system-health.component').then(
            (m) => m.SystemHealthComponent
          ),
      },
      {
        path: 'settings',
        loadComponent: () =>
          import('./features/back-office/admin/settings/settings.component').then(
            (m) => m.SettingsComponent
          ),
      },
    ],
  },

  {
    path: 'access-denied',
    loadComponent: () =>
      import('./features/not-found/access-denied.component').then(
        (m) => m.AccessDeniedComponent
      ),
  },

  {
    path: '**',
    loadComponent: () =>
      import('./features/not-found/not-found.component').then(
        (m) => m.NotFoundComponent
      ),
  },
];
