import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnDestroy, Output } from '@angular/core';

@Component({
  selector: 'app-mic-button',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './mic-button.component.html',
  styleUrl: './mic-button.component.scss'
})
export class MicButtonComponent implements OnDestroy {
  @Input() disabled = false;

  @Output() audioReady = new EventEmitter<Blob>();
  @Output() recordingStateChange = new EventEmitter<boolean>();

  isRecording = false;
  isPreparing = false;
  recordingDurationSeconds = 0;

  private mediaRecorder: MediaRecorder | null = null;
  private audioChunks: Blob[] = [];
  private stream: MediaStream | null = null;
  private durationInterval: ReturnType<typeof setInterval> | null = null;

  async startRecording(): Promise<void> {
    if (this.disabled || this.isPreparing || this.isRecording) {
      return;
    }

    try {
      this.isPreparing = true;

      this.stream = await navigator.mediaDevices.getUserMedia({
        audio: {
          channelCount: 1,
          sampleRate: 16000,
          echoCancellation: true,
          noiseSuppression: true
        }
      });

      const mimeType = MediaRecorder.isTypeSupported('audio/webm;codecs=opus')
        ? 'audio/webm;codecs=opus'
        : 'audio/webm';

      this.mediaRecorder = new MediaRecorder(this.stream, { mimeType });
      this.audioChunks = [];

      this.mediaRecorder.ondataavailable = (event: BlobEvent) => {
        if (event.data.size > 0) {
          this.audioChunks.push(event.data);
        }
      };

      this.mediaRecorder.onstop = () => {
        const audioBlob = new Blob(this.audioChunks, { type: mimeType });
        this.audioReady.emit(audioBlob);
        this.stopStream();
      };

      this.mediaRecorder.start(250);
      this.isRecording = true;
      this.isPreparing = false;
      this.recordingStateChange.emit(true);

      this.recordingDurationSeconds = 0;
      this.durationInterval = setInterval(() => {
        this.recordingDurationSeconds += 1;
      }, 1000);
    } catch (err) {
      this.isPreparing = false;
      console.error('Mic access denied or unavailable:', err);
      alert('Microphone access is required to record your answer. Please allow mic access and try again.');
      this.stopStream();
    }
  }

  stopRecording(): void {
    if (this.mediaRecorder && this.isRecording) {
      this.mediaRecorder.stop();
      this.isRecording = false;
      this.recordingStateChange.emit(false);
    }

    if (this.durationInterval) {
      clearInterval(this.durationInterval);
      this.durationInterval = null;
    }
  }

  toggleRecording(): void {
    if (this.disabled || this.isPreparing) {
      return;
    }

    if (this.isRecording) {
      this.stopRecording();
    } else {
      this.startRecording();
    }
  }

  get formattedDuration(): string {
    const m = Math.floor(this.recordingDurationSeconds / 60);
    const s = this.recordingDurationSeconds % 60;
    return `${m}:${s.toString().padStart(2, '0')}`;
  }

  ngOnDestroy(): void {
    this.stopRecording();
    this.stopStream();
    if (this.durationInterval) {
      clearInterval(this.durationInterval);
      this.durationInterval = null;
    }
  }

  private stopStream(): void {
    if (this.stream) {
      this.stream.getTracks().forEach((track) => track.stop());
      this.stream = null;
    }
  }
}
