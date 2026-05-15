import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, of, throwError } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { getProfileUserUuid, isLocalDemoMode, setLocalDemoMode } from './profile-user-id';

const LOCAL_PROFILE_KEY = 'smarthire_local_profile';

export interface ProfileApiResponse {
  userId: string;
  firstName?: string;
  lastName?: string;
  headline?: string;
  location?: string;
  githubUrl?: string;
  linkedinUrl?: string;
  avatarUrl?: string;
  email?: string;
  onboardingJson?: string | null;
  skillAssessmentJson?: string | null;
}

export interface SkillAssessmentSyncPayload {
  attempts: {
    sessionId: number;
    categoryTitle: string;
    categoryCode: string;
    scorePercent: number | null;
    completedAt: string | null;
    scoreReleased: boolean;
    adminFeedback: string | null;
  }[];
}

export interface UserApiResponse {
  id: string;
  email: string;
  status?: string;
  role?: { id?: string; name?: string };
}

export interface OnboardingCompletePayload {
  situation: string;
  careerPath: string;
  answers?: string[];
  skillScores?: Record<string, number>;
  developmentPlanNotes?: string;
}

@Injectable({ providedIn: 'root' })
export class ProfileApiService {
  private readonly base = environment.userApiUrl.replace(/\/$/, '');

  constructor(private http: HttpClient) {}

  getProfile(userId?: string): Observable<ProfileApiResponse> {
    const id = userId ?? getProfileUserUuid();
    if (isLocalDemoMode()) {
      return of(this.readLocalProfile(id));
    }
    return this.http.get<ProfileApiResponse>(`${this.base}/profiles/user/${id}`).pipe(
      catchError((err) => {
        if (environment.localAuthFallback && this.hasLocalProfileFor(id)) {
          return of(this.readLocalProfile(id));
        }
        return throwError(() => err);
      })
    );
  }

  completeOnboarding(body: OnboardingCompletePayload, userId?: string): Observable<ProfileApiResponse> {
    const id = userId ?? getProfileUserUuid();
    if (isLocalDemoMode()) {
      return of(this.persistLocalOnboarding(id, body));
    }
    return this.http
      .post<ProfileApiResponse>(`${this.base}/profiles/user/${id}/onboarding-complete`, body)
      .pipe(
        catchError((err) => {
          if (environment.localAuthFallback) {
            setLocalDemoMode(true);
            return of(this.persistLocalOnboarding(id, body));
          }
          return throwError(() => err);
        })
      );
  }

  getUserByEmail(email: string): Observable<UserApiResponse> {
    return this.http.get<UserApiResponse>(`${this.base}/users/email/${encodeURIComponent(email)}`);
  }

  /** Persists MCQ assessment snapshot on the profile (MS-User when available). */
  syncSkillAssessments(userId: string, body: SkillAssessmentSyncPayload): Observable<ProfileApiResponse> {
    const persistLocal = (): ProfileApiResponse => {
      const prev = this.readLocalProfile(userId);
      const merged: ProfileApiResponse = {
        ...prev,
        skillAssessmentJson: JSON.stringify({ attempts: body.attempts, syncedAt: new Date().toISOString() }),
      };
      this.writeLocalProfile(merged);
      return merged;
    };
    if (isLocalDemoMode()) {
      return of(persistLocal());
    }
    return this.http
      .post<ProfileApiResponse>(`${this.base}/profiles/user/${userId}/skill-assessments/sync`, body)
      .pipe(
        catchError((err) => {
          if (environment.localAuthFallback || err?.status === 404) {
            return of(persistLocal());
          }
          return throwError(() => err);
        })
      );
  }

  createUserWithProfile(body: {
    userRequest: any;
    profileRequest: any;
  }): Observable<any> {
    return this.http.post<any>(`${this.base}/users`, body);
  }

  private hasLocalProfileFor(id: string): boolean {
    const raw = localStorage.getItem(LOCAL_PROFILE_KEY);
    if (!raw) {
      return false;
    }
    try {
      return (JSON.parse(raw) as ProfileApiResponse).userId === id;
    } catch {
      return false;
    }
  }

  private readLocalProfile(id: string): ProfileApiResponse {
    const raw = localStorage.getItem(LOCAL_PROFILE_KEY);
    if (raw) {
      try {
        const p = JSON.parse(raw) as ProfileApiResponse;
        if (p.userId === id) {
          return p;
        }
      } catch {
        /* fall through */
      }
    }
    const u = localStorage.getItem('smarthire_local_user');
    let email = '';
    let firstName = 'Candidate';
    let lastName = '';
    if (u) {
      try {
        const j = JSON.parse(u) as { email?: string; firstName?: string; lastName?: string };
        email = j.email ?? '';
        firstName = j.firstName ?? firstName;
        lastName = j.lastName ?? lastName;
      } catch {
        /* ignore */
      }
    }
    return {
      userId: id,
      firstName,
      lastName,
      email,
      headline: '',
      onboardingJson: null,
    };
  }

  private writeLocalProfile(p: ProfileApiResponse): void {
    localStorage.setItem(LOCAL_PROFILE_KEY, JSON.stringify(p));
  }

  private persistLocalOnboarding(id: string, body: OnboardingCompletePayload): ProfileApiResponse {
    const prev = this.readLocalProfile(id);
    const snapshot = {
      situation: body.situation,
      careerPath: body.careerPath,
      answers: body.answers ?? [],
      skillScores: body.skillScores ?? {},
      preferencesOnly: !(body.answers && body.answers.length),
      developmentPlanNotes: body.developmentPlanNotes,
      completedAt: new Date().toISOString(),
    };
    const headline =
      prev.headline?.trim() ||
      [snapshot.situation, snapshot.careerPath].filter(Boolean).join(' · ') ||
      '';
    const merged: ProfileApiResponse = {
      ...prev,
      userId: id,
      onboardingJson: JSON.stringify(snapshot),
      headline,
    };
    this.writeLocalProfile(merged);
    return merged;
  }
}
