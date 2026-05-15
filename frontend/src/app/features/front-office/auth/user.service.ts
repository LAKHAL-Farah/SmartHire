// user.service.ts
import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpHeaders } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError, map, retry } from 'rxjs/operators';
import { environment } from '../../../../environments/environment';

  @Injectable({
    providedIn: 'root'
  })
  export class UserService {

    private readonly usersUrl = `${environment.userApiUrl.replace(/\/$/, '')}/users`;

    constructor(private http: HttpClient) { }

  getAllUsers(): Observable<any[]> {
    return this.http.get<any[]>(this.usersUrl)
      .pipe(
        retry(1),
        catchError(this.handleError)
      );
  }


  getUserById(id: string): Observable<any> {
    return this.http.get<any>(`${this.usersUrl}/${id}`)
      
  }

 
  getUserByEmail(email: string): Observable<any> {
    return this.http.get<any>(`${this.usersUrl}/email/${email}`)
      .pipe(
        catchError(this.handleError)
      );
  }

 
  createUser(userRequest: any, profileRequest: any): Observable<any> {
    return this.http.post<any>(this.usersUrl, {userRequest, profileRequest})
      .pipe(
        catchError(this.handleError)
      );
  }

  

 
  
  getAvatarUrl(user: any): string {
    return user.profile.avatarUrl || 'assets/default-avatar.png';
  }


  private handleError(error: any): Observable<never> {
    let errorMessage = 'Une erreur est survenue';

    if (error.error instanceof ErrorEvent) {
      // Erreur côté client
      errorMessage = `Erreur: ${error.error.message}`;
    } else {
      // Erreur côté serveur
      switch (error.status) {
        case 400:
          errorMessage = 'Requête invalide. Vérifiez les données saisies.';
          break;
        case 401:
          errorMessage = 'Non authentifié. Veuillez vous connecter.';
          break;
        case 403:
          errorMessage = 'Accès non autorisé.';
          break;
        case 404:
          errorMessage = 'Utilisateur non trouvé.';
          break;
        case 409:
          errorMessage = 'Conflit: Cet email existe déjà.';
          break;
        case 500:
          errorMessage = 'Erreur serveur. Veuillez réessayer plus tard.';
          break;
        default:
          errorMessage = `Erreur ${error.status}: ${error.message}`;
      }
    }

    console.error('UserService Error:', error);
    return throwError(() => new Error(errorMessage));
  }
}