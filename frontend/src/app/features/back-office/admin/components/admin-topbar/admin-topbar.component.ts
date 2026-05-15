import { Component, signal, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs/operators';
import { LUCIDE_ICONS } from '../../../../../shared/lucide-icons';
import { AutheService } from '../../../../front-office/auth/authe.service';

@Component({
  selector: 'app-admin-topbar',
  standalone: true,
  imports: [CommonModule, FormsModule, LUCIDE_ICONS],
  templateUrl: './admin-topbar.component.html',
  styleUrl: './admin-topbar.component.scss'
})
export class AdminTopbarComponent {
  searchQuery = '';
  userName = localStorage.getItem('userName') || 'Admin User';
  email = localStorage.getItem('email')  || 'Admim@gmail.com';
  notifOpen = signal(false);
  avatarOpen = signal(false);

  constructor(private router: Router, private authService: AutheService ){}

  notifications = [
    { text: 'API error rate spike — 4.2% in last 15 min', time: '2 min ago', color: '#ef4444' },
    { text: 'New recruiter awaiting verification — TechCorp', time: '18 min ago', color: '#f59e0b' },
    { text: 'Stripe webhook failure — payment #8841', time: '1 hour ago', color: '#ef4444' },
    { text: 'AI model latency above threshold — 3.2s avg', time: '2 hours ago', color: '#f59e0b' },
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
    this.authService.logout();
    this.router.navigate(['/login']);
    console.log('Admin sign out');
  }

  @HostListener('document:click', ['$event'])
  onDocClick(e: Event): void {

  }
}
