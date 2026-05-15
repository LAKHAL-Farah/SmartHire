import { CommonModule } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { catchError, finalize, forkJoin, of, switchMap } from 'rxjs';
import {
  CertificateDto,
  MilestoneDto,
  RoadmapApiService,
} from '../../../../../services/roadmap-api.service';
import { resolveRoadmapUserId } from '../roadmap-user-context';

@Component({
  selector: 'app-milestones',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './milestones.component.html',
  styleUrl: './milestones.component.scss',
})
export class MilestonesComponent implements OnInit {
  private readonly roadmapApi = inject(RoadmapApiService);

  loading = signal(false);
  errorMessage = signal<string | null>(null);
  sharingCertificateId = signal<number | null>(null);

  roadmapId = signal<number | null>(null);
  userId = signal<number | null>(null);

  milestones = signal<MilestoneDto[]>([]);
  nextMilestone = signal<MilestoneDto | null>(null);
  certificates = signal<CertificateDto[]>([]);

  achievedCount = computed(
    () => this.milestones().filter((milestone) => !!milestone.reachedAt).length
  );

  pendingCount = computed(
    () => this.milestones().filter((milestone) => !milestone.reachedAt).length
  );

  ngOnInit(): void {
    this.userId.set(resolveRoadmapUserId());
    if (!this.userId()) {
      this.errorMessage.set('No authenticated user found. Please sign in again.');
      return;
    }
    this.loadMilestones();
  }

  isAchieved(milestone: MilestoneDto): boolean {
    return !!milestone.reachedAt;
  }

  openPdf(certificate: CertificateDto): void {
    const target =
      certificate.pdfUrl ||
      this.roadmapApi.getCertificatePdfUrl(certificate.certificateCode);
    window.open(target, '_blank', 'noopener');
  }

  openBadge(certificate: CertificateDto): void {
    const target =
      certificate.badgeUrl ||
      this.roadmapApi.getCertificateBadgeUrl(certificate.certificateCode);
    window.open(target, '_blank', 'noopener');
  }

  shareLinkedIn(certificate: CertificateDto): void {
    if (this.sharingCertificateId() === certificate.id) {
      return;
    }

    this.sharingCertificateId.set(certificate.id);
    this.roadmapApi
      .shareCertificateLinkedIn(certificate.id)
      .pipe(finalize(() => this.sharingCertificateId.set(null)))
      .subscribe({
        next: (updated) => {
          this.certificates.update((list) =>
            list.map((item) => (item.id === updated.id ? updated : item))
          );
        },
        error: () => {
          this.errorMessage.set('Could not update LinkedIn share status.');
        },
      });
  }

  trackMilestone(_index: number, milestone: MilestoneDto): number {
    return milestone.id;
  }

  trackCertificate(_index: number, certificate: CertificateDto): number {
    return certificate.id;
  }

  private loadMilestones(): void {
    const userId = this.userId();
    if (!userId) {
      this.errorMessage.set('No authenticated user found. Please sign in again.');
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    this.roadmapApi
      .getUserRoadmap(userId)
      .pipe(
        switchMap((roadmap) => {
          this.roadmapId.set(roadmap.id);
          return forkJoin({
            milestones: this.roadmapApi
              .getMilestones(roadmap.id)
              .pipe(catchError(() => of([]))),
            nextMilestone: this.roadmapApi
              .getNextMilestone(roadmap.id)
              .pipe(catchError(() => of(null))),
            certificates: this.roadmapApi
              .getUserCertificates(userId)
              .pipe(catchError(() => of([]))),
          });
        }),
        finalize(() => this.loading.set(false))
      )
      .subscribe({
        next: (payload) => {
          const sorted = payload.milestones
            .slice()
            .sort((a, b) => a.stepThreshold - b.stepThreshold);

          this.milestones.set(sorted);
          this.nextMilestone.set(payload.nextMilestone);
          this.certificates.set(payload.certificates);
        },
        error: () => {
          this.errorMessage.set('Unable to load milestones at this time.');
        },
      });
  }
}
