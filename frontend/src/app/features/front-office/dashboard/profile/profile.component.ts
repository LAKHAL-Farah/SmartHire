import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule, NgTemplateOutlet } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { LUCIDE_ICONS } from '../../../../shared/lucide-icons';
import {
  ProfileApiService,
  ProfileApiResponse,
} from '../../profile/profile-api.service';
import {
  CandidateSessionApiService,
  isSessionCompleted,
  isSessionPublished,
  SessionResponseDto,
} from '../../assessments/candidate-session-api.service';
import { CandidateAssignmentApiService } from '../../assessments/candidate-assignment-api.service';
import { collectCandidateUserIdsForSessions } from '../../assessments/assessment-canonical-user';
import { getAssessmentUserId } from '../../profile/profile-user-id';

type ProfileTab = 'overview' | 'experience' | 'projects' | 'assessments';

interface OnboardingSnapshot {
  situation?: string;
  careerPath?: string;
  answers?: string[];
  skillScores?: Record<string, number>;
  preferencesOnly?: boolean;
  developmentPlanNotes?: string;
  completedAt?: string;
}

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, NgTemplateOutlet, FormsModule, RouterLink, LUCIDE_ICONS],
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.scss',
})
export class ProfileComponent implements OnInit {
  private readonly profileApi = inject(ProfileApiService);
  private readonly sessionApi = inject(CandidateSessionApiService);
  private readonly assignmentApi = inject(CandidateAssignmentApiService);

  activeTab = signal<ProfileTab>('overview');
  tabs: { id: ProfileTab; label: string }[] = [
    { id: 'overview', label: 'Overview' },
    { id: 'experience', label: 'Experience' },
    { id: 'projects', label: 'Projects' },
    { id: 'assessments', label: 'Assessments' },
  ];

  apiProfile = signal<ProfileApiResponse | null>(null);
  profileLoading = signal(true);
  profileError = signal<string | null>(null);
  onboardingSnap = signal<OnboardingSnapshot | null>(null);
  /** Live from MS-Assessment — drives skill bars & readiness */
  assessmentSessions = signal<SessionResponseDto[]>([]);
  /** Optional aggregated profile for template compatibility (not loaded in this branch). */
  skillProfile = signal<any | null>(null);

  ringCircum = 2 * Math.PI * 42;

  badges = [
    { name: 'Early Adopter', icon: '🚀' },
    { name: 'Code Streak 30', icon: '🔥' },
    { name: 'Quiz Master', icon: '🧠' },
    { name: 'Profile Complete', icon: '✅' },
    { name: 'Open Source', icon: '💎' },
    { name: 'Team Player', icon: '🤝' },
  ];

  languages = [
    { name: 'TypeScript', pct: 42, color: '#3178c6' },
    { name: 'Python', pct: 22, color: '#3572A5' },
    { name: 'JavaScript', pct: 18, color: '#f1e05a' },
    { name: 'SCSS', pct: 10, color: '#c6538c' },
    { name: 'Go', pct: 8, color: '#00ADD8' },
  ];

  topRepos = [
    { name: 'smarthire-platform', stars: 234, lang: 'TypeScript', color: '#3178c6' },
    { name: 'devsync-editor', stars: 189, lang: 'TypeScript', color: '#3178c6' },
    { name: 'ml-pipeline-utils', stars: 97, lang: 'Python', color: '#3572A5' },
    { name: 'infra-terraform', stars: 54, lang: 'Go', color: '#00ADD8' },
  ];

  linkedinScores = [
    { section: 'Headline', score: 90 },
    { section: 'Summary', score: 78 },
    { section: 'Experience', score: 85 },
    { section: 'Skills', score: 65 },
    { section: 'Education', score: 92 },
  ];

