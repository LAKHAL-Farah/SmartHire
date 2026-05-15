import { Component, AfterViewInit, ElementRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ScrollAnimationService } from '../../services/scroll-animation.service';
import { LUCIDE_ICONS } from '../../../../../shared/lucide-icons';

@Component({
  selector: 'app-cta',
  standalone: true,
  imports: [CommonModule, LUCIDE_ICONS],
  templateUrl: './cta.component.html',
  styleUrl: './cta.component.scss'
})
export class CtaComponent implements AfterViewInit {
  @ViewChild('ctaSection') section!: ElementRef;
  @ViewChild('scrollText') scrollText!: ElementRef;

  constructor(private scrollAnim: ScrollAnimationService) {}

  ngAfterViewInit(): void {
    this.scrollAnim.scrollTextReveal(this.scrollText.nativeElement);
    this.scrollAnim.animateFadeUp('.cta__subtitle, .btn-primary', this.section.nativeElement, { delay: 0.3 });
  }
}
