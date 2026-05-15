import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';

interface CloudQuestionView {
  domain: string;
  category: string;
  questionText: string;
}

interface InfraNode {
  id: string;
  label: string;
  x: number;
  y: number;
}

interface InfraEdge {
  from: string;
  to: string;
}

@Component({
  selector: 'app-interview-cloud-screen',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './interview-cloud-screen.component.html',
  styleUrl: './interview-cloud-screen.component.scss',
})
export class InterviewCloudScreenComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private timerRef: ReturnType<typeof setInterval> | null = null;

  readonly sessionId = signal<number | null>(null);
  readonly elapsedSeconds = signal(0);
  readonly currentIndex = signal(0);
  readonly totalQuestions = signal(8);
  readonly mode = signal<'PRACTICE' | 'TEST'>('PRACTICE');

  readonly question = signal<CloudQuestionView>({
    domain: 'Cloud Architecture',
    category: 'Scalability',
    questionText:
      'Design a globally available API architecture for an e-commerce platform that handles traffic spikes, minimizes latency, and controls cloud costs.',
  });

  readonly answerText = signal('');
  readonly hintOpen = signal(false);
  readonly submitFeedbackOpen = signal(false);
  readonly submitFeedbackText = signal('');

  readonly builderOpen = signal(false);
  readonly paletteItems = ['Load Balancer', 'API Server', 'Database', 'Redis Cache', 'CDN', 'Kafka Queue', 'S3 Bucket', 'Auto Scaler'];
  readonly nodes = signal<InfraNode[]>([]);
  readonly edges = signal<InfraEdge[]>([]);
  readonly selectedNodeId = signal<string | null>(null);
  readonly exportedJson = signal<string>('');
  readonly designScore = signal<number | null>(null);

  readonly timerDisplay = computed(() => this.toClock(this.elapsedSeconds()));
  readonly progressPercent = computed(() => ((this.currentIndex() + 1) / this.totalQuestions()) * 100);
  readonly badgeLabel = computed(() => this.deriveBadge(this.question().category));

  ngOnInit(): void {
    const parsed = Number(this.route.snapshot.paramMap.get('id'));
    if (Number.isFinite(parsed)) {
      this.sessionId.set(parsed);
    }

    this.timerRef = setInterval(() => {
      this.elapsedSeconds.update((value) => value + 1);
    }, 1000);
  }

  ngOnDestroy(): void {
    if (this.timerRef) {
      clearInterval(this.timerRef);
      this.timerRef = null;
    }
  }

  toggleHint(): void {
    this.hintOpen.update((value) => !value);
  }

  submitAnswer(): void {
    const text = this.answerText().trim();
    if (!text) {
      return;
    }

    this.submitFeedbackOpen.set(true);
    this.submitFeedbackText.set('Feedback preview: good architectural direction. Add failover and multi-region data strategy.');
  }

  toggleBuilder(): void {
    this.builderOpen.update((value) => !value);
  }

  onPaletteDragStart(event: DragEvent, componentLabel: string): void {
    event.dataTransfer?.setData('text/plain', componentLabel);
  }

  onCanvasDrop(event: DragEvent): void {
    event.preventDefault();
    const label = event.dataTransfer?.getData('text/plain');
    if (!label) {
      return;
    }

    const rect = (event.currentTarget as HTMLElement).getBoundingClientRect();
    const x = Math.max(24, event.clientX - rect.left);
    const y = Math.max(24, event.clientY - rect.top);

    const node: InfraNode = {
      id: `${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
      label,
      x,
      y,
    };

    this.nodes.update((list) => [...list, node]);
  }

  allowDrop(event: DragEvent): void {
    event.preventDefault();
  }

  selectNode(nodeId: string): void {
    const selected = this.selectedNodeId();
    if (!selected) {
      this.selectedNodeId.set(nodeId);
      return;
    }

    if (selected === nodeId) {
      this.selectedNodeId.set(null);
      return;
    }

    this.edges.update((list) => [...list, { from: selected, to: nodeId }]);
    this.selectedNodeId.set(null);
  }

  getNodeById(id: string): InfraNode | null {
    return this.nodes().find((node) => node.id === id) ?? null;
  }

  exportDiagram(): void {
    const payload = {
      nodes: this.nodes(),
      edges: this.edges(),
    };

    this.exportedJson.set(JSON.stringify(payload, null, 2));
    this.designScore.set(null);
  }

  private deriveBadge(category: string): string {
    const lower = category.toLowerCase();
    if (lower.includes('scale')) {
      return 'Scalability';
    }
    if (lower.includes('cost')) {
      return 'Cost';
    }
    if (lower.includes('availability')) {
      return 'Availability';
    }
    return 'System Design';
  }

  private toClock(seconds: number): string {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
  }
}