  experiences = [
    {
      company: 'TechCorp',
      role: 'Software Engineer',
      dateRange: 'Mar 2023 – Present',
      bullets: [
        'Architected microservices processing 50k+ daily requests',
        'Led monolith-to-event-driven migration reducing latency 60%',
        'Reduced CI/CD pipeline time by 40% via parallel test execution',
      ],
    },
    {
      company: 'StartupXYZ',
      role: 'Junior Developer',
      dateRange: 'Jun 2021 – Feb 2023',
      bullets: [
        'Built RESTful APIs and React dashboards for internal tools',
        'Increased code coverage from 30% to 85% with automated tests',
        'Implemented OAuth2 login flow serving 12k+ users',
      ],
    },
    {
      company: 'University of Technology',
      role: 'Teaching Assistant — CS301',
      dateRange: 'Sep 2020 – May 2021',
      bullets: [
        'Led labs for Distributed Systems course (40 students)',
        'Authored automated grading scripts in Python',
      ],
    },
  ];

  projects = [
    { name: 'SmartHire Platform', techStack: ['Angular', 'NestJS', 'PostgreSQL', 'Docker'], aiScore: 88 },
    { name: 'DevSync Editor', techStack: ['TypeScript', 'WebSockets', 'OT', 'Redis'], aiScore: 82 },
    { name: 'ML Pipeline Utils', techStack: ['Python', 'PyTorch', 'FastAPI', 'Docker'], aiScore: 71 },
    { name: 'Infra Terraform Modules', techStack: ['Terraform', 'AWS', 'Go'], aiScore: 64 },
    { name: 'CLI Task Runner', techStack: ['Go', 'Cobra', 'SQLite'], aiScore: 52 },
    { name: 'Portfolio Site', techStack: ['Next.js', 'Tailwind', 'Vercel'], aiScore: 45 },
  ];

  radarAxes = [
    { label: 'Frontend', value: 85 },
    { label: 'Backend', value: 72 },
    { label: 'DevOps', value: 58 },
    { label: 'Algorithms', value: 65 },
    { label: 'Databases', value: 75 },
    { label: 'Soft Skills', value: 80 },
  ];

  scoreHistory = [
    { date: 'Feb 20, 2026', score: 72 },
    { date: 'Jan 15, 2026', score: 65 },
    { date: 'Dec 10, 2025', score: 58 },
    { date: 'Nov 5, 2025', score: 52 },
    { date: 'Oct 1, 2025', score: 44 },
  ];

  displayName = computed(() => {
    const p = this.apiProfile();
    if (p?.firstName || p?.lastName) {
      return [p.firstName, p.lastName].filter(Boolean).join(' ').trim();
    }
    // Fallback to userName stored by login
    const userName = localStorage.getItem('userName')?.trim();
    if (userName) return userName;
    return 'Your profile';
  });

  displayInitials = computed(() => {
    const p = this.apiProfile();
    const f = p?.firstName?.charAt(0) ?? '';
    const l = p?.lastName?.charAt(0) ?? '';
    const s = `${f}${l}`.toUpperCase();
    if (s) return s;
    // Fallback to userName initials
    const userName = localStorage.getItem('userName')?.trim() ?? '';
    if (userName) {
      const parts = userName.split(/\s+/);
      const fi = parts[0]?.charAt(0) ?? '';
      const li = parts.length > 1 ? parts[parts.length - 1].charAt(0) : '';
      return `${fi}${li}`.toUpperCase() || '?';
    }
    return '?';
  });

  displayHeadline = computed(() => {
    const p = this.apiProfile();
    if (p?.headline?.trim()) return p.headline;
    return 'Complete onboarding to personalize your headline';
  });

  displayLocation = computed(() => this.apiProfile()?.location?.trim() ?? '');

  skillGroupsForSidebar = computed(() => {
    const sessions = this.assessmentSessions().filter(
      (s) => isSessionCompleted(s) && isSessionPublished(s) && s.scorePercent != null
    );
    if (sessions.length === 0) {
      return [];
    }
    const bestByTitle = new Map<string, number>();
    for (const s of sessions) {
      const prev = bestByTitle.get(s.categoryTitle) ?? 0;
      bestByTitle.set(s.categoryTitle, Math.max(prev, s.scorePercent ?? 0));
    }
    const skills = [...bestByTitle.entries()].map(([name, level]) => ({ name, level }));
    return [{ category: 'Skill assessments', skills }];
  });

