import { CdkDragDrop, CdkDragEnd, DragDropModule } from '@angular/cdk/drag-drop';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import {
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
  Output,
  SimpleChanges,
  ViewChild,
  inject,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { InterviewApiService } from '../../interview-api.service';
import { MicButtonComponent } from '../mic-button/mic-button.component';
import { TtsService } from '../../../../../../shared/services/tts.service';

interface PaletteComponent {
  type: string;
  label: string;
  icon: string;
  group: 'compute' | 'network' | 'storage' | 'devops';
}

interface RequirementItem {
  key: string;
  label: string;
  required: boolean;
}

interface ParsedFeedback {
  strengths: string;
  weaknesses: string;
  recommendations: string;
}

interface ArchitectureScore {
  id: number;
  designScore: number;
  requirementsMet: string[];
  requirementsMissed: string[];
  feedback: ParsedFeedback;
}

export interface CanvasNode {
  id: string;
  type: string;
  label: string;
  icon: string;
  group: 'compute' | 'network' | 'storage' | 'devops';
  x: number;
  y: number;
}

export interface CanvasEdge {
  id: string;
  from: string;
  to: string;
}

@Component({
  selector: 'app-cloud-interview',
  standalone: true,
  imports: [CommonModule, FormsModule, DragDropModule, MicButtonComponent],
  templateUrl: './cloud-interview.component.html',
  styleUrl: './cloud-interview.component.scss'
})
export class CloudInterviewComponent implements OnChanges, OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly interviewApi = inject(InterviewApiService);
  private readonly ttsService = inject(TtsService);
  private readonly apiBase = this.resolveBaseUrl();
  private readonly apiRoot = this.resolveApiRoot(this.apiBase);

  @Input() question: { id: number; questionText?: string } | null = null;
  @Input() metadata: { scenario?: string; requirements?: RequirementItem[] } | null = null;
  @Input() sessionId = 0;
  @Output() answerSubmitted = new EventEmitter<any>();

  @ViewChild('canvasEl') canvasEl?: ElementRef<HTMLElement>;

  readonly paletteComponents: PaletteComponent[] = [
    { type: 'vm', label: 'VM', icon: '🖥️', group: 'compute' },
    { type: 'container', label: 'Container', icon: '📦', group: 'compute' },
    { type: 'kubernetes', label: 'Kubernetes', icon: '☸️', group: 'compute' },
    { type: 'server', label: 'Server', icon: '🖧', group: 'compute' },
    { type: 'load_balancer', label: 'Load Balancer', icon: '⚖️', group: 'network' },
    { type: 'vpc', label: 'VPC', icon: '🔒', group: 'network' },
    { type: 'subnet', label: 'Subnet', icon: '🕸️', group: 'network' },
    { type: 'internet_gateway', label: 'Internet Gateway', icon: '🌍', group: 'network' },
    { type: 'cdn', label: 'CDN', icon: '🛰️', group: 'network' },
    { type: 'firewall', label: 'Firewall', icon: '🛡️', group: 'network' },
    { type: 'database', label: 'Database', icon: '🗄️', group: 'storage' },
    { type: 'object_storage', label: 'Object Storage', icon: '☁️', group: 'storage' },
    { type: 'block_storage', label: 'Block Storage', icon: '💿', group: 'storage' },
    { type: 'cache', label: 'Cache / Redis', icon: '⚡', group: 'storage' },
    { type: 'cicd', label: 'CI/CD Pipeline', icon: '⚙️', group: 'devops' },
    { type: 'monitoring', label: 'Monitoring', icon: '📊', group: 'devops' },
    { type: 'logging', label: 'Logging', icon: '📋', group: 'devops' },
    { type: 'auto_scaling', label: 'Auto Scaling', icon: '📈', group: 'devops' },
  ];

  canvasNodes: CanvasNode[] = [];
  canvasEdges: CanvasEdge[] = [];
  requirementsStatus: Record<string, boolean> = {};

  isSubmitting = false;
  diagramId: number | null = null;
  answerId: number | null = null;
  architectureScore: ArchitectureScore | null = null;
  showExplanationPanel = false;
  explanation = '';
  isSubmittingExplanation = false;
  connectingFrom: string | null = null;
  selectedEdgeId: string | null = null;
  animatedScorePercent = 0;

  private mousePoint = { x: 0, y: 0 };
  private pollRef: ReturnType<typeof setInterval> | null = null;
  private pollTimeoutRef: ReturnType<typeof setTimeout> | null = null;
  private scoreAnimRef: ReturnType<typeof setInterval> | null = null;
  private nodeCounter = 0;

  readonly scoreRingRadius = 52;
  readonly scoreRingCircumference = 2 * Math.PI * this.scoreRingRadius;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['question']) {
      this.resetBoard();
    }

    if (changes['metadata'] || changes['question']) {
      this.initializeRequirementStatus();
      this.checkRequirements();
    }
  }

  ngOnDestroy(): void {
    this.clearPolling();
    if (this.scoreAnimRef) {
      clearInterval(this.scoreAnimRef);
      this.scoreAnimRef = null;
    }
  }

  get requirements(): RequirementItem[] {
    if (!Array.isArray(this.metadata?.requirements)) {
      return [];
    }

    return this.metadata?.requirements ?? [];
  }

  get metCount(): number {
    return Object.values(this.requirementsStatus).filter((value) => value).length;
  }

  get totalCount(): number {
    return this.requirements.length;
  }

  get estimatedScore(): number {
    if (this.totalCount === 0) {
      return 0;
    }

    return Math.round((this.metCount / this.totalCount) * 100);
  }

  get estimatedScoreColor(): string {
    if (this.estimatedScore <= 40) {
      return '#FC8181';
    }
    if (this.estimatedScore <= 70) {
      return '#F6E05E';
    }

    return '#68D391';
  }

  get metCountClass(): string {
    if (this.totalCount === 0 || this.metCount === 0) {
      return 'met-count met-count--danger';
    }

    if (this.metCount < this.totalCount) {
      return 'met-count met-count--warning';
    }

    return 'met-count met-count--success';
  }

  get scorePercent(): number {
    if (!this.architectureScore) {
      return 0;
    }

    const designScore = this.architectureScore.designScore;
    const scaled = designScore <= 10 ? designScore * 10 : designScore;
    return Math.max(0, Math.min(100, Math.round(scaled)));
  }

  get scoreStrokeColor(): string {
    if (this.animatedScorePercent > 70) {
      return '#68D391';
    }

    if (this.animatedScorePercent > 50) {
      return '#F6E05E';
    }

    return '#FC8181';
  }

  get scoreRingOffset(): number {
    return this.scoreRingCircumference * (1 - this.animatedScorePercent / 100);
  }

  get previewPath(): string {
    if (!this.connectingFrom) {
      return '';
    }

    const fromNode = this.canvasNodes.find((node) => node.id === this.connectingFrom);
    if (!fromNode) {
      return '';
    }

    const x1 = fromNode.x + 70;
    const y1 = fromNode.y + 45;
    const x2 = this.mousePoint.x;
    const y2 = this.mousePoint.y;
    const cx = (x1 + x2) / 2;

    return `M ${x1} ${y1} Q ${cx} ${y1} ${x2} ${y2}`;
  }

  getGroupComponents(group: PaletteComponent['group']): PaletteComponent[] {
    return this.paletteComponents.filter((component) => component.group === group);
  }

  canEnterCanvas = (drag: { data: unknown }): boolean => {
    const data = drag.data as PaletteComponent | undefined;
    return !!data?.type;
  };

  onDrop(event: CdkDragDrop<CanvasNode[], CanvasNode[], PaletteComponent>): void {
    const componentType = event.item.data;
    if (!componentType?.type || !this.canvasEl) {
      return;
    }

    const canvasRect = this.canvasEl.nativeElement.getBoundingClientRect();
    const x = event.dropPoint.x - canvasRect.left - 70;
    const y = event.dropPoint.y - canvasRect.top - 30;

    const boundedX = Math.max(0, Math.min(canvasRect.width - 140, x));
    const boundedY = Math.max(0, Math.min(canvasRect.height - 90, y));

    this.nodeCounter += 1;

    const newNode: CanvasNode = {
      id: `node_${Date.now()}_${this.nodeCounter}`,
      type: componentType.type,
      label: componentType.label,
      icon: componentType.icon,
      group: componentType.group,
      x: boundedX,
      y: boundedY,
    };

    this.canvasNodes = [...this.canvasNodes, newNode];
    this.checkRequirements();
  }

  onNodeDragEnded(node: CanvasNode, event: CdkDragEnd): void {
    if (!this.canvasEl) {
      return;
    }

    const canvasRect = this.canvasEl.nativeElement.getBoundingClientRect();
    const draggedRect = event.source.element.nativeElement.getBoundingClientRect();

    const boundedX = Math.max(0, Math.min(canvasRect.width - 140, draggedRect.left - canvasRect.left));
    const boundedY = Math.max(0, Math.min(canvasRect.height - 90, draggedRect.top - canvasRect.top));

    this.canvasNodes = this.canvasNodes.map((item) => item.id === node.id
      ? { ...item, x: boundedX, y: boundedY }
      : item);

    event.source.reset();
  }

  removeNode(nodeId: string, event?: MouseEvent): void {
    event?.stopPropagation();
    this.canvasNodes = this.canvasNodes.filter((node) => node.id !== nodeId);
    this.canvasEdges = this.canvasEdges.filter((edge) => edge.from !== nodeId && edge.to !== nodeId);

    if (this.connectingFrom === nodeId) {
      this.connectingFrom = null;
    }

    this.checkRequirements();
  }

  startConnecting(nodeId: string, event: MouseEvent): void {
    event.stopPropagation();
    this.connectingFrom = nodeId;
    this.selectedEdgeId = null;
  }

  onHandleMouseDown(event: MouseEvent): void {
    event.preventDefault();
    event.stopPropagation();
  }

  connectToNode(nodeId: string, event?: MouseEvent): void {
    event?.stopPropagation();

    if (this.connectingFrom && this.connectingFrom !== nodeId) {
      const exists = this.canvasEdges.some((edge) =>
        (edge.from === this.connectingFrom && edge.to === nodeId)
        || (edge.from === nodeId && edge.to === this.connectingFrom)
      );

      if (!exists) {
        this.canvasEdges = [...this.canvasEdges, {
          id: `edge_${Date.now()}_${this.canvasEdges.length + 1}`,
          from: this.connectingFrom,
          to: nodeId,
        }];
      }
    }

    this.connectingFrom = null;
  }

  onCanvasMouseMove(event: MouseEvent): void {
    if (!this.canvasEl) {
      return;
    }

    const canvasRect = this.canvasEl.nativeElement.getBoundingClientRect();
    this.mousePoint = {
      x: event.clientX - canvasRect.left,
      y: event.clientY - canvasRect.top,
    };
  }

  onCanvasClick(): void {
    this.connectingFrom = null;
  }

  clearCanvas(): void {
    this.canvasNodes = [];
    this.canvasEdges = [];
    this.connectingFrom = null;
    this.selectedEdgeId = null;
    this.architectureScore = null;
    this.showExplanationPanel = false;
    this.animatedScorePercent = 0;
    this.initializeRequirementStatus();
  }

  autoLayout(): void {
    if (!this.canvasNodes.length) {
      return;
    }

    const cols = Math.ceil(Math.sqrt(this.canvasNodes.length));
    this.canvasNodes = this.canvasNodes.map((node, index) => ({
      ...node,
      x: (index % cols) * 180 + 40,
      y: Math.floor(index / cols) * 160 + 40,
    }));
  }

  checkRequirements(): void {
    const usedTypes = new Set(this.canvasNodes.map((node) => node.type));

    this.requirementsStatus = this.requirements.reduce<Record<string, boolean>>((acc, req) => {
      acc[req.key] = usedTypes.has(req.key);
      return acc;
    }, {});
  }

  buildDiagramJson(): string {
    return JSON.stringify({
      nodes: this.canvasNodes,
      edges: this.canvasEdges,
    });
  }

  getEdgePath(edge: CanvasEdge): string {
    const fromNode = this.canvasNodes.find((node) => node.id === edge.from);
    const toNode = this.canvasNodes.find((node) => node.id === edge.to);

    if (!fromNode || !toNode) {
      return '';
    }

    const x1 = fromNode.x + 70;
    const y1 = fromNode.y + 45;
    const x2 = toNode.x + 70;
    const y2 = toNode.y;
    const cx = (x1 + x2) / 2;

    return `M ${x1} ${y1} Q ${cx} ${y1} ${x2} ${y2}`;
  }

  submitDiagram(): void {
    if (this.isSubmitting || this.canvasNodes.length < 2 || !this.question) {
      return;
    }

    this.isSubmitting = true;

    this.http.post<any>(`${this.apiBase}/answers/submit`, {
      sessionId: this.sessionId,
      questionId: this.question.id,
      answerText: 'Architecture diagram submitted',
      videoUrl: null,
      audioUrl: null,
    }).subscribe({
      next: (answer) => {
        this.answerId = Number(answer?.id) || null;

        if (!this.answerId) {
          this.isSubmitting = false;
          return;
        }

        this.http.post<any>(`${this.apiBase}/diagrams/submit`, {
          answerId: this.answerId,
          sessionId: this.sessionId,
          questionId: this.question?.id,
          diagramJson: this.buildDiagramJson(),
        }).subscribe({
          next: (diagram) => {
            this.diagramId = Number(diagram?.diagramId ?? diagram?.id) || null;
            this.isSubmitting = false;

            if (this.answerId) {
              this.pollForScore(this.answerId);
            }
          },
          error: () => {
            this.isSubmitting = false;
          },
        });
      },
      error: () => {
        this.isSubmitting = false;
      },
    });
  }

  pollForScore(answerId: number): void {
    this.clearPolling();

    this.pollRef = setInterval(() => {
      this.http.get<any>(`${this.apiBase}/diagrams/answer/${answerId}`).subscribe({
        next: (diagram) => {
          if (diagram?.designScore !== null && diagram?.designScore !== undefined) {
            this.clearPolling();
            this.architectureScore = this.normalizeArchitectureScore(diagram);
            this.animateScoreRing(this.scorePercent);
            this.revealExplanationPanel();
          }
        },
        error: () => {
          // Keep polling until timeout.
        },
      });
    }, 3000);

    this.pollTimeoutRef = setTimeout(() => {
      this.clearPolling();

      if (!this.architectureScore) {
        this.architectureScore = this.buildFallbackScore();
        this.animateScoreRing(this.scorePercent);
        this.revealExplanationPanel();
      }
    }, 60000);
  }

  speakExplanationPrompt(): void {
    const prompt = 'Great design. Now walk me through your architecture. Explain why you chose each component and how they work together.';

    this.http.post<any>(`${this.apiRoot}/audio/tts/speak`, { text: prompt }).subscribe({
      next: (response) => {
        if (!response?.audioUrl) {
          return;
        }

        const audioUrl = this.interviewApi.resolveBackendAssetUrl(response.audioUrl);
        const deleteUrl = this.interviewApi.resolveBackendAssetUrl(response.audioUrl);
        let cleaned = false;
        let cleanupTimer: ReturnType<typeof setTimeout> | null = null;

        const cleanup = (): void => {
          if (cleaned) {
            return;
          }

          cleaned = true;
          if (cleanupTimer) {
            clearTimeout(cleanupTimer);
            cleanupTimer = null;
          }
          this.http.delete(deleteUrl).subscribe({ error: () => undefined });
        };

        cleanupTimer = setTimeout(() => cleanup(), 4000);
        this.ttsService.playAbsoluteUrl(audioUrl).then(() => cleanup()).catch(() => cleanup());
      },
      error: () => {
        // Speech prompt is best-effort.
      },
    });
  }

  submitExplanation(): void {
    if (!this.explanation.trim() || this.isSubmittingExplanation || !this.diagramId || !this.question) {
      return;
    }

    this.isSubmittingExplanation = true;

    this.http.post(
      `${this.apiBase}/diagrams/${this.diagramId}/explain`,
      {
        sessionId: this.sessionId,
        questionId: this.question.id,
        explanation: this.explanation.trim(),
      }
    ).subscribe({
      next: () => {
        this.isSubmittingExplanation = false;
        this.answerSubmitted.emit({ id: this.answerId, source: 'cloud-canvas' });
      },
      error: () => {
        this.isSubmittingExplanation = false;
      },
    });
  }

  onExplanationAudio(blob: Blob): void {
    const formData = new FormData();
    formData.append('audio', blob, 'explanation.webm');

    this.http.post<any>(`${this.apiBase}/answers/transcribe-only`, formData).subscribe({
      next: (response) => {
        if (response?.transcript) {
          this.explanation = String(response.transcript);
        }
      },
      error: () => {
        // Typed explanation remains available even when transcription fails.
      },
    });
  }

  isRequirementMetByAi(key: string): boolean {
    return !!this.architectureScore?.requirementsMet.includes(key);
  }

  private animateScoreRing(target: number): void {
    if (this.scoreAnimRef) {
      clearInterval(this.scoreAnimRef);
      this.scoreAnimRef = null;
    }

    this.animatedScorePercent = 0;
    const steps = 24;
    const increment = target / steps;
    let current = 0;

    this.scoreAnimRef = setInterval(() => {
      current += 1;
      this.animatedScorePercent = Math.min(target, Math.round(current * increment));

      if (current >= steps) {
        if (this.scoreAnimRef) {
          clearInterval(this.scoreAnimRef);
          this.scoreAnimRef = null;
        }
      }
    }, 30);
  }

  private normalizeArchitectureScore(raw: any): ArchitectureScore {
    const designScore = Number(raw?.designScore ?? raw?.aiDesignScore ?? 0);
    const requirementsMet = this.parseStringArray(raw?.requirementsMet);
    const requirementsMissed = this.parseStringArray(raw?.requirementsMissed);

    return {
      id: Number(raw?.id ?? 0),
      designScore,
      requirementsMet,
      requirementsMissed,
      feedback: this.parseFeedbackSections(String(raw?.aiFeedback ?? '')),
    };
  }

  private parseStringArray(value: unknown): string[] {
    if (Array.isArray(value)) {
      return value.map((item) => String(item));
    }

    if (typeof value !== 'string' || !value.trim()) {
      return [];
    }

    try {
      const parsed = JSON.parse(value) as unknown;
      if (Array.isArray(parsed)) {
        return parsed.map((item) => String(item));
      }
    } catch {
      return [];
    }

    return [];
  }

  private parseFeedbackSections(feedback: string): ParsedFeedback {
    const strengths = this.extractSection(feedback, 'Strengths', 'Weaknesses') || feedback || 'Evaluation received.';
    const weaknesses = this.extractSection(feedback, 'Weaknesses', 'Recommendations') || 'No explicit weaknesses were returned.';
    const recommendations = this.extractSection(feedback, 'Recommendations', '') || 'No recommendations were returned.';

    return {
      strengths,
      weaknesses,
      recommendations,
    };
  }

  private extractSection(text: string, startLabel: string, endLabel: string): string {
    if (!text) {
      return '';
    }

    const startRegex = new RegExp(`${startLabel}:`, 'i');
    const startMatch = startRegex.exec(text);
    if (!startMatch || startMatch.index === undefined) {
      return '';
    }

    const startIndex = startMatch.index + startMatch[0].length;
    const rest = text.slice(startIndex).trim();

    if (!endLabel) {
      return rest;
    }

    const endRegex = new RegExp(`${endLabel}:`, 'i');
    const endMatch = endRegex.exec(rest);
    if (!endMatch || endMatch.index === undefined) {
      return rest;
    }

    return rest.slice(0, endMatch.index).trim();
  }

  private initializeRequirementStatus(): void {
    this.requirementsStatus = this.requirements.reduce<Record<string, boolean>>((acc, req) => {
      acc[req.key] = false;
      return acc;
    }, {});
  }

  private resetBoard(): void {
    this.canvasNodes = [];
    this.canvasEdges = [];
    this.connectingFrom = null;
    this.selectedEdgeId = null;
    this.architectureScore = null;
    this.diagramId = null;
    this.answerId = null;
    this.explanation = '';
    this.showExplanationPanel = false;
    this.animatedScorePercent = 0;
    this.clearPolling();
  }

  private clearPolling(): void {
    if (this.pollRef) {
      clearInterval(this.pollRef);
      this.pollRef = null;
    }

    if (this.pollTimeoutRef) {
      clearTimeout(this.pollTimeoutRef);
      this.pollTimeoutRef = null;
    }
  }

  private buildFallbackScore(): ArchitectureScore {
    const met = Object.entries(this.requirementsStatus)
      .filter(([, isMet]) => isMet)
      .map(([key]) => key);
    const missed = Object.entries(this.requirementsStatus)
      .filter(([, isMet]) => !isMet)
      .map(([key]) => key);
    const scoreOutOfTen = Number((this.estimatedScore / 10).toFixed(2));

    return {
      id: this.diagramId ?? 0,
      designScore: scoreOutOfTen,
      requirementsMet: met,
      requirementsMissed: missed,
      feedback: {
        strengths: 'Your architecture includes key components and demonstrates solid decomposition.',
        weaknesses: 'AI evaluation is delayed. Add missing requirements and explicit fault-tolerance links.',
        recommendations: 'Explain trade-offs, scaling strategy, and recovery design in your walkthrough.',
      },
    };
  }

  private revealExplanationPanel(): void {
    if (this.showExplanationPanel) {
      return;
    }

    this.showExplanationPanel = true;
    this.speakExplanationPrompt();
  }

  private resolveBaseUrl(): string {
    const configured = (globalThis.localStorage?.getItem('smarthire.interviewApiBaseUrl') ?? '').trim();
    if (configured) {
      return configured.replace(/\/+$/, '');
    }

    return '/interview-service/api/v1';
  }

  private resolveApiRoot(apiBase: string): string {
    if (!apiBase) {
      return '';
    }

    return apiBase.replace(/\/api\/v1\/?$/, '');
  }
}
