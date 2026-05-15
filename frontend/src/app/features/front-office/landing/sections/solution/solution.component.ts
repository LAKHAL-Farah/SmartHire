import {
  Component,
  AfterViewInit,
  OnDestroy,
  ElementRef,
  ViewChild,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { gsap } from 'gsap';
import { ScrollTrigger } from 'gsap/ScrollTrigger';

gsap.registerPlugin(ScrollTrigger);

@Component({
  selector: 'app-solution',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './solution.component.html',
  styleUrl: './solution.component.scss'
})
export class SolutionComponent implements AfterViewInit, OnDestroy {
  @ViewChild('solutionSection') section!: ElementRef;

  activeStep = 0;
  private scrollTriggerInstance: ScrollTrigger | null = null;

  steps = [
    { id: '01', title: 'Assess your skills', desc: 'Take a 5-minute AI-driven skills assessment. Get a detailed radar of where you stand across key engineering competencies.' },
    { id: '02', title: 'Get your roadmap', desc: 'Receive a personalized learning roadmap with clear milestones, resources, and estimated timelines tailored to your goal.' },
    { id: '03', title: 'Ace interviews', desc: 'Practice with AI mock interviews that adapt to your level. Get real-time feedback, scoring, and improvement tips.' },
    { id: '04', title: 'Match with jobs', desc: 'Our matching engine connects you with roles that fit your exact skill profile. Apply with an AI-optimized resume.' },
  ];

  roadmapItems = [
    { label: 'JavaScript Fundamentals', duration: 'Week 1-2' },
    { label: 'React + TypeScript', duration: 'Week 3-5' },
    { label: 'Node.js & APIs', duration: 'Week 6-8' },
    { label: 'System Design Basics', duration: 'Week 9-10' },
    { label: 'Portfolio Projects', duration: 'Week 11-12' },
    { label: 'Interview Prep', duration: 'Week 13-14' },
  ];

  jobs = [
    { role: 'Frontend Engineer', company: 'Spotify • Remote', match: 94 },
    { role: 'Full-Stack Developer', company: 'Datadog • Paris', match: 89 },
    { role: 'Software Engineer Intern', company: 'Google • Zürich', match: 85 },
    { role: 'React Developer', company: 'Stripe • Dublin', match: 82 },
  ];

  ngAfterViewInit(): void {
    const section = this.section.nativeElement;
    const totalSteps = this.steps.length;

    this.scrollTriggerInstance = ScrollTrigger.create({
      trigger: section,
      start: 'top top',
      end: `+=${totalSteps * 100}%`,
      pin: true,
      scrub: 1,
      onUpdate: (self) => {
        const progress = self.progress;
        const step = Math.min(Math.floor(progress * totalSteps), totalSteps - 1);
        this.activeStep = step;
      },
    });
  }

  ngOnDestroy(): void {
    this.scrollTriggerInstance?.kill();
  }
}
