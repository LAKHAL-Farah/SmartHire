import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { finalize } from 'rxjs/operators';

import { LUCIDE_ICONS } from '../../../../shared/lucide-icons';
import { JobCreateDto, JobService } from '../../../../services/job.service';

@Component({
  selector: 'app-post-job',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, LUCIDE_ICONS],
  templateUrl: './post-job.component.html',
  styleUrl: './post-job.component.scss',
})
export class PostJobComponent {
  private fb = inject(FormBuilder);

  isSubmitting = signal(false);
  submitError = signal<string | null>(null);

  skillInput = '';
  skills = signal<string[]>([]);

  locationOptions = ['Remote', 'Hybrid', 'On-site'];
  contractOptions = ['Internship', 'Full-time', 'Part-time', 'Freelance'];
  experienceOptions = ['Junior', 'Mid', 'Senior'];

  form = this.fb.group({
    title: ['', [Validators.required, Validators.maxLength(255)]],
    company: ['TechCorp', [Validators.required, Validators.maxLength(255)]],
    companyInitials: ['TC', [Validators.maxLength(10)]],
    companyColor: ['#1DB954', [Validators.maxLength(7)]],
    verified: [false],
    locationType: ['Remote', [Validators.required]],
    contractType: ['Full-time', [Validators.required]],
    experienceLevel: ['Mid', [Validators.required]],
    salaryRange: [''],
    description: ['', [Validators.required]],
  });

  constructor(
    private jobService: JobService,
    private router: Router
  ) {}

  addSkill(): void {
    const raw = (this.skillInput ?? '').trim();
    if (!raw) return;

    const existing = this.skills();
    const already = existing.some((s) => s.toLowerCase() === raw.toLowerCase());
    if (!already) {
      this.skills.set([...existing, raw]);
    }
    this.skillInput = '';
  }

  removeSkill(skill: string): void {
    this.skills.set(this.skills().filter((s) => s !== skill));
  }

  submit(): void {
    this.submitError.set(null);

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const v = this.form.getRawValue();

    const payload: JobCreateDto = {
      title: v.title ?? '',
      company: v.company ?? '',
      locationType: v.locationType ?? '',
      contractType: v.contractType ?? '',
      experienceLevel: v.experienceLevel ?? '',
      description: v.description ?? '',
      companyInitials: v.companyInitials || undefined,
      companyColor: v.companyColor || undefined,
      verified: v.verified ?? false,
      salaryRange: v.salaryRange || undefined,
      skills: this.skills().length ? this.skills() : undefined,
      // userId intentionally omitted until MS-User integration exists.
    };

    this.isSubmitting.set(true);

    this.jobService
      .createJob(payload)
      .pipe(finalize(() => this.isSubmitting.set(false)))
      .subscribe({
        next: () => {
          this.router.navigateByUrl('/dashboard/jobs');
        },
        error: (err) => {
          const msg =
            err?.error?.message ||
            err?.error?.error ||
            err?.message ||
            'Failed to post job. Please try again.';
          this.submitError.set(String(msg));
        },
      });
  }

  cancel(): void {
    this.router.navigateByUrl('/dashboard/jobs');
  }

  fieldInvalid(name: keyof typeof this.form.controls): boolean {
    const ctrl = this.form.get(name);
    return !!ctrl && ctrl.invalid && (ctrl.touched || ctrl.dirty);
  }
}
