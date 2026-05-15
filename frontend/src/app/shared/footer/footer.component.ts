import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LUCIDE_ICONS } from '../../shared/lucide-icons';

@Component({
  selector: 'app-footer',
  standalone: true,
  imports: [CommonModule, LUCIDE_ICONS],
  templateUrl: './footer.component.html',
  styleUrl: './footer.component.scss'
})
export class FooterComponent {
  columns = [
    {
      title: 'Product',
      links: ['Features', 'Pricing', 'Roadmap', 'Changelog', 'API'],
    },
    {
      title: 'Company',
      links: ['About', 'Blog', 'Careers', 'Press Kit', 'Contact'],
    },
    {
      title: 'Legal',
      links: ['Privacy Policy', 'Terms of Service', 'Cookie Policy'],
    },
  ];

  socials = [
    { label: 'Twitter', icon: 'twitter', href: '#' },
    { label: 'GitHub', icon: 'github', href: '#' },
    { label: 'LinkedIn', icon: 'linkedin', href: '#' },
  ];
}
