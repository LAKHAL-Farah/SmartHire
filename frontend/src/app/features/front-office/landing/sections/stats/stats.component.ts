import {
  Component,
  AfterViewInit,
  ElementRef,
  ViewChild,
  ViewChildren,
  QueryList,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ScrollAnimationService } from '../../services/scroll-animation.service';

@Component({
  selector: 'app-stats',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './stats.component.html',
  styleUrl: './stats.component.scss'
})
export class StatsComponent implements AfterViewInit {
  @ViewChild('statsSection') section!: ElementRef;
  @ViewChildren('statValue') statValues!: QueryList<ElementRef>;

  stats = [
    { value: 84, suffix: '%', prefix: '', label: 'Average interview score improvement' },
    { value: 3, suffix: 'x', prefix: '', label: 'Faster to first job offer' },
    { value: 12000, suffix: '+', prefix: '', label: 'Career roadmaps generated' },
    { value: 400, suffix: '+', prefix: '', label: 'Companies hiring on SmartHire' },
  ];

  constructor(private scrollAnim: ScrollAnimationService) {}

  ngAfterViewInit(): void {
    this.statValues.forEach((el) => {
      const target = parseInt(el.nativeElement.getAttribute('data-target'), 10);
      const suffix = el.nativeElement.getAttribute('data-suffix') || '';
      const prefix = el.nativeElement.getAttribute('data-prefix') || '';
      this.scrollAnim.animateCounter(el.nativeElement, target, { suffix, prefix });
    });
  }
}