  /** 0–100 from published assessments; null until at least one published score exists */
  readinessPct = computed((): number | null => {
    const sessions = this.assessmentSessions().filter(
      (s) => isSessionCompleted(s) && isSessionPublished(s) && s.scorePercent != null
    );
    if (sessions.length === 0) {
      return null;
    }
    const sum = sessions.reduce((a, s) => a + (s.scorePercent ?? 0), 0);
    return Math.round(sum / sessions.length);
  });

  /** Ring fill 0–100; use 0 when readiness is unknown */
  readinessRingFill = computed(() => this.readinessPct() ?? 0);

  /** Best score per category for the bar chart */
  skillBarRows = computed(() => {
    const sessions = this.assessmentSessions().filter(
      (s) => isSessionCompleted(s) && isSessionPublished(s) && s.scorePercent != null
    );
    const best = new Map<string, number>();
    for (const s of sessions) {
      const prev = best.get(s.categoryTitle) ?? 0;
      best.set(s.categoryTitle, Math.max(prev, s.scorePercent ?? 0));
    }
    return [...best.entries()].map(([categoryTitle, score]) => ({ categoryTitle, score }));
  });

  developmentPlanText = computed(() => {
    const n = this.onboardingSnap()?.developmentPlanNotes?.trim();
    if (n) return n;
    return '';
  });

  /** Published scores available — drives Overview assessment blocks */
  hasPublishedAssessmentData = computed(() => this.skillBarRows().length > 0);

  /** Completed attempts waiting for admin publish */
  pendingPublishCount = computed(() =>
    this.assessmentSessions().filter(
      (s) => isSessionCompleted(s) && !isSessionPublished(s)
    ).length
  );

  /** At least one open (not submitted) attempt */
  hasInProgressAssessment = computed(() =>
    this.assessmentSessions().some((s) => !isSessionCompleted(s))
  );

  /** Radar grid rings (25% … 100%) */
  readonly radarGridScales = [0.25, 0.5, 0.75, 1] as const;

  /** ≥3 categories — radar is readable */
  showSkillRadar = computed(() => this.skillBarRows().length >= 3);

  skillRadarPolygon = computed((): string => {
    const rows = this.skillBarRows();
    const n = rows.length;
    if (n < 3) return '';
    const cx = 100,
      cy = 100,
      r = 70;
    const angleStep = (2 * Math.PI) / n;
    const scores = rows.map((x: { score: number }) => x.score / 100);
    return scores
      .map((s: number, i: number) => {
        const angle = angleStep * i - Math.PI / 2;
        const x = cx + r * s * Math.cos(angle);
        const y = cy + r * s * Math.sin(angle);
        return `${x},${y}`;
      })
      .join(' ');
  });

  skillRadarAxisPoints = computed(
    (): {
      label: string;
      fullLabel: string;
      x: number;
      y: number;
      lx: number;
      ly: number;
    }[] => {
      const rows = this.skillBarRows();
      const n = rows.length;
      if (n < 3) return [];
      const cx = 100,
        cy = 100,
        r = 70;
      const angleStep = (2 * Math.PI) / n;
      return rows.map((row: { categoryTitle: string }, i: number) => {
        const angle = angleStep * i - Math.PI / 2;
        const short =
          row.categoryTitle.length > 12
            ? row.categoryTitle.slice(0, 11) + '…'
            : row.categoryTitle;
        return {
          label: short,
          fullLabel: row.categoryTitle,
          x: cx + r * Math.cos(angle),
          y: cy + r * Math.sin(angle),
          lx: cx + (r + 18) * Math.cos(angle),
          ly: cy + (r + 18) * Math.sin(angle),
        };
      });
    }
  );

