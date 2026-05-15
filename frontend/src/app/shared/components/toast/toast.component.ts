import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { LUCIDE_ICONS } from '../../lucide-icons';
import { ToastService } from './toast.service';

@Component({
  selector: 'app-toast',
  standalone: true,
  imports: [CommonModule, LUCIDE_ICONS],
  templateUrl: './toast.component.html',
  styleUrl: './toast.component.scss',
})
export class ToastComponent {
  // NOTE: Existing codebase pattern uses centralized LUCIDE_ICONS instead of per-component icon imports.
  readonly toastService = inject(ToastService);

  iconName(type: 'success' | 'error' | 'info'): string {
    if (type === 'success') {
      return 'check-circle';
    }
    if (type === 'error') {
      return 'alert-circle';
    }
    return 'info';
  }
}
