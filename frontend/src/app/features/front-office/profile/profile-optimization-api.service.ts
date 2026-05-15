import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, map, of, switchMap, tap, throwError } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { getProfileUserUuid, isLocalDemoMode } from './profile-user-id';

const LOCAL_M4_CACHE_KEY = 'smarthire_m4_profile_optimization';

export type ProcessingStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED';
export type FileFormat = 'PDF' | 'DOCX';
export type CVVersionType = 'ORIGINAL' | 'TAILORED' | 'GENERIC_OPTIMIZED';
export type ProfileType = 'CV' | 'LINKEDIN' | 'GITHUB';
export type TipPriority = 'HIGH' | 'MEDIUM' | 'LOW';

export interface CandidateCvDto {
  id: string;
  userId: string;
  originalFileUrl: string;
  originalFileName: string;
  fileFormat: FileFormat;
  parsedContent: Record<string, unknown> | null;
  parseStatus: ProcessingStatus;
  atsScore: number | null;
  isActive: boolean;
  uploadedAt: string;
}

export interface CvVersionDto {
  id: string;
  cvId: string;
  jobOfferId: string | null;
  versionType: CVVersionType;
  tailoredContent: Record<string, unknown>;
  atsScore: number | null;
  keywordMatchRate: number | null;
  diffSnapshot: {
    summary: string[];
    experience: string[];
    skills: string[];
  } | null;
  exportedFileUrl: string | null;
  generatedAt: string;
}

export interface JobOfferDto {
  id: string;
  userId: string;
  title: string;
  company: string | null;
  rawDescription: string;
  extractedKeywords: string[] | null;
  sourceUrl: string | null;
  createdAt: string;
}

export interface LinkedInProfileDto {
  id: string;
  userId: string;
  profileUrl: string;
  rawContent: string | null;
  scrapeStatus: ProcessingStatus;
  globalScore: number | null;
  sectionScores: Record<string, number> | null;
  currentHeadline: string | null;
  optimizedHeadline: string | null;
  currentSummary: string | null;
  optimizedSummary: string | null;
  optimizedSkills: string[] | null;
  analyzedAt: string | null;
}

export interface GitHubRepositoryDto {
  id: string;
  githubProfileId: string;
  repoName: string;
  repoUrl: string;
  language: string | null;
  stars: number;
  readmeScore: number | null;
  hasCiCd: boolean | null;
  hasTests: boolean | null;
  codeStructureScore: number | null;
  issues: string[] | null;
  overallScore: number | null;
}

export interface GitHubProfileDto {
  id: string;
  userId: string;
  githubUsername: string;
  overallScore: number | null;
  repoCount: number | null;
  topLanguages: { name: string; percent: number }[] | null;
  profileReadmeScore: number | null;
  feedback: string[] | null;
  auditStatus: ProcessingStatus;
  analyzedAt: string | null;
  repositories: GitHubRepositoryDto[];
}

export interface ProfileTipDto {
  id: string;
  userId: string;
  profileType: ProfileType;
  sourceEntityId: string | null;
  tipText: string;
  priority: TipPriority;
  isResolved: boolean;
  createdAt: string;
}

export interface HireReadinessScoreDto {
  userId: string;
  cvScore: number | null;
  linkedinScore: number | null;
  githubScore: number | null;
  globalScore: number | null;
  computedAt: string;
}

export interface CvScoreResponse {
  userId: string;
  atsScore: number;
  keywordMatchRate: number;
  matchedKeywords: string[];
  missingKeywords: string[];
  topGaps: string[];
}

export interface CvUploadResponse {
  cv: CandidateCvDto;
  latestVersion: CvVersionDto;
}

export interface CvTailorRequest {
  cvId: string;
  jobOfferText: string;
  jobOfferTitle?: string;
  company?: string;
  sourceUrl?: string;
}

export interface LinkedInAnalyzeRequest {
  userId?: string;
  profileUrl: string;
  targetRole?: string;
}

export interface ProfileOptimizationSnapshot {
  cvs: CandidateCvDto[];
  versionsByCvId: Record<string, CvVersionDto[]>;
  activeCvScore: CvScoreResponse;
  linkedin: LinkedInProfileDto;
  github: GitHubProfileDto;
  tips: ProfileTipDto[];
  readiness: HireReadinessScoreDto;
  latestJobOffer: JobOfferDto | null;
}

