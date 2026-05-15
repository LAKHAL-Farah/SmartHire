import {
  AfterViewChecked,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  ViewChild,
  computed,
  inject,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize } from 'rxjs';
import { AiChatService } from './ai-chat.service';

type ChatRole = 'user' | 'ai' | 'system';

interface ChatMessage {
  id: string;
  role: ChatRole;
  text: string;
  createdAt: number;
}

function newId(): string {
  const maybeCrypto = globalThis.crypto as Crypto | undefined;
  if (maybeCrypto?.randomUUID) return maybeCrypto.randomUUID();
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

@Component({
  selector: 'app-chat-widget',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './chat-widget.component.html',
  styleUrl: './chat-widget.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ChatWidgetComponent implements AfterViewChecked {
  private readonly ai = inject(AiChatService);

  isOpen = signal(false);
  isSending = signal(false);
  draft = signal('');
  error = signal<string | null>(null);
  selectedFile = signal<File | null>(null);

  panelWidth = signal(340);
  panelHeight = signal(460);

  messages = signal<ChatMessage[]>([
    {
      id: newId(),
      role: 'system',
      text: 'Hi! Ask me about jobs, or upload your resume for matching.',
      createdAt: Date.now()
    }
  ]);

  canSend = computed(() => {
    return this.draft().trim().length > 0 && !this.isSending();
  });

  canUpload = computed(() => {
    return this.selectedFile() !== null && !this.isSending();
  });

  @ViewChild('messagesViewport')
  private readonly messagesViewport?: ElementRef<HTMLDivElement>;

  private pendingScrollToBottom = false;

  private resizeMode: 'left' | 'top' | 'corner' | null = null;
  private resizeStartX = 0;
  private resizeStartY = 0;
  private resizeStartWidth = 0;
  private resizeStartHeight = 0;

  ngAfterViewChecked(): void {
    if (!this.pendingScrollToBottom) return;
    const el = this.messagesViewport?.nativeElement;
    if (!el) return;

    el.scrollTop = el.scrollHeight;
    this.pendingScrollToBottom = false;
  }

  toggleOpen(): void {
    this.isOpen.set(!this.isOpen());
    if (this.isOpen()) {
      this.queueScrollToBottom();
    }
  }

  minimize(): void {
    this.isOpen.set(false);
  }

  startResize(mode: 'left' | 'top' | 'corner', event: PointerEvent): void {
    if (!this.isOpen()) return;

    event.preventDefault();
    event.stopPropagation();

    this.resizeMode = mode;
    this.resizeStartX = event.clientX;
    this.resizeStartY = event.clientY;
    this.resizeStartWidth = this.panelWidth();
    this.resizeStartHeight = this.panelHeight();

    window.addEventListener('pointermove', this.onResizeMove, { passive: false });
    window.addEventListener('pointerup', this.onResizeEnd, { passive: true });
  }

  onDraftInput(value: string): void {
    this.draft.set(value);
  }

  onKeyDown(event: KeyboardEvent): void {
    if (event.key !== 'Enter') return;
    event.preventDefault();
    this.send();
  }

  onFileSelected(file: File | null): void {
    this.selectedFile.set(file);
  }

  clearSelectedFile(): void {
    this.selectedFile.set(null);
  }

  send(): void {
    const message = this.draft().trim();
    if (!message || this.isSending()) return;

    this.error.set(null);
    this.isSending.set(true);
    this.draft.set('');

    this.appendMessage('user', message);

    this.ai
      .sendMessage(message)
      .pipe(finalize(() => this.isSending.set(false)))
      .subscribe({
        next: (res) => {
          this.appendMessage('ai', res.response);
        },
        error: (error: unknown) => {
          this.error.set(this.toErrorMessage(error, 'Could not reach the AI service.'));
          this.appendMessage('system', 'Sorry — something went wrong. Please try again.');
        }
      });
  }

  uploadResume(): void {
    const file = this.selectedFile();
    if (!file || this.isSending()) return;

    this.error.set(null);
    this.isSending.set(true);
    this.selectedFile.set(null);

    this.appendMessage('user', `Uploaded resume: ${file.name}`);

    this.ai
      .matchResume(file)
      .pipe(finalize(() => this.isSending.set(false)))
      .subscribe({
        next: (res) => {
          this.appendMessage('ai', res.response);
        },
        error: (error: unknown) => {
          this.error.set(this.toErrorMessage(error, 'Resume upload failed.'));
          this.appendMessage('system', 'Sorry — I could not process that file.');
        }
      });
  }

  private toErrorMessage(error: unknown, fallback: string): string {
    if (!(error instanceof HttpErrorResponse)) {
      return fallback;
    }

    const payload = error.error as { error?: string } | string | null;
    if (payload && typeof payload === 'object' && typeof payload.error === 'string' && payload.error.trim()) {
      return payload.error;
    }

    if (typeof payload === 'string' && payload.trim()) {
      return payload;
    }

    return fallback;
  }

  private appendMessage(role: ChatRole, text: string): void {
    const next: ChatMessage = {
      id: newId(),
      role,
      text,
      createdAt: Date.now()
    };

    this.messages.update((current) => [...current, next]);
    this.queueScrollToBottom();
  }

  private queueScrollToBottom(): void {
    this.pendingScrollToBottom = true;
  }

  private readonly onResizeMove = (event: PointerEvent): void => {
    if (!this.resizeMode) return;
    event.preventDefault();

    const dx = this.resizeStartX - event.clientX;
    const dy = this.resizeStartY - event.clientY;

    const maxWidth = Math.max(320, window.innerWidth - 32);
    const maxHeight = Math.max(320, window.innerHeight - 32);
    const minWidth = 320;
    const minHeight = 360;

    if (this.resizeMode === 'left' || this.resizeMode === 'corner') {
      const nextWidth = this.clamp(this.resizeStartWidth + dx, minWidth, Math.min(640, maxWidth));
      this.panelWidth.set(nextWidth);
    }

    if (this.resizeMode === 'top' || this.resizeMode === 'corner') {
      const nextHeight = this.clamp(this.resizeStartHeight + dy, minHeight, Math.min(720, maxHeight));
      this.panelHeight.set(nextHeight);
    }
  };

  private readonly onResizeEnd = (): void => {
    this.resizeMode = null;
    window.removeEventListener('pointermove', this.onResizeMove);
    window.removeEventListener('pointerup', this.onResizeEnd);
  };

  private clamp(value: number, min: number, max: number): number {
    return Math.min(max, Math.max(min, value));
  }
}
