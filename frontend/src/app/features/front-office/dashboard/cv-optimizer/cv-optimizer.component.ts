import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LUCIDE_ICONS } from '../../../../shared/lucide-icons';
import { ProfileApiResponse, ProfileApiService } from '../../profile/profile-api.service';
import {
  CvVersionDto,
  ProfileOptimizationApiService,
  ProfileOptimizationSnapshot,
} from '../../profile/profile-optimization-api.service';

type CvTab = 'hub' | 'editor' | 'history';

@Component({
  selector: 'app-cv-optimizer',
  standalone: true,
  imports: [CommonModule, FormsModule, LUCIDE_ICONS],
  templateUrl: './cv-optimizer.component.html',
  styleUrl: './cv-optimizer.component.scss',
})
export class CvOptimizerComponent implements OnInit {
  private readonly profileApi = inject(ProfileApiService);
  private readonly optimizationApi = inject(ProfileOptimizationApiService);

  activeTab = signal<CvTab>('hub');
  selectedCvId = signal<string | null>(null);
  tabs: { id: CvTab; label: string }[] = [
    { id: 'hub', label: 'CV Hub' },
    { id: 'editor', label: 'Editor' },
    { id: 'history', label: 'Version History' },
  ];

  profile = signal<ProfileApiResponse | null>(null);
  snapshot = signal<ProfileOptimizationSnapshot | null>(null);
  loading = signal(true);
  busyUpload = signal(false);
  busyTailor = signal(false);
  error = signal<string | null>(null);
  notice = signal<string | null>(null);

  jobOfferTitle = 'Senior Full-stack Engineer';
  jobOfferCompany = 'Target company';
  jobOfferSourceUrl = '';
  jobOfferText = '';

  selectedCv = computed(() => {
    const data = this.snapshot();
    if (!data) {
      return null;
    }
    const selected = this.selectedCvId();
    if (selected) {
      return data.cvs.find((cv) => cv.id === selected) ?? null;
    }
    return data.cvs.find((cv) => cv.isActive) ?? data.cvs[0] ?? null;
  });

  versions = computed(() => {
    const data = this.snapshot();
    const cv = this.selectedCv();
    if (!data || !cv) {
      return [] as CvVersionDto[];
    }
    return data.versionsByCvId[cv.id] ?? [];
  });

  latestVersion = computed(() => this.versions()[0] ?? null);

  versionSections = computed(() => {
    const version = this.latestVersion();
    const sections = version?.tailoredContent?.['sections'];
    if (!Array.isArray(sections)) {
      return [] as { label: string; content: string }[];
    }
    return sections.map((section) => {
      const typed = section as { label?: string; content?: unknown };
      const content = Array.isArray(typed.content) ? typed.content.join(', ') : String(typed.content ?? '');
      return {
        label: typed.label ?? 'Section',
        content,
      };
    });
  });

  miniCircum = 2 * Math.PI * 20;

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.loading.set(true);
    this.error.set(null);
    this.profileApi.getProfile().subscribe({
      next: (profile) => {
        this.profile.set(profile);
        this.refreshSnapshot(profile);
      },
      error: () => {
        this.refreshSnapshot(null);
      },
    });
  }

  refreshSnapshot(profile: ProfileApiResponse | null): void {
    this.optimizationApi
      .getSnapshot(profile?.userId, {
        linkedinUrl: profile?.linkedinUrl,
        githubUrl: profile?.githubUrl,
      })
      .subscribe({
        next: (snapshot) => {
          this.snapshot.set(snapshot);
          this.loading.set(false);
          this.selectedCvId.set(this.selectedCvId() || snapshot.cvs[0]?.id || null);
          this.jobOfferTitle = snapshot.latestJobOffer?.title ?? this.jobOfferTitle;
          this.jobOfferCompany = snapshot.latestJobOffer?.company ?? this.jobOfferCompany;
          this.jobOfferSourceUrl = snapshot.latestJobOffer?.sourceUrl ?? this.jobOfferSourceUrl;
          this.jobOfferText = snapshot.latestJobOffer?.rawDescription ?? this.jobOfferText;
        },
        error: () => {
          this.loading.set(false);
          this.error.set('Could not load the CV Hub data. Check the M4 profile optimization service configuration.');
        },
      });
  }

  chooseCv(cvId: string): void {
    this.selectedCvId.set(cvId);
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement | null;
    const file = input?.files?.[0];
    if (!file) {
      return;
    }
    this.busyUpload.set(true);
    this.error.set(null);
    this.notice.set(null);
    this.optimizationApi.uploadCv(file, this.profile()?.userId).subscribe({
      next: (response) => {
        this.busyUpload.set(false);
        this.notice.set(`Uploaded ${response.cv.originalFileName}. Parsing and ATS preview are now available.`);
        this.selectedCvId.set(response.cv.id);
        this.refreshSnapshot(this.profile());
      },
      error: () => {
        this.busyUpload.set(false);
        this.error.set('CV upload failed. Verify that the M4 backend endpoint /cv/upload is available.');
      },
    });
    if (input) {
      input.value = '';
    }
  }

  tailorCv(): void {
    const cv = this.selectedCv();
    if (!cv || !this.jobOfferText.trim()) {
      this.error.set('Select a CV and provide a target job description before tailoring.');
      return;
    }
    this.busyTailor.set(true);
    this.error.set(null);
    this.notice.set(null);
    this.optimizationApi
      .tailorCv({
        cvId: cv.id,
        jobOfferTitle: this.jobOfferTitle,
        company: this.jobOfferCompany,
        sourceUrl: this.jobOfferSourceUrl || undefined,
        jobOfferText: this.jobOfferText,
      })
      .subscribe({
        next: () => {
          this.busyTailor.set(false);
          this.notice.set('A new tailored CV version was generated and added to version history.');
          this.activeTab.set('history');
          this.refreshSnapshot(this.profile());
        },
        error: () => {
          this.busyTailor.set(false);
          this.error.set('CV tailoring failed. Verify that the M4 backend endpoint /cv/tailor is available.');
        },
      });
  }

  scoreTone(score: number | null | undefined): 'high' | 'mid' | 'low' {
    const value = score ?? 0;
    if (value >= 80) {
      return 'high';
    }
    if (value >= 60) {
      return 'mid';
    }
    return 'low';
  }
}