@Injectable({ providedIn: 'root' })
export class ProfileOptimizationApiService {
  private readonly base = environment.profileOptimizationApiUrl.replace(/\/$/, '');

  constructor(private http: HttpClient) {}

  getSnapshot(userId?: string, profile?: { linkedinUrl?: string | null; githubUrl?: string | null }): Observable<ProfileOptimizationSnapshot> {
    const id = userId ?? getProfileUserUuid();
    const demo = this.readLocalSnapshot(id, profile);
    if (isLocalDemoMode()) {
      return of(demo);
    }

    return this.getCvScore(id).pipe(
      map((score) => ({
        ...demo,
        activeCvScore: score,
        readiness: {
          ...demo.readiness,
          cvScore: score.atsScore,
          globalScore: this.computeGlobalScore(score.atsScore, demo.linkedin.globalScore, demo.github.overallScore),
          computedAt: new Date().toISOString(),
        },
      })),
      catchError(() => of(demo))
    );
  }

  getCvScore(userId?: string): Observable<CvScoreResponse> {
    const id = userId ?? getProfileUserUuid();
    if (isLocalDemoMode()) {
      return of(this.readLocalSnapshot(id).activeCvScore);
    }

    return this.http.get<CandidateCvDto[]>(`${this.base}/cv`).pipe(
      switchMap((cvs) => {
        const active = cvs.find((cv) => cv.isActive) ?? cvs[0];
        if (!active?.id) {
          if (environment.localAuthFallback) {
            return of(this.readLocalSnapshot(id).activeCvScore);
          }
          return throwError(() => new Error('No CV available to compute ATS score'));
        }
        return this.http.get<CvScoreResponse>(`${this.base}/cv/${encodeURIComponent(active.id)}/score`);
      }),
      catchError((err) => {
        if (environment.localAuthFallback) {
          return of(this.readLocalSnapshot(id).activeCvScore);
        }
        return throwError(() => err);
      })
    );
  }

  uploadCv(file: File, userId?: string): Observable<CvUploadResponse> {
    const id = userId ?? getProfileUserUuid();
    if (isLocalDemoMode()) {
      return of(this.persistUploadedCv(id, file));
    }
    const formData = new FormData();
    formData.append('file', file);
    formData.append('userId', id);
    return this.http.post<CvUploadResponse>(`${this.base}/cv/upload`, formData).pipe(
      tap((response) => this.cacheUploadedCv(response)),
      catchError((err) => {
        if (environment.localAuthFallback) {
          return of(this.persistUploadedCv(id, file));
        }
        return throwError(() => err);
      })
    );
  }

  tailorCv(body: CvTailorRequest): Observable<CvVersionDto> {
    if (isLocalDemoMode()) {
      return of(this.persistTailoredCv(body));
    }
    return this.http.post<CvVersionDto>(`${this.base}/cv/tailor`, body).pipe(
      tap((version) => this.cacheVersion(version)),
      catchError((err) => {
        if (environment.localAuthFallback) {
          return of(this.persistTailoredCv(body));
        }
        return throwError(() => err);
      })
    );
  }

  analyzeLinkedIn(body: LinkedInAnalyzeRequest): Observable<LinkedInProfileDto> {
    const id = body.userId ?? getProfileUserUuid();
    if (isLocalDemoMode()) {
      return of(this.persistLinkedInAudit(id, body.profileUrl, body.targetRole));
    }
    const payload = {
      rawContent: `LinkedIn profile source: ${body.profileUrl}\nTarget role: ${body.targetRole ?? 'Not specified'}`,
      currentHeadline: body.targetRole ?? null,
      currentSummary: `Candidate target role: ${body.targetRole ?? 'Software Engineer'}`,
      currentSkills: 'Angular, TypeScript, Java, Spring Boot, REST APIs',
    };
    return this.http.post<LinkedInProfileDto>(`${this.base}/linkedin/analyze`, payload).pipe(
      tap((profile) => this.cacheLinkedIn(profile)),
      catchError((err) => {
        if (environment.localAuthFallback) {
          return of(this.persistLinkedInAudit(id, body.profileUrl, body.targetRole));
        }
        return throwError(() => err);
      })
    );
  }

