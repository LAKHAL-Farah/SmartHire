import { Component, AfterViewInit, ElementRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ScrollAnimationService } from '../../services/scroll-animation.service';

@Component({
  selector: 'app-trust-bar',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './trust-bar.component.html',
  styleUrl: './trust-bar.component.scss'
})
export class TrustBarComponent implements AfterViewInit {
  @ViewChild('trustSection') section!: ElementRef;

  logos = [
    'EPFL', 'Polytechnique', 'ENIT', 'INSAT', 'ESPRIT',
    'MIT', 'Stanford', 'ETH Zürich', 'Centrale Lyon', 'ENSIAS',
    'ENSI', 'SUP\'COM', 'FST', 'ENIS',
  ];

  // Duplicate for infinite scroll
  allLogos = [...this.logos, ...this.logos];

  constructor(private scrollAnim: ScrollAnimationService) {}

  ngAfterViewInit(): void {
    this.scrollAnim.animateFadeUp('.trust__label', this.section.nativeElement, { duration: 0.8 });
  }
}
