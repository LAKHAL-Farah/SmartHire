import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { LUCIDE_ICONS } from '../../../../../shared/lucide-icons';

interface NavItem {
  icon: string;
  label: string;
  route: string;
}

@Component({
  selector: 'app-admin-sidebar',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive, LUCIDE_ICONS],
  templateUrl: './admin-sidebar.component.html',
  styleUrl: './admin-sidebar.component.scss'
})
export class AdminSidebarComponent {
  mainItems: NavItem[] = [
    { icon: 'layout-grid', label: 'Dashboard', route: '/admin' },
  ];

  userItems: NavItem[] = [
    { icon: 'users', label: 'User Management', route: '/admin/users' },
    { icon: 'briefcase', label: 'Recruiter Management', route: '/admin/recruiters' },
  ];

  contentItems: NavItem[] = [
    { icon: 'briefcase', label: 'Job Offers', route: '/admin/jobs' },
    { icon: 'circle-question-mark', label: 'Interview Questions', route: '/admin/questions' },
    { icon: 'book-open', label: 'Skill assessments', route: '/admin/skill-assessments' },
    { icon: 'file-text', label: 'Interview Dashboard', route: '/admin/interview' },
    { icon: 'clock', label: 'Career Paths', route: '/admin/careers' },
  ];

  systemItems: NavItem[] = [
    { icon: 'lightbulb', label: 'AI Monitor', route: '/admin/ai-monitor' },
    { icon: 'chart-bar', label: 'Platform Analytics', route: '/admin/analytics' },
    { icon: 'activity', label: 'System Health', route: '/admin/health' },
  ];

  bottomItems: NavItem[] = [
    { icon: 'settings', label: 'Settings', route: '/admin/settings' },
  ];

  logout(): void {
    console.log('Admin logout');
  }
}
