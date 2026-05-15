import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CandidateCvDto } from '../models/profile-optimizer.models';
import { ProfileOptimizerService } from './profile-optimizer.service';

const ACTIVE_CV_STORAGE_KEY = 'po_active_cv_id';

@Injectable({
  providedIn: 'root',
})
export class ActiveCvService {
  private readonly optimizerApi = inject(ProfileOptimizerService);
  private readonly destroyRef = inject(DestroyRef);

  readonly activeCv = signal<CandidateCvDto | null>(null);

  setActiveCv(cv: CandidateCvDto): void {
    this.activeCv.set(cv);
    localStorage.setItem(ACTIVE_CV_STORAGE_KEY, cv.id);
  }

  loadActiveCv(): void {
    const activeCvId = localStorage.getItem(ACTIVE_CV_STORAGE_KEY);
    if (!activeCvId) {
      this.activeCv.set(null);
      return;
    }

    this.optimizerApi
      .getCvById(activeCvId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (cv) => {
          const current = this.activeCv();
          if (current && current.id !== cv.id) {
            return;
          }

          this.activeCv.set(cv);
        },
        error: () => {
          if (!this.activeCv()) {
            localStorage.removeItem(ACTIVE_CV_STORAGE_KEY);
          }
        },
      });
  }

  clearActiveCv(): void {
    this.activeCv.set(null);
    localStorage.removeItem(ACTIVE_CV_STORAGE_KEY);
  }

  isActive(cvId: string): boolean {
    return computed(() => this.activeCv()?.id === cvId)();
  }
}
