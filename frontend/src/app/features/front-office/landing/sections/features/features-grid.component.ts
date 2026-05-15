import { Component, AfterViewInit, ElementRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ScrollAnimationService } from '../../services/scroll-animation.service';

@Component({
  selector: 'app-features-grid',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './features-grid.component.html',
  styleUrl: './features-grid.component.scss'
})
export class FeaturesGridComponent implements AfterViewInit {
  @ViewChild('featuresSection') section!: ElementRef;
  @ViewChild('scrollText') scrollText!: ElementRef;

  features = [
    { icon: '🎯', title: 'AI Skill Assessment', desc: 'Instant analysis of your technical skills with actionable gap identification across 50+ technologies.' },
    { icon: '🗺️', title: 'Personalized Roadmaps', desc: 'Custom learning paths with curated resources, milestones, and time estimates tailored to your career goal.' },
    { icon: '🤖', title: 'AI Mock Interviews', desc: 'Realistic interview simulations with real-time feedback, scoring, and targeted improvement suggestions.' },
    { icon: '📄', title: 'Smart CV Builder', desc: 'AI-optimized resumes that highlight your strengths and match specific job requirements.' },
    { icon: '🔗', title: 'Job Matching Engine', desc: 'Intelligent matching algorithm that connects you with opportunities aligned to your skill profile.' },
    { icon: '📊', title: 'Progress Analytics', desc: 'Track your growth with detailed dashboards, streaks, and performance trends over time.' },
  ];

  constructor(private scrollAnim: ScrollAnimationService) {}

  ngAfterViewInit(): void {
    this.scrollAnim.scrollTextReveal(this.scrollText.nativeElement);
    this.scrollAnim.animateStagger('.features__card', this.section.nativeElement, { stagger: 0.12, y: 50 });
  }
}