  /** Vertical bar chart (works for any number of categories) */
  skillVerticalBars = computed(() => {
    const rows = this.skillBarRows();
    const n = rows.length;
    if (n === 0) return [];
    const bottom = 112;
    const maxH = 88;
    const w = 400;
    const pad = 20;
    const inner = w - pad * 2;
    const gap = Math.min(12, inner / Math.max(n * 6, 12));
    const barW = (inner - gap * (n - 1)) / n;
    let x = pad;
    return rows.map((row: { categoryTitle: string; score: number }) => {
      const h = (row.score / 100) * maxH;
      const y = bottom - h;
      const short =
        row.categoryTitle.length > 10 ? row.categoryTitle.slice(0, 9) + '…' : row.categoryTitle;
      const b = { x, y, w: barW, h, score: row.score, label: short, full: row.categoryTitle };
      x += barW + gap;
      return b;
    });
  });

  getSkillRadarGridPolygon(scale: number): string {
    const rows = this.skillBarRows();
    const n = rows.length;
    if (n < 3) return '';
    const cx = 100,
      cy = 100,
      r = 70;
    const angleStep = (2 * Math.PI) / n;
    const pts: string[] = [];
    for (let i = 0; i < n; i++) {
      const angle = angleStep * i - Math.PI / 2;
      pts.push(`${cx + r * scale * Math.cos(angle)},${cy + r * scale * Math.sin(angle)}`);
    }
    return pts.join(' ');
  }

  ngOnInit(): void {
    this.loadProfile();
  }

  loadProfile(): void {
    this.profileLoading.set(true);
    this.profileError.set(null);
    const baseUid = getAssessmentUserId();

    this.profileApi
      .getProfile()
      .pipe(
        switchMap((profile) => {
          if (!baseUid) {
            return of({
              profile,
              sessions: [] as SessionResponseDto[],
            });
          }
          return this.assignmentApi.getStatus(baseUid).pipe(
            catchError((err: unknown) => {
              if (err instanceof HttpErrorResponse && err.status === 404) {
                return of(null);
              }
              return throwError(() => err);
            }),
            switchMap((plan) => {
              const ids = collectCandidateUserIdsForSessions(plan, baseUid);
              return this.sessionApi
                .listForUserMergedDistinct(ids)
                .pipe(catchError(() => of([] as SessionResponseDto[])), switchMap((sessions) => of({ profile, sessions })));
            })
          );
        })
      )
      .subscribe({
        next: ({ profile, sessions }) => {
          this.apiProfile.set(profile);
          this.onboardingSnap.set(this.parseOnboarding(profile.onboardingJson));
          this.assessmentSessions.set(sessions);
          this.profileLoading.set(false);
          if (baseUid && sessions.length > 0) {
            const attempts = sessions
              .filter((s) => isSessionCompleted(s))
              .map((s) => ({
                sessionId: s.id,
                categoryTitle: s.categoryTitle,
                categoryCode: s.categoryCode ?? '',
                scorePercent: s.scorePercent,
                completedAt: s.completedAt,
                scoreReleased: s.scoreReleased,
                adminFeedback: s.adminFeedback ?? null,
              }));
            this.profileApi.syncSkillAssessments(baseUid, { attempts }).subscribe({ error: () => {} });
          }
        },
        error: () => {
          this.profileLoading.set(false);
          this.profileError.set(
            'Could not load your profile from MS-User. Ensure the service is running (port 8082) and you are logged in.'
          );
        },
      });
  }

  sessionCompleted(s: SessionResponseDto): boolean {
    return isSessionCompleted(s);
  }

  sessionPublished(s: SessionResponseDto): boolean {
    return isSessionPublished(s);
  }

  private parseOnboarding(raw: string | null | undefined): OnboardingSnapshot | null {
    if (!raw?.trim()) return null;
    try {
      return JSON.parse(raw) as OnboardingSnapshot;
    } catch {
      return null;
    }
  }
}
