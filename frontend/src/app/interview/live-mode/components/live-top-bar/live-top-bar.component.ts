import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { LiveSubMode } from '../../../models/live-session.model';
import { TimerPipe } from '../../../../shared/pipes/timer.pipe';

@Component({
  selector: 'app-live-top-bar',
  standalone: true,
  imports: [CommonModule, TimerPipe],
  templateUrl: './live-top-bar.component.html',
  styleUrl: './live-top-bar.component.scss',
})
export class LiveTopBarComponent {
  @Input() sessionTimerSeconds = 0;
  @Input() currentQuestionIndex = 0;
  @Input() totalQuestions = 0;
  @Input() liveSubMode: LiveSubMode = 'TEST_LIVE';
  @Input() companyName = 'Tech Company';
}
