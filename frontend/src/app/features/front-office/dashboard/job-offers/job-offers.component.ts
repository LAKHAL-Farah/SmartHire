import { CommonModule } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { JobOfferDto } from '../../../../core/models/profile-optimizer.models';
import { ProfileOptimizerService } from '../../../../core/services/profile-optimizer.service';
import { LUCIDE_ICONS } from '../../../../shared/lucide-icons';
import { JobOfferPanelComponent } from '../../../../shared/components/job-offer-panel/job-offer-panel.component';
import { ToastComponent } from '../../../../shared/components/toast/toast.component';
import { ToastService } from '../../../../shared/components/toast/toast.service';

@Component({
  selector: 'app-job-offers',
  standalone: true,
  imports: [CommonModule, FormsModule, JobOfferPanelComponent, ToastComponent],
  templateUrl: './job-offers.component.html',
  styleUrl: './job-offers.component.scss',
})
export class JobOffersComponent {
  // NOTE: Existing codebase pattern uses centralized LUCIDE_ICONS instead of per-component icon imports.
  private readonly optimizerApi = inject(ProfileOptimizerService);
  private readonly toastService = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly router = inject(Router);

  readonly loading = signal(true);
  readonly search = signal('');
  readonly panelOpen = signal(false);
  readonly deletingId = signal<string | null>(null);
  readonly offers = signal<JobOfferDto[]>([]);

  readonly filteredOffers = computed(() => {
    const query = this.search().trim().toLowerCase();
    if (!query) {
      return this.offers();
    }

    return this.offers().filter((offer) => {
      const haystack = `${offer.title} ${offer.company || ''} ${offer.rawDescription}`.toLowerCase();
      return haystack.includes(query);
    });
  });

  readonly mainOffers = computed(() => this.filteredOffers().filter((_, index) => index % 3 !== 2));
  readonly runtimeOffers = computed(() => this.filteredOffers().filter((_, index) => index % 3 === 2));

  constructor() {
    this.loadOffers();
  }

  loadOffers(): void {
    this.loading.set(true);
    this.optimizerApi
      .listJobOffers()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (rows) => {
          this.offers.set(
                rows
              .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
          );
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
          this.toastService.show('error', 'Unable to load job offers');
        },
      });
  }

  openPanel(): void {
    this.panelOpen.set(true);
  }

  closePanel(): void {
    this.panelOpen.set(false);
  }

  onSaved(offer: JobOfferDto): void {
    this.offers.update((rows) => [offer, ...rows.filter((item) => item.id !== offer.id)]);
    this.panelOpen.set(false);
  }

  setSearch(value: string): void {
    this.search.set(value);
  }

  promptDelete(id: string): void {
    this.deletingId.set(id);
  }

  cancelDelete(): void {
    this.deletingId.set(null);
  }

  deleteOffer(id: string): void {
    this.optimizerApi
      .deleteJobOffer(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.offers.update((rows) => rows.filter((offer) => offer.id !== id));
          this.deletingId.set(null);
          this.toastService.show('success', 'Job offer deleted');
        },
        error: () => this.toastService.show('error', 'Failed to delete job offer'),
      });
  }

  goToOffer(offerId: string): void {
    void this.router.navigate(['/dashboard/job-offers', offerId]);
  }

  offerAgeDays(createdAt: string): number {
    const days = Math.floor((Date.now() - new Date(createdAt).getTime()) / 86400000);
    return Math.max(0, days);
  }

  keywords(offer: JobOfferDto): string[] {
    if (!offer.extractedKeywords) {
      return [];
    }

    try {
      const parsed = JSON.parse(offer.extractedKeywords) as unknown;
      return Array.isArray(parsed) ? parsed.map((entry) => String(entry)) : [];
    } catch {
      return [];
    }
  }

  themeClass(index: number): string {
    return `po-offer-theme--${index % 3}`;
  }

  previewDescription(text: string): string {
    const sanitized = text
      .replace(/LONG_DESCRIPTION_SEGMENT/gi, ' ')
      .replace(/\s+/g, ' ')
      .trim();

    if (sanitized.length <= 150) {
      return sanitized;
    }

    return `${sanitized.slice(0, 150).trimEnd()}...`;
  }


  
}