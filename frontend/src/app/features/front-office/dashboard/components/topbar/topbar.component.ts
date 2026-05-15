import { Component, Input, signal, HostListener, computed, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink, NavigationEnd } from '@angular/router';
import { LUCIDE_ICONS } from '../../../../../shared/lucide-icons';
import { filter } from 'rxjs/operators';
import { AutheService } from '../../../auth/authe.service';
import { ProfileApiService } from '../../../profile/profile-api.service';

@Component({
  selector: 'app-topbar',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, LUCIDE_ICONS],
  templateUrl: './topbar.component.html',
  styleUrl: './topbar.component.scss'
})
export class TopbarComponent {
  @Input() quizMode = false;
  searchQuery = '';
  notifOpen = signal(false);
  avatarOpen = signal(false);

  formattedDate = this.getFormattedDate();

  private pageTitles: Record<string, string> = {
    '/dashboard/roadmap': 'Roadmap',
    '/dashboard/projects': 'Projects',
    '/dashboard/interview': 'Interview Simulation',
    '/dashboard/cv': 'CV Optimizer',
    '/dashboard/profile': 'Profile',
    '/dashboard/settings': 'Settings',
    '/dashboard/jobs': 'Jobs',
  };

  private url = signal('');
  pageTitle = computed(() => this.pageTitles[this.url()] ?? '');

  constructor(private router: Router, private authService: AutheService) {
    this.url.set(this.router.url);
    this.router.events.pipe(filter(e => e instanceof NavigationEnd)).subscribe((e: NavigationEnd) => {
      this.url.set(e.urlAfterRedirects ?? e.url);
    });
  }

  displayName = computed(() => {
    const userName = localStorage.getItem('userName')?.trim();
    if (userName) return `Good morning, ${userName}`;
    const userJson = localStorage.getItem('user');
    if (userJson) {
      try {
        const u = JSON.parse(userJson) as { name?: string };
        if (u?.name) return `Good morning, ${u.name}`;
      } catch {}
    }
    return 'Good morning';
  });

  displayInitials = computed(() => {
    const userName = localStorage.getItem('userName')?.trim() || '';
    if (userName) {
      const parts = userName.split(/\s+/);
      const fi = parts[0]?.charAt(0) ?? '';
      const li = parts.length > 1 ? parts[parts.length - 1].charAt(0) : '';
      return (fi + li).toUpperCase() || '?';
    }
    const userJson = localStorage.getItem('user');
    if (userJson) {
      try {
        const u = JSON.parse(userJson) as { name?: string };
        const parts = (u.name ?? '').split(/\s+/);
        const fi = parts[0]?.charAt(0) ?? '';
        const li = parts.length > 1 ? parts[parts.length - 1].charAt(0) : '';
        return (fi + li).toUpperCase() || '?';
      } catch {}
    }
    return '?';
  });

  notifications = [
    { text: 'Your roadmap has a new recommended step', time: '2 min ago', color: '#2ee8a5' },
    { text: 'Practice interview score: 8.4/10', time: '1 hour ago', color: '#8b5cf6' },
    { text: 'New job match: Frontend Dev @ Spotify', time: '3 hours ago', color: '#3b82f6' },
  ];

  toggleNotif(): void {
    this.avatarOpen.set(false);
    this.notifOpen.update(v => !v);
  }

  toggleAvatar(): void {
    this.notifOpen.set(false);
    this.avatarOpen.update(v => !v);
  }

  signOut(): void {
    this.avatarOpen.set(false);
    this.authService.logout();
    this.router.navigate(['/login']);
    console.log('User sign out');
  }

  @HostListener('document:click', ['$event'])
  onDocClick(_e: Event): void {
    /* close dropdowns if needed */
  }

  private getFormattedDate(): string {
    const d = new Date();
    const days = ['Sunday','Monday','Tuesday','Wednesday','Thursday','Friday','Saturday'];
    const months = ['January','February','March','April','May','June','July','August','September','October','November','December'];
    return `${days[d.getDay()]}, ${d.getDate()} ${months[d.getMonth()]} ${d.getFullYear()}`;
  }
}