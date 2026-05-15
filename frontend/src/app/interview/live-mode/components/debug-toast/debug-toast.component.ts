import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-debug-toast',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './debug-toast.component.html',
  styleUrl: './debug-toast.component.scss',
})
export class DebugToastComponent {
  @Input() turnLabel = 'IDLE';
  @Input() bgTask = 'nothing';
  @Input() lastTranscript = '—';
  @Input() sessionId: number | null = null;
  @Input() audioState = 'idle';
  @Input() greetingUrl = '—';
  @Input() visible = false;
}
