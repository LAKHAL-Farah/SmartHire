import { Component, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LUCIDE_ICONS } from '../../../../shared/lucide-icons';
import { HttpErrorResponse } from '@angular/common/http';
import { JobService, Job, JobApplication } from '../../../../services/job.service';


@Component({
  selector: 'app-jobs',
  standalone: true,
  imports: [CommonModule, FormsModule, LUCIDE_ICONS],
  templateUrl: './jobs.component.html',
  styleUrl: './jobs.component.scss'
})
export class JobsComponent implements OnInit {
  /* ── User's current skills (for match highlighting) ── */
  userSkills = ['TypeScript', 'Angular', 'Node.js', 'PostgreSQL', 'Docker', 'Python', 'React', 'AWS', 'Git', 'REST APIs'];

  /* ── Signals ── */
  selectedJobId = signal<number>(0);
  breakdownOpen = signal(false);
  sortOption = signal<'match' | 'recent' | 'salary'>('match');
  searchQuery = signal('');
  techQuery = signal('');

  /* ── Filter state ── */
  locationFilters = signal<string[]>([]);
  contractFilters = signal<string[]>([]);
  experienceFilters = signal<string[]>([]);
  salaryMin = signal(0);
  salaryMax = signal(200000);
  techFilters = signal<string[]>([]);

  /* ── Filter options ── */
  locationOptions = ['Remote', 'Hybrid', 'On-site'];
  contractOptions = ['Internship', 'Full-time', 'Part-time', 'Freelance'];
  experienceOptions = ['Junior', 'Mid', 'Senior'];

  allTechOptions = [
    'TypeScript', 'JavaScript', 'Python', 'Java', 'Go', 'Rust', 'C++',
    'Angular', 'React', 'Vue', 'Svelte', 'Next.js', 'Node.js', 'NestJS', 'Express',
    'PostgreSQL', 'MongoDB', 'Redis', 'MySQL', 'GraphQL', 'REST APIs',
    'Docker', 'Kubernetes', 'AWS', 'GCP', 'Azure', 'Terraform',
    'Git', 'CI/CD', 'Linux', 'Kafka', 'RabbitMQ',
  ];

  /* ── Match ring math ── */
  matchCircum = 2 * Math.PI * 16; // ≈ 100.53

  /* ── Job data ── */
  allJobs = signal<Job[]>([]);

  /* ── Apply modal state ── */
  applyModalOpen = signal(false);
  applyJobId = signal<number | null>(null);
  resumeFile = signal<File | null>(null);
  submitError = signal<string | null>(null);
  isSubmitting = signal(false);
  lastApplication = signal<JobApplication | null>(null);

