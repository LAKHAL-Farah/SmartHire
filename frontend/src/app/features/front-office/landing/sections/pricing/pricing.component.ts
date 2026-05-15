import { Component, AfterViewInit, ElementRef, ViewChild, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ScrollAnimationService } from '../../services/scroll-animation.service';
import { LUCIDE_ICONS } from '../../../../../shared/lucide-icons';

@Component({
  selector: 'app-pricing',
  standalone: true,
  imports: [CommonModule, LUCIDE_ICONS],
  templateUrl: './pricing.component.html',
  styleUrl: './pricing.component.scss'
})
export class PricingComponent implements AfterViewInit {
  @ViewChild('pricingSection') section!: ElementRef;

  isAnnual = signal(false);

  plans = [
    {
      name: 'Free',
      billing: 'Billed monthly',
      price: 0,
      desc: 'Ideal for students just getting started.',
      featured: false,
      cta: 'Get it now',
      features: [
        'Skill assessment (1 per month)',
        'Basic career roadmap',
        '3 AI mock interviews',
        'Community access',
      ],
    },
    {
      name: 'Pro',
      billing: 'Billed monthly',
      price: 19,
      desc: 'Ideal for students serious about landing a job.',
      featured: true,
      cta: 'Get it now',
      features: [
        'Unlimited skill assessments',
        'Advanced AI roadmap',
        'Unlimited mock interviews',
        'Smart CV builder & optimizer',
        'Job matching engine',
        'Progress analytics dashboard',
      ],
    },
    {
      name: 'Team',
      billing: 'Billed monthly',
      price: 299,
      desc: 'Ideal for bootcamps & universities.',
      featured: false,
      cta: 'Contact Sales',
      features: [
        'Everything in Pro',
        'Up to 50 student seats',
        'Group analytics dashboard',
        'Dedicated onboarding session',
        'Priority support',
      ],
    },
  ];

  constructor(private scrollAnim: ScrollAnimationService) {}

  ngAfterViewInit(): void {
    this.scrollAnim.animateFadeUp('.pricing__eyebrow, .pricing__title', this.section.nativeElement);
    this.scrollAnim.animateStagger('.pricing__card', this.section.nativeElement, { stagger: 0.15, y: 50 });
  }
}
