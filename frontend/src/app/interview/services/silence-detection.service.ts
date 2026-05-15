import { Injectable } from '@angular/core';

export interface SilenceDetectionConfig {
  analyser: AnalyserNode;
  silenceThresholdRms?: number;
  requiredContinuousSilenceMs?: number;
  minimumRecordingMs?: number;
  smoothingAlpha?: number;
  onLevel?: (rms: number, smoothedRms: number) => void;
  onSilenceProgress?: (continuousSilenceMs: number) => void;
  onSpeakingChange?: (isSpeaking: boolean) => void;
  onSilenceConfirmed: () => void;
}

export interface SilenceDetectionSnapshot {
  rawRms: number;
  smoothedRms: number;
  isSpeaking: boolean;
  continuousSilenceMs: number;
}

@Injectable({ providedIn: 'root' })
export class SilenceDetectionService {
  private readonly defaultSilenceThresholdRms = 0.02;
  private readonly defaultRequiredContinuousSilenceMs = 3000;
  private readonly defaultMinimumRecordingMs = 2000;
  private readonly defaultSmoothingAlpha = 0.22;

  private rafId = 0;
  private active = false;
  private startedAtMs = 0;
  private silenceStartedAtMs: number | null = null;
  private hasConfirmedSilence = false;
  private speaking = false;
  private rawRms = 0;
  private smoothedRms = 0;
  private continuousSilenceMs = 0;
  private config: SilenceDetectionConfig | null = null;
  private sampleBuffer: Float32Array | null = null;

  start(config: SilenceDetectionConfig): void {
    this.stop();

    this.config = config;
    this.active = true;
    this.startedAtMs = performance.now();
    this.silenceStartedAtMs = null;
    this.hasConfirmedSilence = false;
    this.speaking = false;
    this.rawRms = 0;
    this.smoothedRms = 0;
    this.continuousSilenceMs = 0;
    this.sampleBuffer = new Float32Array(config.analyser.fftSize);

    this.tick();
  }

  stop(): void {
    this.active = false;
    this.config = null;
    this.sampleBuffer = null;
    this.silenceStartedAtMs = null;
    this.hasConfirmedSilence = false;
    this.continuousSilenceMs = 0;
    cancelAnimationFrame(this.rafId);
    this.rafId = 0;
  }

  getSnapshot(): SilenceDetectionSnapshot {
    return {
      rawRms: this.rawRms,
      smoothedRms: this.smoothedRms,
      isSpeaking: this.speaking,
      continuousSilenceMs: this.continuousSilenceMs,
    };
  }

  private tick = (): void => {
    if (!this.active || !this.config || !this.sampleBuffer) {
      return;
    }

    const now = performance.now();
    const analyser = this.config.analyser;
    const threshold = this.config.silenceThresholdRms ?? this.defaultSilenceThresholdRms;
    const requiredContinuousSilenceMs =
      this.config.requiredContinuousSilenceMs ?? this.defaultRequiredContinuousSilenceMs;
    const minimumRecordingMs = this.config.minimumRecordingMs ?? this.defaultMinimumRecordingMs;
    const smoothingAlpha = this.config.smoothingAlpha ?? this.defaultSmoothingAlpha;

    analyser.getFloatTimeDomainData(this.sampleBuffer);

    let squared = 0;
    for (let i = 0; i < this.sampleBuffer.length; i += 1) {
      const sample = this.sampleBuffer[i];
      squared += sample * sample;
    }

    this.rawRms = Math.sqrt(squared / this.sampleBuffer.length);
    this.smoothedRms =
      this.smoothedRms === 0
        ? this.rawRms
        : this.smoothedRms * (1 - smoothingAlpha) + this.rawRms * smoothingAlpha;

    const silent = this.smoothedRms < threshold;

    if (silent) {
      if (this.silenceStartedAtMs === null) {
        this.silenceStartedAtMs = now;
      }
      this.continuousSilenceMs = Math.max(0, now - this.silenceStartedAtMs);
    } else {
      this.silenceStartedAtMs = null;
      this.continuousSilenceMs = 0;
    }

    const nextSpeaking = !silent;
    if (nextSpeaking !== this.speaking) {
      this.speaking = nextSpeaking;
      this.config.onSpeakingChange?.(this.speaking);
    }

    this.config.onLevel?.(this.rawRms, this.smoothedRms);
    this.config.onSilenceProgress?.(this.continuousSilenceMs);

    const elapsedMs = now - this.startedAtMs;
    const silenceSatisfied = this.continuousSilenceMs >= requiredContinuousSilenceMs;
    const minDurationSatisfied = elapsedMs >= minimumRecordingMs;

    if (!this.hasConfirmedSilence && silenceSatisfied && minDurationSatisfied) {
      this.hasConfirmedSilence = true;
      this.config.onSilenceConfirmed();
    }

    this.rafId = requestAnimationFrame(this.tick);
  };
}