  auditGithub(username: string, userId?: string): Observable<GitHubProfileDto> {
    const id = userId ?? getProfileUserUuid();
    if (isLocalDemoMode()) {
      return of(this.persistGithubAudit(id, username));
    }
    const githubProfileUrl = username.startsWith('http')
      ? username
      : `https://github.com/${encodeURIComponent(username)}`;
    return this.http.post<GitHubProfileDto>(`${this.base}/github/audit`, { githubProfileUrl }).pipe(
      tap((profile) => this.cacheGithub(profile)),
      catchError((err) => {
        if (environment.localAuthFallback) {
          return of(this.persistGithubAudit(id, username));
        }
        return throwError(() => err);
      })
    );
  }

  getProfileTips(userId?: string): Observable<ProfileTipDto[]> {
    const id = userId ?? getProfileUserUuid();
    if (isLocalDemoMode()) {
      return of(this.readLocalSnapshot(id).tips);
    }
    return this.http.get<ProfileTipDto[]>(`${this.base}/profile-tips/${id}`).pipe(
      catchError((err) => {
        if (environment.localAuthFallback) {
          return of(this.readLocalSnapshot(id).tips);
        }
        return throwError(() => err);
      })
    );
  }

  private cacheUploadedCv(response: CvUploadResponse): void {
    const snapshot = this.readLocalSnapshot(response.cv.userId);
    snapshot.cvs = [response.cv, ...snapshot.cvs.filter((cv) => cv.id !== response.cv.id)];
    snapshot.versionsByCvId[response.cv.id] = [response.latestVersion];
    this.writeLocalSnapshot(snapshot);
  }

  private cacheVersion(version: CvVersionDto): void {
    const snapshot = this.readLocalSnapshot(getProfileUserUuid());
    const existing = snapshot.versionsByCvId[version.cvId] ?? [];
    snapshot.versionsByCvId[version.cvId] = [version, ...existing.filter((item) => item.id !== version.id)];
    if (version.atsScore != null) {
      snapshot.activeCvScore = {
        ...snapshot.activeCvScore,
        atsScore: version.atsScore,
        keywordMatchRate: version.keywordMatchRate ?? snapshot.activeCvScore.keywordMatchRate,
      };
      snapshot.readiness.cvScore = version.atsScore;
      snapshot.readiness.globalScore = this.computeGlobalScore(
        version.atsScore,
        snapshot.linkedin.globalScore,
        snapshot.github.overallScore
      );
    }
    this.writeLocalSnapshot(snapshot);
  }

  private cacheLinkedIn(profile: LinkedInProfileDto): void {
    const snapshot = this.readLocalSnapshot(profile.userId);
    snapshot.linkedin = profile;
    snapshot.readiness.linkedinScore = profile.globalScore;
    snapshot.readiness.globalScore = this.computeGlobalScore(
      snapshot.readiness.cvScore,
      profile.globalScore,
      snapshot.github.overallScore
    );
    this.writeLocalSnapshot(snapshot);
  }

  private cacheGithub(profile: GitHubProfileDto): void {
    const snapshot = this.readLocalSnapshot(profile.userId);
    snapshot.github = profile;
    snapshot.readiness.githubScore = profile.overallScore;
    snapshot.readiness.globalScore = this.computeGlobalScore(
      snapshot.readiness.cvScore,
      snapshot.linkedin.globalScore,
      profile.overallScore
    );
    this.writeLocalSnapshot(snapshot);
  }

