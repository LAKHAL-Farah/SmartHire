// services/user-management.service.ts
import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map, catchError, throwError } from 'rxjs';
import { environment } from '../../../../environments/environment';


export interface ApiUser {
  id: string;
  email: string;
  status: 'ACTIVE' | 'INACTIVE' | 'SUSPENDED' | 'BANNED' | 'DELETED';
  role: {
    id: string;
    name: 'ADMIN' | 'USER' | 'recruiter';
  };
  profile: {
    firstName: string;
    lastName: string;
    headline: string;
    location: string;
    githubUrl: string;
    linkedinUrl: string;
    avatarUrl: string;
  };
  createdAt: string;
  updatedAt: string;
}


export interface DisplayUser {
  id: string;
  name: string;
  email: string;
  avatar: string;
  role: 'Candidate' | 'Recruiter' | 'Admin';
  plan: 'Free' | 'Premium' | 'Recruiter' ;
  status: 'Active' | 'Suspended' | 'Pending' | 'Inactive';
  joined: string;
  lastActive: string;
  location: string;
  assessments: number;
  interviews: number;
  headline: string;
  githubUrl: string;
  linkedinUrl: string;
  avatarUrl: string;
}

export interface UserUpdateRequest {
  email: string;
  password?: string;
  roleName: 'candidate' | 'recruiter';
}

@Injectable({
  providedIn: 'root'
})
export class UserManagementService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.userApiUrl.replace(/\/$/, '')}/users`;

  getAllUsers(): Observable<ApiUser[]> {
    return this.http.get<ApiUser[]>(this.apiUrl).pipe(
      catchError(this.handleError)
    );
  }

  getUserById(id: string): Observable<ApiUser> {
    return this.http.get<ApiUser>(`${this.apiUrl}/${id}`).pipe(
      catchError(this.handleError)
    );
  }

  
  transformToDisplayUser(apiUser: ApiUser): DisplayUser {
    const fullName = `${apiUser.profile.firstName} ${apiUser.profile.lastName}`;
    const initials = `${apiUser.profile.firstName.charAt(0)}${apiUser.profile.lastName.charAt(0)}`;
    
    
    let displayRole: 'Candidate' | 'Recruiter' | 'Admin' = 'Candidate';
    if (apiUser.role.name === 'ADMIN') displayRole = 'Admin';
    else if (apiUser.role.name === 'recruiter') displayRole = 'Recruiter';
    else displayRole = 'Candidate';
    
    
    let displayStatus: 'Active' | 'Suspended' | 'Pending' | 'Inactive' = 'Active';
    switch (apiUser.status) {
      case 'ACTIVE': displayStatus = 'Active'; break;
      case 'INACTIVE': displayStatus = 'Inactive'; break;
      case 'SUSPENDED': displayStatus = 'Suspended'; break;
      case 'BANNED': displayStatus = 'Suspended'; break;
      case 'DELETED': displayStatus = 'Inactive'; break;
    }
    
    
    let plan: 'Free' | 'Premium' | 'Recruiter' = 'Free';
    if (displayRole === 'Recruiter') plan = 'Recruiter';
    else if (displayRole === 'Admin') plan = 'Premium';
    
    
    const joinedDate = new Date(apiUser.createdAt);
    const formattedDate = joinedDate.toLocaleDateString('en-US', { 
      month: 'short', 
      day: 'numeric', 
      year: 'numeric' 
    });
    
    const lastActiveDate = new Date(apiUser.updatedAt);
    const now = new Date();
    const diffMs = now.getTime() - lastActiveDate.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    let lastActiveStr = '';
    
    if (diffMins < 1) lastActiveStr = 'Just now';
    else if (diffMins < 60) lastActiveStr = `${diffMins} min ago`;
    else if (diffMins < 1440) lastActiveStr = `${Math.floor(diffMins / 60)} hr ago`;
    else lastActiveStr = `${Math.floor(diffMins / 1440)} days ago`;
    
    return {
      id: apiUser.id,
      name: fullName,
      email: apiUser.email,
      avatar: initials,
      role: displayRole,
      plan: plan,
      status: displayStatus,
      joined: formattedDate,
      lastActive: lastActiveStr,
      location: apiUser.profile.location || 'Not specified',
      assessments: 0, 
      interviews: 0, 
      headline: apiUser.profile.headline,
      githubUrl: apiUser.profile.githubUrl,
      linkedinUrl: apiUser.profile.linkedinUrl,
      avatarUrl: apiUser.profile.avatarUrl
    };
  }


  public updateUser(id: string, data: UserUpdateRequest): Observable<any> {
    return this.http.put<any>(`${this.apiUrl}/${id}`, data).pipe(
      catchError(this.handleError)
    );
  }

  public deleteUser(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}/hard`).pipe(
      catchError(this.handleError)
    );
  }

  public isMfaEnabled(id: string): Observable<boolean>{
    return this.http.get<boolean>(`${this.apiUrl}/mfa/${id}`).pipe(
      catchError(this.handleError)
    );
  }

  private handleError(error: any): Observable<never> {
    console.error('API Error:', error);
    let errorMessage = 'An error occurred while fetching users';
    if (error.status === 0) {
      errorMessage = 'Cannot connect to server. Please check if the backend is running.';
    } else if (error.status === 404) {
      errorMessage = 'API endpoint not found.';
    } else if (error.status === 500) {
      errorMessage = 'Server error. Please try again later.';
    }
    return throwError(() => new Error(errorMessage));
  }
}