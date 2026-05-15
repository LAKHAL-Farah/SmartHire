import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../../auth/auth.service';
import { LUCIDE_ICONS } from '../../../../../shared/lucide-icons';

interface NavItem {
  icon: string;
  label: string;
  route: string;
  className?: string;
}

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive, LUCIDE_ICONS],
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.scss'
})
export class SidebarComponent {
  @Input() disabled = false;
  mainItems: NavItem[] = [
    { icon: 'layout-grid', label: 'Dashboard', route: '/dashboard' },
    { icon: 'clock', label: 'Roadmap', route: '/dashboard/roadmap' },
    { icon: 'book-open', label: 'Projects', route: '/dashboard/projects' },
  ];

  prepareItems: NavItem[] = [
    { icon: 'circle-check', label: 'Skill assessments', route: '/dashboard/assessments' },
    { icon: 'message-square', label: 'Interview', route: '/dashboard/interview' },
    { icon: 'globe', label: 'Discover', route: '/dashboard/interview/discover' },
    
    // NOTE: Existing shared icon registry uses layout-grid instead of layout-dashboard.
    { icon: 'layout-grid', label: 'Optimizer', route: '/dashboard/optimizer' },
    { icon: 'linkedin', label: 'LinkedIn', route: '/dashboard/linkedin', className: 'sidebar__item--linkedin' },
    { icon: 'github', label: 'GitHub', route: '/dashboard/github', className: 'sidebar__item--github' },
    { icon: 'briefcase', label: 'Job Offers', route: '/dashboard/job-offers' },
    { icon: 'user', label: 'Profile', route: '/dashboard/profile' },
  ];

  recruitItems: NavItem[] = [
    { icon: 'briefcase', label: 'Jobs', route: '/dashboard/jobs' },
    { icon: 'square-pen', label: 'Post Job', route: '/dashboard/post-job' },
  ];

  constructor(private authService: AuthService) {}

  logout(): void {
    this.authService.logout();
  }
}