  constructor(private jobService: JobService) {}
  ngOnInit(): void {
    this.loadJobs();
  }
  loadJobs(): void {
    this.jobService.getJobs().subscribe({
      next: (jobs) => {
        const safeJobs: Job[] = (jobs ?? []).map((j: any) => {
          const company = typeof j?.company === 'string' ? j.company : '';
          return {
            ...j,
            id: typeof j?.id === 'number' ? j.id : (Number(j?.id) || 0),
            title: typeof j?.title === 'string' ? j.title : '',
            company,
            locationType: typeof j?.locationType === 'string' ? j.locationType : '',
            contractType: typeof j?.contractType === 'string' ? j.contractType : '',
            experienceLevel: typeof j?.experienceLevel === 'string' ? j.experienceLevel : '',
            skills: Array.isArray(j?.skills) ? j.skills : [],
            description: typeof j?.description === 'string' ? j.description : '',
            postedDate: typeof j?.postedDate === 'string' ? j.postedDate : '',
            salaryRange: typeof j?.salaryRange === 'string' ? j.salaryRange : '',
            companyInitials: typeof j?.companyInitials === 'string'
              ? j.companyInitials
              : (company.trim() ? company.trim().slice(0, 2).toUpperCase() : 'NA'),
            companyColor: typeof j?.companyColor === 'string' ? j.companyColor : '#64748b',
            verified: typeof j?.verified === 'boolean' ? j.verified : false,
            saved: typeof j?.saved === 'boolean' ? j.saved : false,
            matchScore: typeof j?.matchScore === 'number' ? j.matchScore : 0,
          };
        });

        this.allJobs.set(safeJobs);

        if (safeJobs.length && !this.selectedJobId()) {
          this.selectedJobId.set(safeJobs[0].id);
        }
      },
      error: (err) => {
        console.error('Failed to load jobs:', err);
      }
    });
  }
  getTimeAgo(dateString: string): string {
  const posted = new Date(dateString);
  if (Number.isNaN(posted.getTime())) return '';
  const now = new Date();
  const diffMs = now.getTime() - posted.getTime();
  const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));

  if (diffDays === 0) return 'Today';
  if (diffDays === 1) return '1 day ago';
  if (diffDays < 7) return `${diffDays} days ago`;
  if (diffDays < 14) return '1 week ago';
  if (diffDays < 30) return `${Math.floor(diffDays / 7)} weeks ago`;
  if (diffDays < 60) return '1 month ago';
  if (diffDays < 365) return `${Math.floor(diffDays / 30)} months ago`;
  if (diffDays < 730) return '1 year ago';
  return `${Math.floor(diffDays / 365)} years ago`;
}


  /* ── Computed ── */
  filteredJobs = computed(() => {
    let jobs = [...this.allJobs()];

    // Search query
    const search = this.searchQuery().trim();
    if (search) {
      const q = search.toLowerCase();
      jobs = jobs.filter(j =>
        j.title.toLowerCase().includes(q) ||
        j.company.toLowerCase().includes(q) ||
        j.skills.some(s => s.toLowerCase().includes(q))
      );
    }

    // Location
    const locs = this.locationFilters();
    if (locs.length) jobs = jobs.filter(j => locs.includes(j.locationType));

    // Contract
    const ctrs = this.contractFilters();
    if (ctrs.length) jobs = jobs.filter(j => ctrs.includes(j.contractType));

    // Experience
    const exps = this.experienceFilters();
    if (exps.length) jobs = jobs.filter(j => exps.includes(j.experienceLevel));

    // Tech stack
    const techs = this.techFilters();
    if (techs.length) jobs = jobs.filter(j => techs.some(t => j.skills.includes(t)));

    // Sort
    if (this.sortOption() === 'match') {
      jobs.sort((a, b) => b.matchScore - a.matchScore);
    } else if (this.sortOption() === 'recent') {
      jobs.sort((a, b) => b.id - a.id);
    } else if (this.sortOption() === 'salary') {
      // Backend salaryRange is a string; keep stable ordering for now.
    }

    return jobs;
  });

  selectedJob = computed(() => {
    const jobs = this.allJobs();
    return jobs.find(j => j.id === this.selectedJobId()) ?? jobs[0];
  });

  techSuggestions = computed(() => {
    const q = this.techQuery().toLowerCase().trim();
    if (!q) return [];
    const active = this.techFilters();
    return this.allTechOptions
      .filter(t => t.toLowerCase().includes(q) && !active.includes(t))
      .slice(0, 6);
  });

  /* ── Methods ── */
  toggleFilter(type: 'location' | 'contract' | 'experience', value: string): void {
    const sigMap = {
      location: this.locationFilters,
      contract: this.contractFilters,
      experience: this.experienceFilters,
    };
    const sig = sigMap[type];
    const current = sig();
    if (current.includes(value)) {
      sig.set(current.filter(v => v !== value));
    } else {
      sig.set([...current, value]);
    }
  }

  onSalaryMinChange(val: number): void {
    if (val <= this.salaryMax()) this.salaryMin.set(val);
  }

  onSalaryMaxChange(val: number): void {
    if (val >= this.salaryMin()) this.salaryMax.set(val);
  }

  addTechTag(): void {
    const q = this.techQuery().trim();
    if (!q) return;
    const match = this.allTechOptions.find(t => t.toLowerCase() === q.toLowerCase());
    if (match && !this.techFilters().includes(match)) {
      this.techFilters.set([...this.techFilters(), match]);
    }
    this.techQuery.set('');
  }

  pickTechTag(tag: string): void {
    if (!this.techFilters().includes(tag)) {
      this.techFilters.set([...this.techFilters(), tag]);
    }
    this.techQuery.set('');
  }

  removeTechTag(tag: string): void {
    this.techFilters.set(this.techFilters().filter(t => t !== tag));
  }

  clearFilters(): void {
    this.searchQuery.set('');
    this.locationFilters.set([]);
    this.contractFilters.set([]);
    this.experienceFilters.set([]);
    this.salaryMin.set(0);
    this.salaryMax.set(200000);
    this.techFilters.set([]);
    this.techQuery.set('');
  }

  applySearch(): void {
    // Filters are reactive — this is a no-op trigger if needed
  }

  toggleSave(job: Job, event: Event): void {
    event.stopPropagation();
    job.saved = !job.saved;
  }

  openApplyModal(jobId: number): void {
    this.applyJobId.set(jobId);
    this.resumeFile.set(null);
    this.submitError.set(null);
    this.lastApplication.set(null);
    this.applyModalOpen.set(true);
  }

  closeApplyModal(): void {
    this.applyModalOpen.set(false);
    this.applyJobId.set(null);
    this.resumeFile.set(null);
    this.submitError.set(null);
    this.isSubmitting.set(false);
  }

  onResumeSelected(event: Event): void {
    const input = event.target as HTMLInputElement | null;
    const file = input?.files?.[0] ?? null;
    this.resumeFile.set(file);
    this.submitError.set(null);
  }

  submitApplication(): void {
    const jobId = this.applyJobId();
    const file = this.resumeFile();
    if (!jobId) {
      this.submitError.set('Missing job id. Please retry.');
      return;
    }
    if (!file) {
      this.submitError.set('Please upload your resume first.');
      return;
    }

    this.isSubmitting.set(true);
    this.submitError.set(null);
    this.lastApplication.set(null);

    this.jobService.applyToJob(jobId, file, 1).subscribe({
      next: (app) => {
        this.lastApplication.set(app);
        this.isSubmitting.set(false);
        // Keep modal open briefly to show success; user can close.
      },
      error: (err: unknown) => {
        this.isSubmitting.set(false);
        this.submitError.set(this.getApplyErrorMessage(err));
      }
    });
  }

  private getApplyErrorMessage(err: unknown): string {
    if (err instanceof HttpErrorResponse) {
      const backendError = (err.error && typeof err.error === 'object') ? (err.error as any).error : null;
      const message = typeof backendError === 'string' && backendError.trim() ? backendError : null;

      if (err.status === 409) {
        return message ?? 'You already applied to this job.';
      }
      if (err.status === 400) {
        return message ?? 'Invalid request. Please check your resume file.';
      }
      if (err.status === 0) {
        return 'Cannot reach the Job service. Is MS_JOB running on http://localhost:8085?';
      }
      return message ?? 'Failed to submit application.';
    }

    return 'Failed to submit application.';
  }

  getMatchedSkills(job: Job): string[] {
    return job.skills.filter(s => this.userSkills.includes(s));
  }

  getMissingSkills(job: Job): string[] {
    return job.skills.filter(s => !this.userSkills.includes(s));
  }
}
