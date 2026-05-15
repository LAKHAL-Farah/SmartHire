import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-participant-tile',
  standalone: true,
  imports: [CommonModule, MatIconModule],
  templateUrl: './participant-tile.component.html',
  styleUrl: './participant-tile.component.scss',
})
export class ParticipantTileComponent {
  @Input() name = '';
  @Input() isAI = false;
  @Input() isSpeaking = false;
  @Input() isLarge = false;
  @Input() videoStream: MediaStream | null = null;
  @Input() micLevel = 0;
}
