import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class FaceCameraService {

  /**
   * Démarrer la caméra
   */
  async startCamera(): Promise<MediaStream> {
    try {
      const constraints = {
        video: {
          facingMode: 'user',
          width: { ideal: 1280 },
          height: { ideal: 720 }
        },
        audio: false
      };

      const stream = await navigator.mediaDevices.getUserMedia(constraints);
      return stream;
    } catch (error) {
      console.error('Error accessing camera:', error);
      throw error;
    }
  }

  /**
   * Arrêter la caméra
   */
  stopCamera(stream: MediaStream): void {
    if (stream) {
      stream.getTracks().forEach(track => track.stop());
    }
  }

  /**
   * Vérifier la disponibilité de la caméra
   */
  async isCameraAvailable(): Promise<boolean> {
    try {
      const devices = await navigator.mediaDevices.enumerateDevices();
      return devices.some(device => device.kind === 'videoinput');
    } catch (error) {
      console.error('Error checking camera availability:', error);
      return false;
    }
  }
}
