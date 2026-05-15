import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';

@Component({
  selector: 'app-live-controls',
  standalone: true,
  imports: [CommonModule, MatIconModule, MatTooltipModule],
  templateUrl: './live-controls.component.html',
  styleUrl: './live-controls.component.scss',
})
export class LiveControlsComponent {
  @Input() micEnabled = true;
  @Input() cameraEnabled = true;

  @Output() micToggled = new EventEmitter<boolean>();
  @Output() cameraToggled = new EventEmitter<boolean>();
  @Output() leaveClicked = new EventEmitter<void>();

  toggleMic(): void {
    this.micEnabled = !this.micEnabled;
    this.micToggled.emit(this.micEnabled);
  }

  toggleCamera(): void {
    this.cameraEnabled = !this.cameraEnabled;
    this.cameraToggled.emit(this.cameraEnabled);
  }
}
