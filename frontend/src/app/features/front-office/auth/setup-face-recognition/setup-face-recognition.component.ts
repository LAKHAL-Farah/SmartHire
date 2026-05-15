import { Component, OnInit, OnDestroy, ViewChild, ElementRef, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { FaceRecognitionService } from './face-recognition.service';
import { FaceCameraService } from './face-camera.service';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthMfaService } from '../auth-mfa.service';

type SetupStep = 'intro' | 'camera' | 'preview' | 'uploading' | 'success' | 'error';

interface SetupFaceState {
  currentStep: SetupStep;
  capturedImage: string | null;
  isLoading: boolean;
  errorMessage: string | null;
  successMessage: string | null;
  processingProgress: number;
  faceEmbeddingId: string | null;
}

@Component({
  selector: 'app-setup-face-recognition',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './setup-face-recognition.component.html',
  styleUrls: ['./setup-face-recognition.component.scss']
})
export class SetupFaceRecognitionComponent implements OnInit, OnDestroy {
  @ViewChild('videoElement') videoElement!: ElementRef<HTMLVideoElement>;
  @ViewChild('canvasElement') canvasElement!: ElementRef<HTMLCanvasElement>;

  state = signal<SetupFaceState>({
    currentStep: 'intro',
    capturedImage: null,
    isLoading: false,
    errorMessage: null,
    successMessage: null,
    processingProgress: 0,
    faceEmbeddingId: null
  });

  cameraStream: MediaStream | null = null;

  constructor(
    private faceRecognitionService: FaceRecognitionService,
    private faceCameraService: FaceCameraService,
    private auth: AuthMfaService,
    private router: Router
  ) {}

  ngOnInit(): void {
    // Initialisation si nécessaire
    
  }

  /**
   * Phase 1: Démarrer le processus
   */
  startSetup(): void {
    const currentState = this.state();
    this.state.set({ ...currentState, currentStep: 'camera' });
    this.initializeCamera();
  }

  /**
   * Phase 2: Initialiser la caméra
   */
  async initializeCamera(): Promise<void> {
    try {
      this.cameraStream = await this.faceCameraService.startCamera();
      
      if (this.videoElement?.nativeElement && this.cameraStream) {
        this.videoElement.nativeElement.srcObject = this.cameraStream;
      }
    } catch (error) {
      this.setError('Unable to access camera. Please check permissions.');
      const currentState = this.state();
      this.state.set({ ...currentState, currentStep: 'error' });
    }
  }

  /**
   * Capturer une image de la caméra
   */
  captureFace(): void {
    if (!this.canvasElement || !this.videoElement) return;

    const video = this.videoElement.nativeElement;
    const canvas = this.canvasElement.nativeElement;
    const ctx = canvas.getContext('2d');

    if (ctx && video.videoWidth > 0) {
      canvas.width = video.videoWidth;
      canvas.height = video.videoHeight;
      ctx.drawImage(video, 0, 0);
      
      const capturedImage = canvas.toDataURL('image/jpeg', 0.95);
      this.stopCamera();
      const currentState = this.state();
      this.state.set({ 
        ...currentState, 
        capturedImage, 
        currentStep: 'preview' 
      });
    }
  }

  /**
   * Upload image depuis fichier
   */
  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files[0]) {
      const file = input.files[0];
      
      if (!file.type.startsWith('image/')) {
        this.setError('Please select a valid image file');
        return;
      }

      const reader = new FileReader();
      reader.onload = (e) => {
        const capturedImage = e.target?.result as string;
        this.stopCamera();
        const currentState = this.state();
        this.state.set({
          ...currentState,
          capturedImage,
          currentStep: 'preview'
        });
      };
      reader.readAsDataURL(file);
    }
  }

  /**
   * Phase 3: Confirmer et envoyer
   */
  async confirmFace(): Promise<void> {
    const currentState = this.state();
    if (!currentState.capturedImage) {
      this.setError('No image captured. Please try again.');
      return;
    }

    this.state.set({
      ...currentState,
      currentStep: 'uploading',
      isLoading: true,
      processingProgress: 0
    });

    try {
      const progressInterval = setInterval(() => {
        const state = this.state();
        if (state.processingProgress < 90) {
          this.state.set({
            ...state,
            processingProgress: state.processingProgress + Math.random() * 20
          });
        }
      }, 300);

      console.log('Uploading face image for user:', this.faceRecognitionService.getCurrentUserId());

      console.log('Captured image size (base64 length):', currentState.capturedImage);
      const response = await this.faceRecognitionService.enableFaceRecognition(
        currentState.capturedImage
      ).toPromise();

      clearInterval(progressInterval);
      const state = this.state();
      this.state.set({
        ...state,
        currentStep: 'success',
        processingProgress: 100,
        faceEmbeddingId: response?.faceEmbeddingId || null,
        successMessage: 'Face recognition enabled successfully!'
      });

      setTimeout(() => {
        void this.auth.redirectAfterLogin();
      }, 2000);

    } catch (error: any) {
      const errorMsg = error?.error?.message || 'Failed to register face. Please try again.';
      this.setError(errorMsg);
      const state = this.state();
      this.state.set({ ...state, currentStep: 'error' });
    } finally {
      const state = this.state();
      this.state.set({ ...state, isLoading: false });
    }
  }

  /**
   * Retake: Retourner à la capture
   */
  retakeFace(): void {
    const currentState = this.state();
    this.state.set({
      ...currentState,
      capturedImage: null,
      errorMessage: null,
      currentStep: 'camera'
    });
    this.initializeCamera();
  }

  /**
   * Arrêter la caméra
   */
  stopCamera(): void {
    if (this.cameraStream) {
      this.cameraStream.getTracks().forEach(track => track.stop());
      this.cameraStream = null;
    }
  }

  /**
   * Annuler l'opération
   */
  cancel(): void {
    this.stopCamera();
    void this.router.navigate(['/admin/settings']);
  }



  /**
   * Setter pour erreur
   */
  private setError(message: string): void {
    const currentState = this.state();
    this.state.set({
      ...currentState,
      errorMessage: message,
      isLoading: false
    });
  }

  ngOnDestroy(): void {
    this.stopCamera();
  }
}
