import {
  Component,
  AfterViewInit,
  OnDestroy,
  OnInit,
  ElementRef,
  ViewChild,
  NgZone,
  signal,
  HostListener,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { NgxParticlesModule, NgParticlesService } from '@tsparticles/angular';
import { loadSlim } from '@tsparticles/slim';
import type { Container, Engine, ISourceOptions, MoveDirection, OutMode } from '@tsparticles/engine';
import { ScrollAnimationService } from '../../services/scroll-animation.service';
import { gsap } from 'gsap';
import { LUCIDE_ICONS } from '../../../../../shared/lucide-icons';

@Component({
  selector: 'app-hero',
  standalone: true,
  imports: [CommonModule, NgxParticlesModule, LUCIDE_ICONS],
  templateUrl: './hero.component.html',
  styleUrl: './hero.component.scss',
})
export class HeroComponent implements OnInit, AfterViewInit, OnDestroy {
  @ViewChild('heroSection') heroSection!: ElementRef;
  @ViewChild('aiBrain') aiBrain!: ElementRef<HTMLElement>;
  @ViewChild('brainInner') brainInner!: ElementRef<HTMLElement>;
  @ViewChild('mockup') mockup!: ElementRef;

  rotateX = signal(0);
  rotateY = signal(0);
  brainScale = signal(1);

  avatarColors = [
    'linear-gradient(135deg, #2ee8a5, #2E86AB)',
    'linear-gradient(135deg, #8b5cf6, #ec4899)',
    'linear-gradient(135deg, #f59e0b, #ef4444)',
    'linear-gradient(135deg, #3b82f6, #06b6d4)',
    'linear-gradient(135deg, #10b981, #84cc16)',
  ];

  private mouseMoveHandler: ((e: MouseEvent) => void) | null = null;
  private scrollHandler: (() => void) | null = null;

  /* ─── tsParticles Navy Network Config ─── */
  particlesOptions: ISourceOptions = {
    fullScreen: { enable: false },
    background: { color: { value: 'transparent' } },
    fpsLimit: 60,
    interactivity: {
      events: {
        onHover: { enable: true, mode: 'grab' },
        resize: { enable: true },
      },
      modes: {
        grab: { distance: 160, links: { opacity: 0.4 } },
      },
    },
    particles: {
      color: { value: ['#1E3A5F', '#2E86AB', '#2ee8a5'] },
      links: {
        color: '#1E3A5F',
        distance: 160,
        enable: true,
        opacity: 0.18,
        width: 1,
      },
      move: {
        direction: 'none' as MoveDirection,
        enable: true,
        outModes: { default: 'bounce' as OutMode },
        random: true,
        speed: 0.6,
        straight: false,
      },
      number: {
        density: { enable: true, width: 1200, height: 800 },
        value: 80,
      },
      opacity: {
        value: { min: 0.15, max: 0.5 },
        animation: { enable: true, speed: 0.8, sync: false },
      },
      shape: { type: 'circle' },
      size: {
        value: { min: 1, max: 3 },
      },
    },
    detectRetina: true,
  };

  constructor(
    private scrollAnim: ScrollAnimationService,
    private ngParticlesService: NgParticlesService,
    private ngZone: NgZone,
  ) {}

  ngOnInit(): void {
    this.ngParticlesService.init(async (engine: Engine) => {
      await loadSlim(engine);
    });
  }

  onParticlesLoaded(container: Container): void {
    // particles ready
  }

  ngAfterViewInit(): void {
    this.initMouseParallax();
    this.initScrollFollow();
    this.animateEntrance();
  }

  /* ─── Mouse parallax — brain + mockup depth ─── */
  private initMouseParallax(): void {
    this.ngZone.runOutsideAngular(() => {
      this.mouseMoveHandler = (e: MouseEvent) => {
        const { innerWidth, innerHeight } = window;
        const nx = (e.clientX / innerWidth - 0.5);
        const ny = (e.clientY / innerHeight - 0.5);

        // Brain shifts opposite to cursor for depth illusion
        if (this.brainInner?.nativeElement) {
          const bx = -nx * 22;
          const by = -ny * 18;
          this.brainInner.nativeElement.style.transform =
            `translate3d(${bx}px, ${by}px, 0)`;
        }

        // Mockup tilt
        this.rotateX.set(ny * -8);
        this.rotateY.set(nx * 8);
      };
      window.addEventListener('mousemove', this.mouseMoveHandler, { passive: true });
    });
  }

  /* ─── Scroll follow – brain drifts down + fades + scales ─── */
  private initScrollFollow(): void {
    this.ngZone.runOutsideAngular(() => {
      const el = this.aiBrain?.nativeElement;
      if (!el) return;

      this.scrollHandler = () => {
        const scrollY = window.scrollY;
        const vh = window.innerHeight;
        const fadeStart = vh * 0.5;
        const fadeEnd = vh * 3.5;

        let opacity: number;
        if (scrollY <= fadeStart) {
          opacity = 1;
        } else if (scrollY >= fadeEnd) {
          opacity = 0;
        } else {
          opacity = 1 - (scrollY - fadeStart) / (fadeEnd - fadeStart);
        }

        // Scroll-driven rotation (full 360 over ~3 viewport heights)
        const rotation = (scrollY / (vh * 3)) * 360;

        // Horizontal sine-wave drift: brain sways left ↔ right
        const sineX = Math.sin(scrollY / (vh * 0.6)) * 80; // ±80px

        // Downward parallax drift
        const parallaxY = scrollY * 0.12;

        // Scale down on scroll: starts at 1, smoothly scales to ~0.6 by fadeEnd
        const scaleDown = Math.max(0.6, 1 - (scrollY / (fadeEnd * 1.2)) * 0.4);
        
        // Slight scale pulse
        const scalePulse = 1 + Math.sin(scrollY / (vh * 0.8)) * 0.06;
        const scale = scaleDown * scalePulse;

        el.style.opacity = `${opacity}`;
        el.style.transform = `translateY(calc(-50% + ${parallaxY}px)) translateX(${sineX}px) rotate(${rotation}deg) scale(${scale})`;
        el.style.visibility = opacity <= 0 ? 'hidden' : 'visible';
      };
      window.addEventListener('scroll', this.scrollHandler, { passive: true });
    });
  }

  /* ─── GSAP staggered entrance ─── */
  private animateEntrance(): void {
    const tl = gsap.timeline({
      defaults: { ease: 'power3.out' },
      delay: 0.3,
    });

    // Eyebrow
    tl.from('.hero__eyebrow', {
      y: 20, opacity: 0, duration: 0.7,
    })
    // Word-by-word headline
    .to('.hero__word', {
      y: 0, opacity: 1,
      duration: 0.6,
      stagger: 0.07,
      ease: 'power4.out',
    }, '-=0.3')
    // Subtitle
    .to('.hero__subtitle', {
      opacity: 1, y: 0, duration: 0.8,
    }, '-=0.3')
    // CTAs
    .to('.hero__actions', {
      opacity: 1, y: 0, duration: 0.7,
    }, '-=0.5')
    // Social proof
    .to('.hero__proof', {
      opacity: 1, y: 0, duration: 0.7,
    }, '-=0.4')
    // AI brain scales in
    .from('.ai-brain-fixed', {
      scale: 0.7, opacity: 0, duration: 1.4,
      ease: 'elastic.out(1, 0.55)',
    }, '-=1.4')
    // Mockup slides up
    .from('.hero__mockup', {
      y: 100, opacity: 0, duration: 1.4,
      ease: 'power4.out',
    }, '-=0.8');
  }

  ngOnDestroy(): void {
    if (this.mouseMoveHandler) {
      window.removeEventListener('mousemove', this.mouseMoveHandler);
    }
    if (this.scrollHandler) {
      window.removeEventListener('scroll', this.scrollHandler);
    }
  }
}
