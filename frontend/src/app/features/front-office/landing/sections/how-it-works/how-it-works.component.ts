import { Component, AfterViewInit, ElementRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ScrollAnimationService } from '../../services/scroll-animation.service';

@Component({
  selector: 'app-how-it-works',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './how-it-works.component.html',
  styleUrl: './how-it-works.component.scss'
})
export class HowItWorksComponent implements AfterViewInit {
  @ViewChild('hiwSection') section!: ElementRef;
  @ViewChild('scrollText') scrollText!: ElementRef;

  steps = [
    { num: '1', title: 'Take your skill assessment', desc: '5-minute AI-powered evaluation of your strengths and gaps' },
    { num: '2', title: 'Get your personalized roadmap', desc: 'Custom learning path with milestones and deadlines' },
    { num: '3', title: 'Practice interviews with AI', desc: 'Mock interviews with real-time scoring and feedback' },
    { num: '4', title: 'Apply with an optimized CV', desc: 'AI-tailored resume that matches job requirements' },
  ];

  constructor(private scrollAnim: ScrollAnimationService) {}

  ngAfterViewInit(): void {
    this.scrollAnim.scrollTextReveal(this.scrollText.nativeElement);
    this.scrollAnim.animateStagger('.hiw__step', this.section.nativeElement, { stagger: 0.2 });
  }
}
