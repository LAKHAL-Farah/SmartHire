import { inject, Injectable } from '@angular/core';
import { InterviewApiService } from '../../features/front-office/dashboard/interview/interview-api.service';
import { AudioQueueService } from './audio-queue.service';

@Injectable({ providedIn: 'root' })
export class TtsService {
  private readonly interviewApi = inject(InterviewApiService);
  private readonly audioQueue = inject(AudioQueueService);

  playFromUrl(audioUrl: string): Promise<void> {
    const absoluteAudioUrl = this.interviewApi.resolveBackendAssetUrl(audioUrl);
    return this.audioQueue.enqueue(absoluteAudioUrl);
  }

  playAbsoluteUrl(audioUrl: string): Promise<void> {
    return this.audioQueue.enqueue(audioUrl);
  }

  stop(): void {
    this.audioQueue.clear();
  }

  get isPlaying(): boolean {
    return this.audioQueue.getSnapshot().isPlaying;
  }
}
