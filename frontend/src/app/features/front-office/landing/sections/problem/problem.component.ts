import { Component, AfterViewInit, ElementRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ScrollAnimationService } from '../../services/scroll-animation.service';

@Component({
  selector: 'app-problem',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './problem.component.html',
  styleUrl: './problem.component.scss'
})
export class ProblemComponent implements AfterViewInit {
  @ViewChild('problemSection') section!: ElementRef;
  @ViewChild('scrollText') scrollText!: ElementRef;

  cards = [
    { icon: '📚', title: 'No clear direction', text: 'Drowning in tutorials with no structured path to employment' },
    { icon: '💼', title: 'Skills mismatch', text: "Don't know what employers actually want or how you compare" },
    { icon: '😰', title: 'Interview anxiety', text: 'Freezing in technical interviews with zero practice or feedback' },
  ];

  constructor(private scrollAnim: ScrollAnimationService) {}

  ngAfterViewInit(): void {
    // Scroll-driven word reveal on the big statement
    this.scrollAnim.scrollTextReveal(this.scrollText.nativeElement);
    // Stagger cards
    this.scrollAnim.animateStagger('.problem__card', this.section.nativeElement, { stagger: 0.2 });
  }
}
