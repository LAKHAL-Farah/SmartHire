import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { Router } from '@angular/router';

@Injectable({ providedIn: 'root' })
export class AuthMfaService {
  // Auth endpoints are exposed at the gateway root (/auth), not under /api/v1.
  private readonly baseUrl = environment.userApiUrl.replace(/\/api\/v1\/?$/, '');

  constructor(private http: HttpClient, private router: Router) {}

  loginMfa(email: string, password: string): Observable<any> {
    return this.http.post(`${this.baseUrl}/auth/login-mfa`, { email, password });
  }

  verifyFace(tempToken: string, image: string): Observable<any> {
    return this.http.post(`${this.baseUrl}/auth/verify-face`, { tempToken, image });
  }

  enableFaceRecognition(faceImageBase64: string): Observable<any> {
    return this.http.post(`${this.baseUrl}/auth/enable-face-recognition`, { faceImage: faceImageBase64 });
  }

  disableFaceRecognition(): Observable<any> {
    return this.http.put(`${this.baseUrl}/auth/disable-face-recognition`, {});
  }

  convertImageToBase64(file: File): Promise<string> {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result));
      reader.onerror = reject;
      reader.readAsDataURL(file);
    });
  }

  captureWebcamImage(video: HTMLVideoElement): string {
    const canvas = document.createElement('canvas');
    canvas.width = video.videoWidth || 640;
    canvas.height = video.videoHeight || 480;
    const ctx = canvas.getContext('2d');
    if (!ctx) return '';
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
    return canvas.toDataURL('image/jpeg');
  }
  logout() {
    localStorage.removeItem('auth_token');
    localStorage.removeItem('access_token');
    localStorage.removeItem('userId');
    localStorage.removeItem('UserId');
    localStorage.removeItem('smarthire_profile_user_uuid');
    localStorage.removeItem('smarthire_interview_user_id');
    localStorage.removeItem('user');
    localStorage.removeItem('userName');
    localStorage.removeItem('email');
    localStorage.removeItem('role');
  }
  redirectAfterLogin(): void {
    const role = (localStorage.getItem('role') || '').toLowerCase();
    if (role.includes('recruiter') || role.includes('admin')) {
      void this.router.navigate(['/admin']);
    } else {
      void this.router.navigate(['/dashboard']);
    }
  }
}