  private persistUploadedCv(userId: string, file: File): CvUploadResponse {
    const snapshot = this.readLocalSnapshot(userId);
    const cvId = this.createId('cv');
    const now = new Date().toISOString();
    const format = file.name.toLowerCase().endsWith('.docx') ? 'DOCX' : 'PDF';
    const cv: CandidateCvDto = {
      id: cvId,
      userId,
      originalFileUrl: '#',
      originalFileName: file.name,
      fileFormat: format,
      parsedContent: {
        summary: 'Imported candidate summary ready for ATS optimization.',
        skills: ['Angular', 'TypeScript', 'Java', 'Spring Boot', 'GitHub Actions'],
      },
      parseStatus: 'COMPLETED',
      atsScore: 71,
      isActive: true,
      uploadedAt: now,
    };
    const version: CvVersionDto = {
      id: this.createId('cv-version'),
      cvId,
      jobOfferId: null,
      versionType: 'ORIGINAL',
      tailoredContent: {
        summary: 'Imported version ready for analysis.',
        sections: [
          { label: 'Summary', content: 'Full-stack engineer with strong Java and Angular delivery experience.' },
          { label: 'Experience', content: 'Built API-first hiring workflows and candidate dashboards.' },
        ],
      },
      atsScore: 71,
      keywordMatchRate: 63,
      diffSnapshot: null,
      exportedFileUrl: null,
      generatedAt: now,
    };
    snapshot.cvs = [cv, ...snapshot.cvs.map((item) => ({ ...item, isActive: false }))];
    snapshot.versionsByCvId[cvId] = [version];
    snapshot.activeCvScore = {
      ...snapshot.activeCvScore,
      atsScore: 71,
      keywordMatchRate: 63,
      matchedKeywords: ['Angular', 'REST APIs', 'CI/CD', 'Testing'],
      missingKeywords: ['Spring Security', 'Microservices', 'ATS optimization'],
      topGaps: ['Add quantified delivery outcomes.', 'Mention Java/Spring ownership earlier in the summary.'],
    };
    snapshot.readiness.cvScore = 71;
    snapshot.readiness.globalScore = this.computeGlobalScore(71, snapshot.linkedin.globalScore, snapshot.github.overallScore);
    snapshot.readiness.computedAt = now;
    this.writeLocalSnapshot(snapshot);
    return { cv, latestVersion: version };
  }

  private persistTailoredCv(body: CvTailorRequest): CvVersionDto {
    const snapshot = this.readLocalSnapshot(getProfileUserUuid());
    const keywords = this.extractKeywords(body.jobOfferText);
    const version: CvVersionDto = {
      id: this.createId('tailored'),
      cvId: body.cvId,
      jobOfferId: this.createId('job'),
      versionType: 'TAILORED',
      tailoredContent: {
        summary: `Targeted for ${body.jobOfferTitle ?? 'the selected role'} with emphasis on ${keywords.slice(0, 4).join(', ')}.`,
        sections: [
          {
            label: 'Summary',
            content: `Results-focused engineer aligned to ${body.company ?? 'the target company'}, highlighting ${keywords.slice(0, 5).join(', ')}.`,
          },
          {
            label: 'Experience',
            content: 'Reordered bullets to foreground ownership, metrics, and job-specific tooling.',
          },
          {
            label: 'Skills',
            content: keywords.slice(0, 8),
          },
        ],
      },
      atsScore: 88,
      keywordMatchRate: 84,
      diffSnapshot: {
        summary: ['Added target role keywords', 'Moved quantified impact into opening paragraph'],
        experience: ['Rewrote bullets with metrics', 'Surfaced platform and CI/CD ownership'],
        skills: ['Expanded stack with job-specific phrases'],
      },
      exportedFileUrl: '#',
      generatedAt: new Date().toISOString(),
    };
    const existing = snapshot.versionsByCvId[body.cvId] ?? [];
    snapshot.versionsByCvId[body.cvId] = [version, ...existing];
    snapshot.activeCvScore = {
      userId: snapshot.readiness.userId,
      atsScore: 88,
      keywordMatchRate: 84,
      matchedKeywords: keywords.slice(0, 8),
      missingKeywords: keywords.slice(8, 11),
      topGaps: ['Add one leadership example in recent experience.', 'Mirror the role naming used in the target job ad.'],
    };
    snapshot.latestJobOffer = {
      id: version.jobOfferId!,
      userId: snapshot.readiness.userId,
      title: body.jobOfferTitle ?? 'Target role',
      company: body.company ?? null,
      rawDescription: body.jobOfferText,
      extractedKeywords: keywords,
      sourceUrl: body.sourceUrl ?? null,
      createdAt: version.generatedAt,
    };
    snapshot.readiness.cvScore = 88;
    snapshot.readiness.globalScore = this.computeGlobalScore(88, snapshot.linkedin.globalScore, snapshot.github.overallScore);
    snapshot.readiness.computedAt = version.generatedAt;
    snapshot.tips = snapshot.tips.map((tip) =>
      tip.profileType === 'CV' ? { ...tip, isResolved: tip.priority !== 'HIGH' } : tip
    );
    this.writeLocalSnapshot(snapshot);
    return version;
  }

