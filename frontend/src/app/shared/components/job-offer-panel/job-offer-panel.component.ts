import { CommonModule } from '@angular/common';
import { Component, DestroyRef, EventEmitter, HostListener, Input, Output, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { CreateJobOfferRequest, JobOfferDto } from '../../../core/models/profile-optimizer.models';
import { ProfileOptimizerService } from '../../../core/services/profile-optimizer.service';
import { LUCIDE_ICONS } from '../../lucide-icons';
import { ToastService } from '../toast/toast.service';

@Component({
  selector: 'app-job-offer-panel',
  standalone: true,
  imports: [CommonModule, FormsModule, LUCIDE_ICONS],
  templateUrl: './job-offer-panel.component.html',
  styleUrl: './job-offer-panel.component.scss',
})
export class JobOfferPanelComponent {
  // NOTE: Existing codebase pattern uses centralized LUCIDE_ICONS instead of per-component icon imports.
  private readonly optimizerApi = inject(ProfileOptimizerService);
  private readonly toastService = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  @Input() open = false;
  @Output() closed = new EventEmitter<void>();
  @Output() saved = new EventEmitter<JobOfferDto>();

  jobTitle = '';
  company = '';
  sourceUrl = '';
  description = '';

  submitted = false;
  loading = false;
  errorMessage = '';

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.open) {
      this.close();
    }
  }

  close(): void {
    this.open = false;
    this.closed.emit();
    this.resetForm();
  }

  save(): void {
    this.submitted = true;
    this.errorMessage = '';

    if (!this.jobTitle.trim() || !this.description.trim()) {
      return;
    }

    const payload: CreateJobOfferRequest = {
      title: this.jobTitle.trim(),
      company: this.company.trim() || undefined,
      sourceUrl: this.sourceUrl.trim() || undefined,
      rawDescription: this.description.trim(),
    };

    this.loading = true;
    this.optimizerApi
      .createJobOffer(payload)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.loading = false;
        })
      )
      .subscribe({
        next: (jobOffer) => {
          this.saved.emit(jobOffer);
          this.toastService.show('success', 'Job offer saved · Keywords extracted');
          this.close();
        },
        error: (error: Error) => {
          this.errorMessage = error.message;
          this.toastService.show('error', 'Failed to save job offer');
        },
      });
  }

  titleError(): boolean {
    return this.submitted && !this.jobTitle.trim();
  }

  descriptionError(): boolean {
    return this.submitted && !this.description.trim();
  }

  descriptionChars(): number {
    return this.description.length;
  }

  private resetForm(): void {
    this.jobTitle = '';
    this.company = '';
    this.sourceUrl = '';
    this.description = '';
    this.errorMessage = '';
    this.submitted = false;
    this.loading = false;
  }
}
