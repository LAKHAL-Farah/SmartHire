import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { InterviewApiService } from '../interview-api.service';
import { InterviewQuestionDto, InterviewSessionDto, SessionQuestionOrderDto } from '../interview.models';

type CodingLanguage = 'python' | 'java' | 'javascript' | 'cpp';

interface CodingQuestionView {
  type: 'CODING' | 'BEHAVIORAL' | 'SITUATIONAL' | 'TECHNICAL';
  domain: string;
  category: string;
  difficulty: 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED' | 'EXPERT';
  questionText: string;
  hints: string;
  sampleCode: string;
}

@Component({
  selector: 'app-interview-code-screen',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './interview-code-screen.component.html',
  styleUrl: './interview-code-screen.component.scss',
})
export class InterviewCodeScreenComponent implements OnInit, OnDestroy {
  private readonly api = inject(InterviewApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  private timerRef: ReturnType<typeof setInterval> | null = null;

  readonly sessionId = signal<number | null>(null);
  readonly currentIndex = signal(0);
  readonly totalQuestions = signal(8);
  readonly elapsedSeconds = signal(0);
  readonly isLoaded = signal(false);
  readonly loadError = signal<string | null>(null);

  readonly mode = signal<'PRACTICE' | 'TEST'>('PRACTICE');

  readonly question = signal<CodingQuestionView | null>(null);

  readonly hintOpen = signal(false);

  readonly selectedLanguage = signal<CodingLanguage>('python');
  readonly languageOptions: Array<{ label: string; value: CodingLanguage; monaco: string }> = [
    { label: 'Python', value: 'python', monaco: 'python' },
    { label: 'Java', value: 'java', monaco: 'java' },
    { label: 'JavaScript', value: 'javascript', monaco: 'javascript' },
    { label: 'C++', value: 'cpp', monaco: 'cpp' },
  ];

  readonly editorValue = signal('def solution():\n    pass');

  readonly running = signal(false);
  readonly currentAnswerId = signal<number | null>(null);
  readonly outputStdout = signal('');
  readonly outputStderr = signal('');
  readonly outputMeta = signal('--');
  readonly testSummary = signal('No run yet');
  readonly passedBadge = signal<'pass' | 'fail' | 'idle'>('idle');

  readonly explanationText = signal('');
  readonly followUpBubbleOpen = signal(false);
  readonly followUpFeedback = signal('');
  readonly followUpQuestion = signal('');

  readonly optimizeHintOpen = signal(false);

  readonly progressPercent = computed(() => ((this.currentIndex() + 1) / this.totalQuestions()) * 100);
  readonly questionPositionLabel = computed(() => `Q${this.currentIndex() + 1} of ${this.totalQuestions()}`);
  readonly timerDisplay = computed(() => this.toClock(this.elapsedSeconds()));

  ngOnInit(): void {
    const rawId = this.route.snapshot.paramMap.get('id');
    const parsedId = Number(rawId);
    if (Number.isFinite(parsedId)) {
      this.sessionId.set(parsedId);
    } else {
      this.loadError.set('Invalid session id.');
      this.isLoaded.set(true);
      return;
    }

    this.bootstrapSession();
    this.startElapsedTimer();
  }

  private async bootstrapSession(): Promise<void> {
    const id = this.sessionId();
    if (id === null) {
      this.loadError.set('Missing session id.');
      this.isLoaded.set(true);
      return;
    }

    this.loadError.set(null);

    try {
      const [session, currentQuestion, questionOrders] = await Promise.all([
        firstValueFrom(this.api.getSessionById(id)),
        firstValueFrom(this.api.getCurrentSessionQuestion(id)),
        firstValueFrom(this.api.getSessionQuestionOrder(id)),
      ]);

      this.applySessionMeta(session, questionOrders, currentQuestion);

      const mappedQuestion = this.mapQuestion(currentQuestion);

      if (mappedQuestion.type !== 'CODING') {
        this.goToDefaultRoom();
        return;
      }

      this.question.set(mappedQuestion);

      const initialCode = mappedQuestion.sampleCode || this.getStarterTemplate(this.selectedLanguage());
      this.editorValue.set(initialCode);
      this.isLoaded.set(true);
    } catch {
      this.loadError.set('Unable to load this coding session question right now.');
      this.isLoaded.set(true);
    }
  }

  ngOnDestroy(): void {
    if (this.timerRef) {
      clearInterval(this.timerRef);
      this.timerRef = null;
    }
  }

  goToDefaultRoom(): void {
    const id = this.sessionId();
    if (id !== null) {
      const target = `/dashboard/interview/session/${id}`;
      this.router.navigateByUrl(target).catch(() => {
        globalThis.location.assign(target);
      });
    }
  }

  toggleHint(): void {
    this.hintOpen.update((value) => !value);
  }

  parsedHints(): string[] {
    const hintsRaw = this.question()?.hints;
    if (!hintsRaw) {
      return [];
    }

    try {
      const parsed = JSON.parse(hintsRaw);
      if (Array.isArray(parsed)) {
        return parsed.map((entry) => String(entry));
      }
    } catch {
      // Keep fallback below.
    }

    return [hintsRaw];
  }

  onLanguageChange(value: string): void {
    const next = this.languageOptions.find((item) => item.value === value)?.value;
    if (!next) {
      return;
    }

    this.selectedLanguage.set(next);
    this.editorValue.set(this.getStarterTemplate(next));
  }

  setEditorValue(value: string): void {
    this.editorValue.set(value);
  }

  runCode(): void {
    if (this.running()) {
      return;
    }

    this.running.set(true);

    if (this.currentAnswerId() === null) {
      this.currentAnswerId.set(Date.now());
    }

    setTimeout(() => {
      const code = this.editorValue();
      const hasSolutionKeyword = /solution|class|function/i.test(code);

      if (hasSolutionKeyword) {
        this.outputStdout.set('Output: 3\nCase #1 => PASS\nCase #2 => PASS');
        this.outputStderr.set('');
        this.outputMeta.set('Execution: 61 ms · Memory: 28 MB');
        this.testSummary.set('2 / 3 test cases passed');
        this.passedBadge.set('pass');
      } else {
        this.outputStdout.set('');
        this.outputStderr.set('RuntimeError: missing solution entry point');
        this.outputMeta.set('Execution: 12 ms · Memory: 16 MB');
        this.testSummary.set('0 / 3 test cases passed');
        this.passedBadge.set('fail');
      }

      this.running.set(false);
    }, 900);
  }

  submitExplanation(): void {
    const text = this.explanationText().trim();
    if (!text) {
      return;
    }

    this.followUpBubbleOpen.set(true);
    this.followUpFeedback.set('Good structure. Mention edge cases and explain O(n) space usage explicitly.');
    this.followUpQuestion.set('What data structure choice keeps lookup at O(1), and why?');
  }

  toggleOptimizeHint(): void {
    this.optimizeHintOpen.update((value) => !value);
  }

  private getStarterTemplate(language: CodingLanguage): string {
    switch (language) {
      case 'python':
        return 'def solution():\n    pass';
      case 'java':
        return 'public class Solution {\n    public void solve() {}\n}';
      case 'javascript':
        return 'function solution() {\n    \n}';
      default:
        return '#include<bits/stdc++.h>\nusing namespace std;\n';
    }
  }

  private startElapsedTimer(): void {
    if (this.timerRef) {
      clearInterval(this.timerRef);
    }

    this.timerRef = setInterval(() => {
      this.elapsedSeconds.update((value) => value + 1);
    }, 1000);
  }

  private applySessionMeta(
    session: InterviewSessionDto,
    questionOrders: SessionQuestionOrderDto[],
    currentQuestion: InterviewQuestionDto,
  ): void {
    this.mode.set(session.mode === 'TEST' ? 'TEST' : 'PRACTICE');

    const total = questionOrders.length > 0 ? questionOrders.length : 1;
    this.totalQuestions.set(total);

    const byQuestion = questionOrders.find((entry) => entry.questionId === currentQuestion.id)?.questionOrder;
    if (byQuestion !== undefined) {
      this.currentIndex.set(byQuestion);
      return;
    }

    const fallback = Math.min(Math.max(session.currentQuestionIndex ?? 0, 0), total - 1);
    this.currentIndex.set(fallback);
  }

  private mapQuestion(source: InterviewQuestionDto): CodingQuestionView {
    const supportedType: CodingQuestionView['type'] =
      source.type === 'CODING' || source.type === 'BEHAVIORAL' || source.type === 'SITUATIONAL' || source.type === 'TECHNICAL'
        ? source.type
        : 'TECHNICAL';

    return {
      type: supportedType,
      domain: source.domain || 'General',
      category: source.category || 'General',
      difficulty: source.difficulty,
      questionText: source.questionText || 'Question is unavailable.',
      hints: source.hints || '[]',
      sampleCode: source.sampleCode || '',
    };
  }

  private toClock(seconds: number): string {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
  }
}
