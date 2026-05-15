import { Component, AfterViewInit, ElementRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ScrollAnimationService } from '../../services/scroll-animation.service';

@Component({
  selector: 'app-testimonials',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './testimonials.component.html',
  styleUrl: './testimonials.component.scss'
})
export class TestimonialsComponent implements AfterViewInit {
  @ViewChild('testimonialSection') section!: ElementRef;

  private testimonials = [
    { name: 'Sarah M.', info: 'EPFL • Got internship at Google', quote: 'SmartHire turned my scattered learning into a clear plan. I went from failing interviews to getting offers in 3 months.', color: 'linear-gradient(135deg, #2ee8a5, #3b82f6)' },
    { name: 'Yassine K.', info: 'ENIT • Junior Dev at Datadog', quote: "The AI mock interviews were a game-changer. I practiced 50+ scenarios and walked into my real interview completely calm.", color: 'linear-gradient(135deg, #8b5cf6, #ec4899)' },
    { name: 'Léa D.', info: 'Polytechnique • SWE at Stripe', quote: "I didn't know where to start. The roadmap gave me direction, and the job matcher found roles I didn't even know existed.", color: 'linear-gradient(135deg, #f59e0b, #ef4444)' },
    { name: 'Ahmed T.', info: 'INSAT • Full-Stack at Spotify', quote: "Best investment in my career. The skill assessment pinpointed exactly what I needed to learn. No more tutorial hell.", color: 'linear-gradient(135deg, #3b82f6, #06b6d4)' },
    { name: 'Maria P.', info: 'ESPRIT • Frontend at Vercel', quote: "The progress analytics kept me accountable. Seeing my interview scores improve week over week was incredibly motivating.", color: 'linear-gradient(135deg, #10b981, #84cc16)' },
    { name: 'Thomas R.', info: 'Centrale Lyon • SRE at AWS', quote: "I tried other platforms but SmartHire's personalization is unmatched. It actually adapts to your pace and learning style.", color: 'linear-gradient(135deg, #6366f1, #a855f7)' },
    { name: 'Fatma B.', info: "SUP'COM • Backend at Meta", quote: "From zero interview confidence to landing my dream job in 4 months. The AI feedback was more helpful than any human coach.", color: 'linear-gradient(135deg, #ec4899, #f43f5e)' },
    { name: 'Lucas V.', info: 'ETH Zürich • ML Engineer at DeepMind', quote: "The technical assessment was spot-on. It identified gaps in my system design knowledge that I'd been ignoring for months.", color: 'linear-gradient(135deg, #14b8a6, #22d3ee)' },
  ];

  // Duplicate for infinite scroll effect
  row1 = [...this.testimonials.slice(0, 4), ...this.testimonials.slice(0, 4)];
  row2 = [...this.testimonials.slice(4, 8), ...this.testimonials.slice(4, 8)];

  constructor(private scrollAnim: ScrollAnimationService) {}

  ngAfterViewInit(): void {
    this.scrollAnim.animateFadeUp('.testimonials__eyebrow, .testimonials__title', this.section.nativeElement);
  }
}
