import { HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { resolveCurrentInterviewUserId, resolveCurrentProfileUserId } from '../services/current-user-id';

function isMsProfileRequest(url: string): boolean {
  try {
    const parsed = new URL(url, window.location.origin);
    return (
      parsed.port === '8092' ||
      parsed.pathname.startsWith('/api/cv') ||
      parsed.pathname.startsWith('/api/job-offers') ||
      parsed.pathname.startsWith('/api/linkedin') ||
      parsed.pathname.startsWith('/api/github') ||
      parsed.pathname.startsWith('/api/tips') ||
      parsed.pathname.startsWith('/api/v1/profile-tips')
    );
  } catch {
    return false;
  }
}

function isInterviewRequest(url: string): boolean {
  try {
    const parsed = new URL(url, window.location.origin);
    return (
      parsed.port === '8081' ||
      parsed.pathname.startsWith('/interview-service/api/v1') ||
      parsed.pathname.startsWith('/api/v1/sessions') ||
      parsed.pathname.startsWith('/api/v1/reports') ||
      parsed.pathname.startsWith('/api/v1/streaks') ||
      parsed.pathname.startsWith('/api/v1/bookmarks') ||
      parsed.pathname.startsWith('/api/v1/questions') ||
      parsed.pathname.startsWith('/api/v1/answers') ||
      parsed.pathname.startsWith('/api/v1/evaluations') ||
      parsed.pathname.startsWith('/api/v1/pressure') ||
      parsed.pathname.startsWith('/api/v1/diagrams') ||
      parsed.pathname.startsWith('/api/v1/ml-answers')
    );
  } catch {
    return false;
  }
}

function isRoadmapRequest(url: string): boolean {
  try {
    const parsed = new URL(url, window.location.origin);
    return (
      parsed.port === '8083' ||
      parsed.pathname.startsWith('/msroadmap/api') ||
      parsed.pathname.startsWith('/api/roadmaps') ||
      parsed.pathname.startsWith('/api/roadmap-steps') ||
      parsed.pathname.startsWith('/api/user-roadmaps') ||
      parsed.pathname.startsWith('/api/projects') ||
      parsed.pathname.startsWith('/api/notifications') ||
      parsed.pathname.startsWith('/api/jobs') ||
      parsed.pathname.startsWith('/api/interview') ||
      parsed.pathname.startsWith('/api/certificates') ||
      parsed.pathname.startsWith('/api/streaks')
    );
  } catch {
    return false;
  }
}

function isAuthRequest(url: string): boolean {
  try {
    const parsed = new URL(url, window.location.origin);
    return parsed.pathname.includes('/auth/');
  } catch {
    return url.includes('/auth/');
  }
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = localStorage.getItem('access_token') || localStorage.getItem('auth_token');
  const currentProfileUserId = resolveCurrentProfileUserId();
  const currentInterviewUserId = resolveCurrentInterviewUserId();
  const shouldAttachProfileUser = isMsProfileRequest(req.url);
  const shouldAttachInterviewUser = isInterviewRequest(req.url);
  const shouldAttachRoadmapUser = isRoadmapRequest(req.url);

  // For profile requests with missing UUID, allow the request to proceed
  // (component will handle 401/403 gracefully rather than forcing logout)
  if (shouldAttachProfileUser && !currentProfileUserId) {
    // Continue without the UUID header; backend will reject if needed and component handles it
    return next(req);
  }

  // For interview requests, require UUID or logout
  if (shouldAttachInterviewUser && !currentInterviewUserId) {
    localStorage.removeItem('access_token');
    localStorage.removeItem('auth_token');
    localStorage.removeItem('user');
    localStorage.removeItem('userId');
    localStorage.removeItem('UserId');
    localStorage.removeItem('smarthire_profile_user_uuid');
    localStorage.removeItem('smarthire_interview_user_id');
    window.location.href = '/';
    return throwError(() => new Error('Missing interview user context. Please log in again.'));
  }

  // For roadmap requests, require UUID or logout
  if (shouldAttachRoadmapUser && !currentInterviewUserId) {
    localStorage.removeItem('access_token');
    localStorage.removeItem('auth_token');
    localStorage.removeItem('user');
    localStorage.removeItem('userId');
    localStorage.removeItem('UserId');
    localStorage.removeItem('smarthire_profile_user_uuid');
    localStorage.removeItem('smarthire_interview_user_id');
    window.location.href = '/';
    return throwError(() => new Error('Missing roadmap user context. Please log in again.'));
  }

  let authReq = req;
  if (token || shouldAttachProfileUser || shouldAttachInterviewUser || shouldAttachRoadmapUser) {
    const setHeaders: Record<string, string> = {};
    if (token) {
      setHeaders['Authorization'] = `Bearer ${token}`;
    }
    if (shouldAttachProfileUser) {
      setHeaders['X-User-Id'] = currentProfileUserId as string;
    }
    if ((shouldAttachInterviewUser || shouldAttachRoadmapUser) && currentInterviewUserId) {
      setHeaders['X-Interview-User-Id'] = String(currentInterviewUserId);
    }

    authReq = req.clone({
      setHeaders,
    });
  }

  return next(authReq).pipe(
    catchError((error) => {
      if (error.status === 401 && !isAuthRequest(req.url)) {
        localStorage.removeItem('access_token');
        localStorage.removeItem('auth_token');
        localStorage.removeItem('user');
        localStorage.removeItem('userId');
        localStorage.removeItem('UserId');
        localStorage.removeItem('smarthire_profile_user_uuid');
        localStorage.removeItem('smarthire_interview_user_id');
        window.location.href = '/';
      }
      return throwError(() => error);
    })
  );
};
