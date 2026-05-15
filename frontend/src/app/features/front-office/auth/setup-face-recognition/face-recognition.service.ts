import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, BehaviorSubject } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../../../environments/environment';

interface EnableFaceResponse {
  status: string;
  message: string;
  faceEmbeddingId: string;
  confidenceScore: number;
}

@Injectable({
  providedIn: 'root'
})
export class FaceRecognitionService {

  private readonly baseUrl = environment.userApiUrl.replace(/\/api\/v1\/?$/, '');
  private currentUserIdSubject = new BehaviorSubject<string | null>(null);

  constructor(private http: HttpClient) {
    this.loadCurrentUserId();
  }

  /**
   * Charger l'ID utilisateur depuis le contexte d'authentification
   */
  private loadCurrentUserId(): void {
    const userId = localStorage.getItem('userId') || 
                   sessionStorage.getItem('userId');
    this.currentUserIdSubject.next(userId);
  }

  /**
   * Obtenir l'ID utilisateur courant
   */
  getCurrentUserId(): string | null {
    return this.currentUserIdSubject.value;
  }

  /**
   * Enregistrer le visage pour MFA
   */
  enableFaceRecognition(imageBase64: string): Observable<EnableFaceResponse> {
    const headers = new HttpHeaders({
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${this.getAuthToken()}`
    });

    const body = {
      image: imageBase64
    };

    const userId = localStorage.getItem('userId') || sessionStorage.getItem('userId');
    console.log('Enabling face recognition for user ID:', userId);
    return this.http.put<EnableFaceResponse>(
      `${this.baseUrl}/auth/enable-face-recognition/${userId}`,
      body,
      { headers }
    ).pipe(
      map(response => {
        console.log('Face registration successful:', response);
        return response;
      })
    );
  }

  /**
   * Désactiver la reconnaissance faciale
   */
  disableFaceRecognition(userId: string): Observable<any> {
    const headers = new HttpHeaders({
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${this.getAuthToken()}`
    });
    
    return this.http.put(
      `${this.baseUrl}/auth/disable-face-recognition/${userId}`,
      {},
      { headers }
    );
  }

  /**
   * Récupérer le token d'authentification
   */
  private getAuthToken(): string {
    return localStorage.getItem('auth_token') || 
           sessionStorage.getItem('auth_token') || '';
  }
}
