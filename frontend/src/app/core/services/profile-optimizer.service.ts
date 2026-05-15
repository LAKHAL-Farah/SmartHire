import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { resolveCurrentProfileUserId } from './current-user-id';
import {
  AuditGitHubRequest,
  AtsScoreDto,
  CandidateCvDto,
  CreateJobOfferRequest,
  GitHubProfileDto,
  GitHubRepoDto,
  LinkedInProfileDto,
  ProfileTipDto,
  CvVersionDto,
  JobOfferDto,
} from '../models/profile-optimizer.models';

@Injectable({
  providedIn: 'root',
})
export class ProfileOptimizerService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = 'http://localhost:8092';
  private readonly githubBaseUrl = 'http://localhost:8092';

  private resolveUserId(): string {
    const userId = resolveCurrentProfileUserId();
    if (!userId) {
      throw new Error('Missing user session. Please log in again.');
    }
    return userId;
  }

  // ─── CV ───────────────────────────────────────────

  /** Endpoint: POST http://localhost:8092/api/cv/upload */
  uploadCv(file: File, userId?: string): Observable<CandidateCvDto> {
    const formData = new FormData();
    let resolvedUserId = userId?.trim();
    if (!resolvedUserId) {
      try {
        resolvedUserId = this.resolveUserId();
      } catch (error) {
        return throwError(() => (error instanceof Error ? error : new Error('Missing user session. Please log in again.')));
      }
    }

    formData.append('file', file);
    formData.append('userId', resolvedUserId);
    return this.http.post<CandidateCvDto>(`${this.baseUrl}/api/cv/upload`, formData).pipe(
      catchError(() => this.toUserError('Unable to upload CV right now. Please try again.'))
    );
  }

  /** Endpoint: GET http://localhost:8092/api/cv */
  listCvs(): Observable<CandidateCvDto[]> {
    return this.http.get<CandidateCvDto[]>(`${this.baseUrl}/api/cv`).pipe(
      catchError(() => this.toUserError('Unable to load CVs right now.'))
    );
  }

  /** Endpoint: GET http://localhost:8092/api/cv */
  getAllCvs(): Observable<CandidateCvDto[]> {
    return this.listCvs();
  }

  /** Endpoint: GET http://localhost:8092/api/cv/{cvId} */
  getCvById(cvId: string): Observable<CandidateCvDto> {
    return this.http.get<CandidateCvDto>(`${this.baseUrl}/api/cv/${encodeURIComponent(cvId)}`).pipe(
      catchError(() => this.toUserError('Unable to load the selected CV.'))
    );
  }

  /** Endpoint: GET http://localhost:8092/api/cv/{cvId}/score */
  getCvScore(cvId: string): Observable<AtsScoreDto> {
    return this.http.get<AtsScoreDto>(`${this.baseUrl}/api/cv/${encodeURIComponent(cvId)}/score`).pipe(
      catchError(() => this.toUserError('Unable to compute ATS score for this CV.'))
    );
  }

  /** Endpoint: POST http://localhost:8092/api/cv/{cvId}/tailor */
  tailorCv(cvId: string, jobOfferId: string): Observable<CvVersionDto> {
    return this.http
      .post<CvVersionDto>(`${this.baseUrl}/api/cv/${encodeURIComponent(cvId)}/tailor`, {
        jobOfferId,
      })
      .pipe(catchError(() => this.toUserError('Unable to tailor CV right now. Please try again.')));
  }

  /** Endpoint: POST http://localhost:8092/api/cv/{cvId}/optimize */
  optimizeCv(cvId: string): Observable<CvVersionDto> {
    return this.http
      .post<CvVersionDto>(`${this.baseUrl}/api/cv/${encodeURIComponent(cvId)}/optimize`, {})
      .pipe(catchError(() => this.toUserError('Unable to optimize CV right now. Please try again.')));
  }

  /** Endpoint: GET http://localhost:8092/api/cv/{cvId}/versions */
  getCvVersions(cvId: string): Observable<CvVersionDto[]> {
    return this.http.get<CvVersionDto[]>(`${this.baseUrl}/api/cv/${encodeURIComponent(cvId)}/versions`).pipe(
      catchError(() => this.toUserError('Unable to load CV versions right now.'))
    );
  }

  /**
   * NOTE: This endpoint is exposed by the backend legacy CVVersion controller.
   * Endpoint: GET http://localhost:8092/api/v1/cv-versions/job-offer/{jobOfferId}
   */
  getCvVersionsForJobOffer(jobOfferId: string): Observable<CvVersionDto[]> {
    return this.http
      .get<CvVersionDto[]>(`${this.baseUrl}/api/v1/cv-versions/job-offer/${encodeURIComponent(jobOfferId)}`)
      .pipe(catchError(() => this.toUserError('Unable to load CV versions for this job offer right now.')));
  }

  /** Endpoint: GET http://localhost:8092/api/cv/versions/{versionId} */
  getCvVersionById(versionId: string): Observable<CvVersionDto> {
    return this.http
      .get<CvVersionDto>(`${this.baseUrl}/api/cv/versions/${encodeURIComponent(versionId)}`)
      .pipe(catchError(() => this.toUserError('Unable to load this CV version.')));
  }

  /** Endpoint: GET http://localhost:8092/api/cv/versions/{versionId}/export */
  exportCvVersionPdf(versionId: string): Observable<Blob> {
    return this.http
      .get(`${this.baseUrl}/api/cv/versions/${encodeURIComponent(versionId)}/export`, {
        responseType: 'blob',
      })
      .pipe(catchError(() => this.toUserError('Unable to export PDF right now.')));
  }

  /** Endpoint: DELETE http://localhost:8092/api/cv/{cvId} */
  deleteCv(cvId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/api/cv/${encodeURIComponent(cvId)}`).pipe(
      catchError(() => this.toUserError('Unable to delete this CV right now.'))
    );
  }

  // ─── TIPS ─────────────────────────────────────────

  /** Endpoint: GET http://localhost:8092/api/v1/profile-tips */
  getTips(type?: 'CV' | 'LINKEDIN' | 'GITHUB'): Observable<ProfileTipDto[]> {
    return this.http.get<ProfileTipDto[]>(`${this.baseUrl}/api/v1/profile-tips`).pipe(
      catchError(() => this.toUserError('Unable to load profile tips right now.')),
      map((tips) => (type ? tips.filter((tip) => tip.profileType === type) : tips))
    );
  }

  /** Endpoint: PATCH http://localhost:8092/api/tips/{tipId}/resolve */
  resolveTip(tipId: string): Observable<void> {
    return this.http.patch<void>(`${this.baseUrl}/api/tips/${encodeURIComponent(tipId)}/resolve`, {}).pipe(
      catchError(() => this.toUserError('Unable to resolve this tip right now.'))
    );
  }

  // ─── LINKEDIN ───────────────────────────────────

  /** Endpoint: POST http://localhost:8092/api/linkedin/analyze */
  analyzeLinkedIn(payload: {
    rawContent: string;
    currentHeadline?: string;
    currentSummary?: string;
    currentSkills?: string;
  }): Observable<LinkedInProfileDto> {
    return this.http
      .post<LinkedInProfileDto>(`${this.baseUrl}/api/linkedin/analyze`, payload)
      .pipe(catchError(() => this.toUserError('Unable to analyze LinkedIn profile right now.')));
  }

  /** Endpoint: GET http://localhost:8092/api/linkedin */
  getLinkedInProfile(): Observable<LinkedInProfileDto> {
    return this.http.get<LinkedInProfileDto>(`${this.baseUrl}/api/linkedin`);
  }

  /** Endpoint: POST http://localhost:8092/api/linkedin/align */
  alignLinkedInToJob(jobOfferId: string): Observable<LinkedInProfileDto> {
    return this.http
      .post<LinkedInProfileDto>(`${this.baseUrl}/api/linkedin/align`, { jobOfferId })
      .pipe(catchError(() => this.toUserError('Unable to align LinkedIn profile right now.')));
  }

  /** Endpoint: POST http://localhost:8092/api/github-legacy/audit */
  auditGitHub(request: AuditGitHubRequest): Observable<GitHubProfileDto> {
    const githubUsername = this.extractGithubUsername(request.githubProfileUrl);
    return this.http
      .post<GitHubProfileDto>(`${this.githubBaseUrl}/api/github-legacy/audit`, {
        githubUsername,
      })
      .pipe(catchError(() => this.toUserError('Unable to audit GitHub profile right now.')));
  }

  /** Endpoint: GET http://localhost:8092/api/github-legacy */
  getGitHubProfile(): Observable<GitHubProfileDto> {
    return this.http.get<GitHubProfileDto>(`${this.githubBaseUrl}/api/github-legacy`);
  }

  /** Endpoint: POST http://localhost:8092/api/github-legacy/reaudit */
  reauditGitHub(request: AuditGitHubRequest): Observable<GitHubProfileDto> {
    return this.http
      .post<GitHubProfileDto>(`${this.githubBaseUrl}/api/github-legacy/reaudit`, {})
      .pipe(catchError(() => this.toUserError('Unable to re-audit GitHub profile right now.')));
  }

  /** Endpoint: GET http://localhost:8092/api/github-legacy/repositories */
  getGitHubRepositories(): Observable<GitHubRepoDto[]> {
    return this.http.get<GitHubRepoDto[]>(`${this.githubBaseUrl}/api/github-legacy/repositories`);
  }

  private extractGithubUsername(githubProfileUrl: string): string {
    const value = githubProfileUrl.trim();
    if (!value) {
      return '';
    }

    const cleaned = value.replace(/^https?:\/\/(www\.)?github\.com\//i, '').replace(/^@/, '');
    return cleaned.split(/[/?#]/)[0].trim();
  }

  // ─── JOB OFFERS ───────────────────────────────────

  /** Endpoint: POST http://localhost:8092/api/job-offers */
  createJobOffer(body: CreateJobOfferRequest): Observable<JobOfferDto> {
    let resolvedUserId: string;
    try {
      resolvedUserId = this.resolveUserId();
    } catch (error) {
      return throwError(() => (error instanceof Error ? error : new Error('Missing user session. Please log in again.')));
    }

    const params = new HttpParams().set('userId', resolvedUserId);
    return this.http.post<JobOfferDto>(`${this.baseUrl}/api/job-offers`, body, { params }).pipe(
      map((offer) => ({ ...offer, userId: offer.userId ?? resolvedUserId })),
      catchError(() => this.toUserError('Unable to save this job offer right now.'))
    );
  }

  /** Endpoint: GET http://localhost:8092/api/job-offers */
  listJobOffers(): Observable<JobOfferDto[]> {
    let resolvedUserId: string;
    try {
      resolvedUserId = this.resolveUserId();
    } catch (error) {
      return throwError(() => (error instanceof Error ? error : new Error('Missing user session. Please log in again.')));
    }

    const params = new HttpParams().set('userId', resolvedUserId);
    return this.http.get<JobOfferDto[]>(`${this.baseUrl}/api/job-offers`, { params }).pipe(
      catchError(() => this.toUserError('Unable to load job offers right now.')),
      map((offers) => offers.map((offer) => ({ ...offer, userId: offer.userId ?? resolvedUserId })))
    );
  }

  /** Endpoint: GET http://localhost:8092/api/job-offers/{id} */
  getJobOfferById(id: string): Observable<JobOfferDto> {
    let resolvedUserId: string;
    try {
      resolvedUserId = this.resolveUserId();
    } catch (error) {
      return throwError(() => (error instanceof Error ? error : new Error('Missing user session. Please log in again.')));
    }

    const params = new HttpParams().set('userId', resolvedUserId);
    return this.http.get<JobOfferDto>(`${this.baseUrl}/api/job-offers/${encodeURIComponent(id)}`, { params }).pipe(
      catchError(() => this.toUserError('Unable to load this job offer.')),
      map((offer) => ({ ...offer, userId: offer.userId ?? resolvedUserId }))
    );
  }

  /** Endpoint: DELETE http://localhost:8092/api/job-offers/{id} */
  deleteJobOffer(id: string): Observable<void> {
    let resolvedUserId: string;
    try {
      resolvedUserId = this.resolveUserId();
    } catch (error) {
      return throwError(() => (error instanceof Error ? error : new Error('Missing user session. Please log in again.')));
    }

    const params = new HttpParams().set('userId', resolvedUserId);
    return this.http.delete<void>(`${this.baseUrl}/api/job-offers/${encodeURIComponent(id)}`, { params }).pipe(
      catchError(() => this.toUserError('Unable to delete this job offer right now.'))
    );
  }

  private toUserError(message: string): Observable<never> {
    return throwError(() => new Error(message));
  }
}
