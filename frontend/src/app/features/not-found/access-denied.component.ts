import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-access-denied',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="flex items-center justify-center min-h-screen bg-gray-50">
      <div class="text-center px-4">
        <div class="mb-8">
          <div class="inline-flex items-center justify-center w-20 h-20 rounded-full bg-red-100">
            <svg class="w-10 h-10 text-red-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4v2m0 4v1m6-14H6a2 2 0 00-2 2v14a2 2 0 002 2h12a2 2 0 002-2V7a2 2 0 00-2-2zm-1 10H7m6 0h-2"/>
            </svg>
          </div>
        </div>

        <h1 class="text-4xl font-bold text-gray-900 mb-4">Accès Refusé</h1>
        <p class="text-xl text-gray-600 mb-2">403 - Forbidden</p>
        
        <p class="text-gray-500 mb-8 max-w-md mx-auto">
          Vous n'avez pas les permissions nécessaires pour accéder à cette ressource. 
          Contactez votre administrateur si vous pensez que c'est une erreur.
        </p>

        <div class="flex gap-4 justify-center flex-wrap">
          <a routerLink="/dashboard" class="inline-block px-6 py-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors">
            Retour au Tableau de Bord
          </a>
          <a routerLink="/" class="inline-block px-6 py-3 bg-gray-200 text-gray-800 rounded-lg hover:bg-gray-300 transition-colors">
            Accueil
          </a>
        </div>
      </div>
    </div>
  `,
  styles: []
})
export class AccessDeniedComponent {}
