import { Injectable, signal } from '@angular/core';

export type ToastType = 'success' | 'error' | 'info';

export interface Toast {
  id: number;
  type: ToastType;
  message: string;
  durationMs: number;
}

@Injectable({
  providedIn: 'root',
})
export class ToastService {
  readonly toasts = signal<Toast[]>([]);
  private counter = 0;

  show(type: ToastType, message: string, durationMs = 3000): void {
    const id = ++this.counter;
    const toast: Toast = { id, type, message, durationMs };
    this.toasts.update((rows) => [...rows, toast]);

    window.setTimeout(() => {
      this.dismiss(id);
    }, durationMs);
  }

  dismiss(toastId: number): void {
    this.toasts.update((rows) => rows.filter((toast) => toast.id !== toastId));
  }
}
