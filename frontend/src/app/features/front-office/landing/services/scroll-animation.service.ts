import { Injectable, NgZone, OnDestroy } from '@angular/core';
import { gsap } from 'gsap';
import { ScrollTrigger } from 'gsap/ScrollTrigger';
import Lenis from 'lenis';

gsap.registerPlugin(ScrollTrigger);

@Injectable({ providedIn: 'root' })
export class ScrollAnimationService implements OnDestroy {
  private lenis: Lenis | null = null;
  private rafId: number | null = null;

  constructor(private ngZone: NgZone) {}

  initLenis(): void {
    if (this.lenis) return;

    this.ngZone.runOutsideAngular(() => {
      this.lenis = new Lenis({
        duration: 1.2,
        easing: (t: number) => Math.min(1, 1.001 - Math.pow(2, -10 * t)),
        smoothWheel: true,
      });

      this.lenis.on('scroll', ScrollTrigger.update);

      gsap.ticker.add((time: number) => {
        this.lenis?.raf(time * 1000);
      });

      gsap.ticker.lagSmoothing(0);
    });
  }

  /**
   * GitHub-style scroll-driven word-by-word text reveal.
   * Each .word inside the container goes from dim/light to bold/bright
   * as the user scrolls through the section.
   *
   * @param container  The wrapper element containing .word spans
   * @param options    start/end offsets for ScrollTrigger
   */
  scrollTextReveal(
    container: Element,
    options?: { start?: string; end?: string; scrub?: number | boolean }
  ): void {
    const { start = 'top 85%', end = 'bottom 40%', scrub = true } = options || {};
    const words = container.querySelectorAll('.word');
    if (!words.length) return;

    gsap.fromTo(
      words,
      {
        opacity: 0.15,
        fontWeight: 400,
      },
      {
        opacity: 1,
        fontWeight: 700,
        stagger: 0.05,
        ease: 'none',
        scrollTrigger: {
          trigger: container,
          start,
          end,
          scrub,
        },
      }
    );
  }

  /** Fade-up entrance animation */
  animateFadeUp(
    elements: string | Element | Element[],
    trigger: string | Element,
    options?: { delay?: number; duration?: number; y?: number; stagger?: number }
  ): gsap.core.Tween {
    const { delay = 0, duration = 1, y = 60, stagger = 0 } = options || {};
    return gsap.from(elements, {
      immediateRender: false,
      scrollTrigger: {
        trigger,
        start: 'top 85%',
        toggleActions: 'play none none none',
      },
      y,
      opacity: 0,
      duration,
      delay,
      stagger,
      ease: 'power3.out',
    });
  }

  /** Staggered entrance for multiple children */
  animateStagger(
    elements: string | Element[],
    trigger: string | Element,
    options?: { stagger?: number; duration?: number; y?: number }
  ): gsap.core.Tween {
    const { stagger = 0.15, duration = 0.8, y = 40 } = options || {};
    return gsap.from(elements, {
      immediateRender: false,
      scrollTrigger: {
        trigger,
        start: 'top 80%',
        toggleActions: 'play none none none',
      },
      y,
      opacity: 0,
      duration,
      stagger,
      ease: 'power3.out',
    });
  }

  /** Pin a section for scroll-driven storytelling */
  pinSection(
    trigger: string | Element,
    options?: { endOffset?: string; scrub?: number | boolean }
  ): ScrollTrigger {
    const { endOffset = '+=400%', scrub = 1 } = options || {};
    return ScrollTrigger.create({
      trigger,
      start: 'top top',
      end: endOffset,
      pin: true,
      scrub,
    });
  }

  /** Animate a counter number */
  animateCounter(
    element: Element,
    endValue: number,
    options?: { duration?: number; suffix?: string; prefix?: string }
  ): void {
    const { duration = 2, suffix = '', prefix = '' } = options || {};
    const obj = { val: 0 };
    gsap.to(obj, {
      val: endValue,
      duration,
      ease: 'power2.out',
      scrollTrigger: {
        trigger: element,
        start: 'top 85%',
        toggleActions: 'play none none none',
      },
      onUpdate: () => {
        element.textContent = prefix + Math.round(obj.val).toLocaleString() + suffix;
      },
    });
  }

  /** Scale-in animation */
  animateScaleIn(
    elements: string | Element | Element[],
    trigger: string | Element,
    options?: { delay?: number; duration?: number }
  ): gsap.core.Tween {
    const { delay = 0, duration = 1 } = options || {};
    return gsap.from(elements, {
      immediateRender: false,
      scrollTrigger: {
        trigger,
        start: 'top 85%',
        toggleActions: 'play none none none',
      },
      scale: 0.9,
      opacity: 0,
      duration,
      delay,
      ease: 'power3.out',
    });
  }

  refreshScrollTrigger(): void {
    ScrollTrigger.refresh();
  }

  ngOnDestroy(): void {
    if (this.rafId !== null) cancelAnimationFrame(this.rafId);
    this.lenis?.destroy();
    ScrollTrigger.getAll().forEach((st) => st.kill());
    gsap.ticker.remove(this.lenis?.raf as any);
  }

  killAll(): void {
    ScrollTrigger.getAll().forEach((st) => st.kill());
  }
}
