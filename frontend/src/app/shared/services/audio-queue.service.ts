import { Injectable } from '@angular/core';

export interface QueueAudioOptions {
  volume?: number;
  playbackRate?: number;
  timeoutMs?: number;
  revokeBlobOnEnd?: boolean;
  onStart?: () => void;
  onEnd?: () => void;
}

export interface AudioQueueSnapshot {
  isPlaying: boolean;
  pendingCount: number;
  currentSource: string;
}

@Injectable({ providedIn: 'root' })
export class AudioQueueService {
  private queueTail: Promise<void> = Promise.resolve();
  private queueVersion = 0;
  private pendingCount = 0;
  private currentAudio: HTMLAudioElement | null = null;
  private currentSource = '';
  private isPlaying = false;

  enqueue(audioUrl: string, options: QueueAudioOptions = {}): Promise<void> {
    const normalizedUrl = audioUrl.trim();
    if (!normalizedUrl) {
      return Promise.resolve();
    }

    const versionAtEnqueue = this.queueVersion;
    this.pendingCount += 1;

    const task = async (): Promise<void> => {
      this.pendingCount = Math.max(0, this.pendingCount - 1);
      if (versionAtEnqueue !== this.queueVersion) {
        return;
      }

      await this.playAudio(normalizedUrl, options);
    };

    const execution = this.queueTail.then(task, task);
    this.queueTail = execution.catch(() => undefined);
    return execution;
  }

  async prefetch(audioUrl: string): Promise<void> {
    const normalizedUrl = audioUrl.trim();
    if (!normalizedUrl) {
      return;
    }

    try {
      await fetch(normalizedUrl, {
        method: 'HEAD',
        cache: 'force-cache',
      });
    } catch {
      // Prefetch is best-effort and should never fail the interview flow.
    }
  }

  // OPT 7 — Load audio as Blob URL in memory for instant playback
  async prefetchAsBlob(audioUrl: string): Promise<string | null> {
    const normalizedUrl = audioUrl.trim();
    if (!normalizedUrl) {
      return null;
    }

    try {
      const response = await fetch(normalizedUrl);
      const blob = await response.blob();
      const blobUrl = URL.createObjectURL(blob);
      return blobUrl; // Blob URL is in memory — plays instantly
    } catch (e) {
      // Fall back to HTTP URL if blob fetch fails
      return normalizedUrl;
    }
  }

  // Revoke Blob URL to free memory
  revokeBlobUrl(url: string): void {
    if (url && url.startsWith('blob:')) {
      URL.revokeObjectURL(url);
    }
  }

  clear(): void {
    this.queueVersion += 1;
    this.pendingCount = 0;
    this.queueTail = Promise.resolve();
    this.stopCurrentAudio();
  }

  getSnapshot(): AudioQueueSnapshot {
    return {
      isPlaying: this.isPlaying,
      pendingCount: this.pendingCount,
      currentSource: this.currentSource,
    };
  }

  private stopCurrentAudio(): void {
    if (!this.currentAudio) {
      this.isPlaying = false;
      this.currentSource = '';
      return;
    }

    this.currentAudio.onended = null;
    this.currentAudio.onerror = null;
    this.currentAudio.pause();
    this.currentAudio.src = '';
    this.currentAudio = null;
    this.isPlaying = false;
    this.currentSource = '';
  }

  private playAudio(audioUrl: string, options: QueueAudioOptions): Promise<void> {
    return new Promise((resolve, reject) => {
      // OPT 6 — Support streaming/progressive download via Accept-Ranges header
      // Blob URLs play instantly from memory; HTTP URLs support progressive download
      const audio = new Audio(audioUrl);
      const timeoutMs = Math.max(0, options.timeoutMs ?? 0);
      let timeoutRef: ReturnType<typeof setTimeout> | null = null;
      let completed = false;

      const finish = (error?: unknown): void => {
        if (completed) {
          return;
        }

        completed = true;
        if (timeoutRef) {
          clearTimeout(timeoutRef);
          timeoutRef = null;
        }

        this.stopCurrentAudio();

        if (!error && options.revokeBlobOnEnd && audioUrl.startsWith('blob:')) {
          URL.revokeObjectURL(audioUrl);
        }

        options.onEnd?.();

        if (error) {
          reject(error);
          return;
        }

        resolve();
      };

      audio.preload = 'auto';
      audio.volume = options.volume ?? 1;
      audio.playbackRate = options.playbackRate ?? 1;

      audio.onended = () => finish();
      audio.onerror = () => finish(new Error('Audio playback failed.'));

      if (timeoutMs > 0) {
        timeoutRef = setTimeout(() => {
          finish(new Error('Audio playback timed out.'));
        }, timeoutMs);
      }

      this.currentAudio = audio;
      this.currentSource = audioUrl;
      this.isPlaying = false;

      audio
        .play()
        .then(() => {
          this.isPlaying = true;
          options.onStart?.();
        })
        .catch((error: unknown) => {
          finish(error);
        });
    });
  }
}