  private persistLinkedInAudit(userId: string, profileUrl: string, targetRole?: string): LinkedInProfileDto {
    const snapshot = this.readLocalSnapshot(userId);
    const role = targetRole?.trim() || 'Senior Software Engineer';
    const profile: LinkedInProfileDto = {
      ...snapshot.linkedin,
      profileUrl,
      scrapeStatus: 'COMPLETED',
      globalScore: 84,
      sectionScores: {
        headline: 91,
        summary: 82,
        skills: 79,
        recommendations: 68,
      },
      optimizedHeadline: `${role} | Angular, Spring Boot, ATS-ready product delivery`,
      optimizedSummary:
        'I build candidate-facing products and internal hiring platforms with a focus on clean architecture, measurable business outcomes, and strong collaboration across product and engineering.',
      optimizedSkills: ['Angular', 'TypeScript', 'Java', 'Spring Boot', 'REST APIs', 'GitHub Actions', 'ATS Optimization'],
      analyzedAt: new Date().toISOString(),
    };
    snapshot.linkedin = profile;
    snapshot.readiness.linkedinScore = 84;
    snapshot.readiness.globalScore = this.computeGlobalScore(snapshot.readiness.cvScore, 84, snapshot.github.overallScore);
    snapshot.readiness.computedAt = profile.analyzedAt!;
    this.writeLocalSnapshot(snapshot);
    return profile;
  }

  private persistGithubAudit(userId: string, username: string): GitHubProfileDto {
    const snapshot = this.readLocalSnapshot(userId);
    const profile: GitHubProfileDto = {
      ...snapshot.github,
      githubUsername: username,
      overallScore: 76,
      repoCount: 9,
      profileReadmeScore: 81,
      topLanguages: [
        { name: 'TypeScript', percent: 44 },
        { name: 'Java', percent: 26 },
        { name: 'SCSS', percent: 17 },
        { name: 'Python', percent: 13 },
      ],
      feedback: [
        'Add screenshots and architecture notes to the top two repositories.',
        'Expose CI badges and test commands in each README.',
        'Document deployment choices so recruiters can assess ownership quickly.',
      ],
      auditStatus: 'COMPLETED',
      analyzedAt: new Date().toISOString(),
      repositories: snapshot.github.repositories.map((repo, index) => ({
        ...repo,
        repoName: index === 0 ? 'smart-hire-platform' : repo.repoName,
      })),
    };
    snapshot.github = profile;
    snapshot.readiness.githubScore = 76;
    snapshot.readiness.globalScore = this.computeGlobalScore(snapshot.readiness.cvScore, snapshot.linkedin.globalScore, 76);
    snapshot.readiness.computedAt = profile.analyzedAt!;
    this.writeLocalSnapshot(snapshot);
    return profile;
  }

  private readLocalSnapshot(
    userId: string,
    profile?: { linkedinUrl?: string | null; githubUrl?: string | null }
  ): ProfileOptimizationSnapshot {
    const raw = localStorage.getItem(LOCAL_M4_CACHE_KEY);
    if (raw) {
      try {
        const parsed = JSON.parse(raw) as Record<string, ProfileOptimizationSnapshot>;
        const cached = parsed[userId];
        if (cached) {
          return {
            ...cached,
            linkedin: {
              ...cached.linkedin,
              profileUrl: profile?.linkedinUrl || cached.linkedin.profileUrl,
            },
            github: {
              ...cached.github,
              githubUsername: this.extractGithubUsername(profile?.githubUrl) || cached.github.githubUsername,
            },
          };
        }
      } catch {
        /* ignore malformed cache */
      }
    }
    const seed = this.createSeedSnapshot(userId, profile);
    this.writeLocalSnapshot(seed);
    return seed;
  }

  private writeLocalSnapshot(snapshot: ProfileOptimizationSnapshot): void {
    const raw = localStorage.getItem(LOCAL_M4_CACHE_KEY);
    let cache: Record<string, ProfileOptimizationSnapshot> = {};
    if (raw) {
      try {
        cache = JSON.parse(raw) as Record<string, ProfileOptimizationSnapshot>;
      } catch {
        cache = {};
      }
    }
    cache[snapshot.readiness.userId] = snapshot;
    localStorage.setItem(LOCAL_M4_CACHE_KEY, JSON.stringify(cache));
  }

