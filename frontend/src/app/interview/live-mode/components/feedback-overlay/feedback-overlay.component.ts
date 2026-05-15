import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { animate, style, transition, trigger } from '@angular/animations';

@Component({
  selector: 'app-feedback-overlay',
  standalone: true,
  imports: [CommonModule, MatIconModule],
  templateUrl: './feedback-overlay.component.html',
  styleUrl: './feedback-overlay.component.scss',
  animations: [
    trigger('slideUp', [
      transition(':enter', [
        style({ transform: 'translateY(100%)' }),
        animate('350ms cubic-bezier(0.4,0,0.2,1)', style({ transform: 'translateY(0)' })),
      ]),
      transition(':leave', [animate('250ms ease', style({ transform: 'translateY(100%)' }))]),
    ]),
  ],
})
export class FeedbackOverlayComponent implements OnInit, OnDestroy {
  @Input() feedbackText = '';
  @Input() score = 0;
  @Input() aiFeedback = '';

  @Output() retryClicked = new EventEmitter<void>();
  @Output() continueClicked = new EventEmitter<void>();

  autoCountdown = 30;
  private intervalRef: ReturnType<typeof setInterval> | null = null;

  ngOnInit(): void {
    this.intervalRef = setInterval(() => {
      this.autoCountdown -= 1;
      if (this.autoCountdown <= 0) {
        this.stopCountdown();
        this.continueClicked.emit();
      }
    }, 1000);
  }

  ngOnDestroy(): void {
    this.stopCountdown();
  }

  get scoreColor(): string {
    if (this.score >= 7) {
      return '#00e676';
    }

    if (this.score >= 5) {
      return '#ffab40';
    }

    return '#ef5350';
  }

  onRetry(): void {
    this.stopCountdown();
    this.retryClicked.emit();
  }

  onContinue(): void {
    this.stopCountdown();
    this.continueClicked.emit();
  }

  private stopCountdown(): void {
    if (this.intervalRef) {
      clearInterval(this.intervalRef);
      this.intervalRef = null;
    }
  }
}
