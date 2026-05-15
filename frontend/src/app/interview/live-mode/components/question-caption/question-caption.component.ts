import { CommonModule } from '@angular/common';
import { Component, Input, OnDestroy } from '@angular/core';
import { animate, style, transition, trigger } from '@angular/animations';

@Component({
  selector: 'app-question-caption',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './question-caption.component.html',
  styleUrl: './question-caption.component.scss',
  animations: [
    trigger('fadeSlide', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateX(-50%) translateY(10px)' }),
        animate('250ms ease', style({ opacity: 1, transform: 'translateX(-50%) translateY(0)' })),
      ]),
      transition(':leave', [animate('200ms ease', style({ opacity: 0 }))]),
    ]),
  ],
})
export class QuestionCaptionComponent implements OnDestroy {
  visible = false;

  private textValue: string | null = null;
  private hideTimer: ReturnType<typeof setTimeout> | null = null;

  @Input() set questionText(val: string | null) {
    if (val && val !== this.textValue) {
      this.textValue = val;
      this.visible = true;
      if (this.hideTimer) {
        clearTimeout(this.hideTimer);
      }
      this.hideTimer = setTimeout(() => {
        this.visible = false;
      }, 12000);
    }
  }

  get questionText(): string | null {
    return this.textValue;
  }

  ngOnDestroy(): void {
    if (this.hideTimer) {
      clearTimeout(this.hideTimer);
      this.hideTimer = null;
    }
  }
}