  private createSeedSnapshot(
    userId: string,
    profile?: { linkedinUrl?: string | null; githubUrl?: string | null }
  ): ProfileOptimizationSnapshot {
    const cvId = this.createId('seed-cv');
    const now = new Date().toISOString();
    const githubUsername = this.extractGithubUsername(profile?.githubUrl) || 'candidate-dev';
    return {
      cvs: [
        {
          id: cvId,
          userId,
          originalFileUrl: '#',
          originalFileName: 'Ahmed_Fullstack_Resume.pdf',
          fileFormat: 'PDF',
          parsedContent: {
            summary: 'Full-stack engineer with Angular, Java, and product delivery experience.',
            skills: ['Angular', 'TypeScript', 'Java', 'Spring Boot', 'MySQL', 'Docker'],
          },
          parseStatus: 'COMPLETED',
          atsScore: 78,
          isActive: true,
          uploadedAt: now,
        },
      ],
      versionsByCvId: {
        [cvId]: [
          {
            id: this.createId('seed-version'),
            cvId,
            jobOfferId: null,
            versionType: 'GENERIC_OPTIMIZED',
            tailoredContent: {
              summary: 'ATS-optimized CV emphasizing measurable impact and platform ownership.',
              sections: [
                { label: 'Summary', content: 'Delivered hiring products across Angular and Spring Boot stacks.' },
                { label: 'Skills', content: ['Angular', 'Java', 'REST APIs', 'CI/CD', 'MySQL'] },
              ],
            },
            atsScore: 78,
            keywordMatchRate: 73,
            diffSnapshot: {
              summary: ['Added product and platform language'],
              experience: ['Inserted quantified impact bullets'],
              skills: ['Moved core stack into the first line'],
            },
            exportedFileUrl: '#',
            generatedAt: now,
          },
        ],
      },
      activeCvScore: {
        userId,
        atsScore: 78,
        keywordMatchRate: 73,
        matchedKeywords: ['Angular', 'Java', 'Spring Boot', 'REST APIs', 'MySQL', 'Docker'],
        missingKeywords: ['Microservices', 'GitHub Actions', 'Recruiting platform'],
        topGaps: [
          'Lead with target-role keywords in the opening summary.',
          'Add more quantified outcomes for recent work.',
          'Mirror CI/CD and testing terminology from the job offer.',
        ],
      },
      linkedin: {
        id: this.createId('linkedin'),
        userId,
        profileUrl: profile?.linkedinUrl || 'https://www.linkedin.com/in/candidate',
        rawContent: null,
        scrapeStatus: 'COMPLETED',
        globalScore: 81,
        sectionScores: {
          headline: 86,
          summary: 78,
          skills: 74,
          recommendations: 63,
        },
        currentHeadline: 'Full-stack developer building hiring products',
        optimizedHeadline: 'Full-stack Engineer | Angular + Java | Building AI-assisted hiring products',
        currentSummary:
          'Developer with experience across frontend and backend systems, focused on useful products and clean code.',
        optimizedSummary:
          'Full-stack engineer with delivery experience across Angular, Java, and hiring workflows. I focus on product clarity, measurable platform impact, and fast iteration with quality guardrails.',
        optimizedSkills: ['Angular', 'TypeScript', 'Java', 'Spring Boot', 'REST APIs', 'GitHub Actions'],
        analyzedAt: now,
      },
      github: {
        id: this.createId('github'),
        userId,
        githubUsername,
        overallScore: 72,
        repoCount: 6,
        topLanguages: [
          { name: 'TypeScript', percent: 48 },
          { name: 'Java', percent: 28 },
          { name: 'SCSS', percent: 14 },
          { name: 'Python', percent: 10 },
        ],
        profileReadmeScore: 69,
        feedback: [
          'Strengthen README narratives with screenshots and setup steps.',
          'Expose test commands and CI status prominently.',
          'Add issue labels or project boards to show maintenance quality.',
        ],
        auditStatus: 'COMPLETED',
        analyzedAt: now,
        repositories: [
          {
            id: this.createId('repo'),
            githubProfileId: 'github',
            repoName: 'smart-hire-frontend',
            repoUrl: `https://github.com/${githubUsername}/smart-hire-frontend`,
            language: 'TypeScript',
            stars: 12,
            readmeScore: 74,
            hasCiCd: true,
            hasTests: true,
            codeStructureScore: 79,
            issues: ['README lacks architecture section', 'Add demo screenshots'],
            overallScore: 81,
          },
          {
            id: this.createId('repo'),
            githubProfileId: 'github',
            repoName: 'candidate-scoring-api',
            repoUrl: `https://github.com/${githubUsername}/candidate-scoring-api`,
            language: 'Java',
            stars: 7,
            readmeScore: 67,
            hasCiCd: true,
            hasTests: false,
            codeStructureScore: 71,
            issues: ['Missing test coverage badge', 'No API example requests'],
            overallScore: 68,
          },
          {
            id: this.createId('repo'),
            githubProfileId: 'github',
            repoName: 'job-board-scraper',
            repoUrl: `https://github.com/${githubUsername}/job-board-scraper`,
            language: 'Python',
            stars: 4,
            readmeScore: 58,
            hasCiCd: false,
            hasTests: false,
            codeStructureScore: 61,
            issues: ['No CI workflow', 'Repository structure needs setup guide'],
            overallScore: 54,
          },
        ],
      },
      tips: [
        {
          id: this.createId('tip'),
          userId,
          profileType: 'CV',
          sourceEntityId: cvId,
          tipText: 'Add one quantified impact bullet above the fold to improve ATS and recruiter scanning.',
          priority: 'HIGH',
          isResolved: false,
          createdAt: now,
        },
        {
          id: this.createId('tip'),
          userId,
          profileType: 'LINKEDIN',
          sourceEntityId: null,
          tipText: 'Rewrite the headline to combine target role, stack, and business domain in one line.',
          priority: 'MEDIUM',
          isResolved: false,
          createdAt: now,
        },
        {
          id: this.createId('tip'),
          userId,
          profileType: 'GITHUB',
          sourceEntityId: null,
          tipText: 'Your top repositories need stronger README storytelling and proof of testing.',
          priority: 'HIGH',
          isResolved: false,
          createdAt: now,
        },
      ],
      readiness: {
        userId,
        cvScore: 78,
        linkedinScore: 81,
        githubScore: 72,
        globalScore: this.computeGlobalScore(78, 81, 72),
        computedAt: now,
      },
      latestJobOffer: {
        id: this.createId('job-offer'),
        userId,
        title: 'Full-stack Engineer',
        company: 'SmartHire',
        rawDescription:
          'Looking for an Angular and Spring Boot engineer who can ship candidate-facing products, improve ATS compatibility, and collaborate across AI-assisted hiring workflows.',
        extractedKeywords: ['Angular', 'Spring Boot', 'ATS', 'Candidate Experience', 'CI/CD', 'REST APIs'],
        sourceUrl: null,
        createdAt: now,
      },
    };
  }

  private computeGlobalScore(
    cvScore: number | null | undefined,
    linkedinScore: number | null | undefined,
    githubScore: number | null | undefined
  ): number {
    const cv = cvScore ?? 0;
    const linkedin = linkedinScore ?? 0;
    const github = githubScore ?? 0;
    return Math.round(cv * 0.4 + linkedin * 0.35 + github * 0.25);
  }

  private extractKeywords(text: string): string[] {
    const matches = text.match(/[A-Za-z][A-Za-z+/.-]{2,}/g) ?? [];
    const unique = new Set<string>();
    for (const word of matches) {
      const normalized = word.toLowerCase();
      if (['with', 'that', 'this', 'from', 'your', 'have', 'will', 'role', 'team'].includes(normalized)) {
        continue;
      }
      unique.add(word);
      if (unique.size >= 12) {
        break;
      }
    }
    return [...unique];
  }

  private extractGithubUsername(url?: string | null): string {
    if (!url) {
      return '';
    }
    const match = url.match(/github\.com\/([^/?#]+)/i);
    return match?.[1] ?? '';
  }

  private createId(prefix: string): string {
    const random = Math.random().toString(36).slice(2, 10);
    return `${prefix}-${random}`;
  }
}