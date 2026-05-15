import { CommonModule } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, ElementRef, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output, SimpleChanges, ViewChild, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MonacoEditorModule } from 'ngx-monaco-editor-v2';
import { MicButtonComponent } from '../mic-button/mic-button.component';
import { CodeExecutionResponse, CodeExecutionService, TestCaseResult } from '../../services/code-execution.service';
import { InterviewQuestionDto } from '../../interview.models';
import { InterviewApiService } from '../../interview-api.service';
import { TtsService } from '../../../../../../shared/services/tts.service';

@Component({
  selector: 'app-coding-interview',
  standalone: true,
  imports: [CommonModule, FormsModule, MonacoEditorModule, MicButtonComponent],
  templateUrl: './coding-interview.component.html',
  styleUrl: './coding-interview.component.scss'
})
export class CodingInterviewComponent implements OnInit, OnChanges, OnDestroy {
  private readonly codeExecutionService = inject(CodeExecutionService);
  private readonly http = inject(HttpClient);
  private readonly interviewApi = inject(InterviewApiService);
  private readonly ttsService = inject(TtsService);
  private readonly apiBase = this.resolveBaseUrl();
  private readonly apiRoot = this.resolveApiRoot(this.apiBase);

  @Input() question: InterviewQuestionDto | null = null;
  @Input() metadata: any;
  @Input() sessionId = 0;
  @Output() answerSubmitted = new EventEmitter<any>();
  @ViewChild('testResultsSection') testResultsSection?: ElementRef;

  selectedLanguage = 'java';
  editorCode = '';
  editorOptions: any = {
    theme: 'vs-dark',
    language: 'java',
    fontSize: 14,
    minimap: { enabled: false },
    scrollBeyondLastLine: false,
    automaticLayout: true,
    tabSize: 4,
    lineNumbers: 'on',
    renderLineHighlight: 'line',
    wordWrap: 'on',
  };

  isRunning = false;
  hasRunOnce = false;
  testCaseResults: TestCaseResult[] = [];
  passedCount = 0;
  totalCount = 0;
  executionStatus = '';
  executionError = '';
  compilationError = '';

  showExplanationPanel = false;
  explanation = '';
  isSubmitting = false;
  answerId: number | null = null;
  aiSpeaking = false;
  copiedIndex: number | null = null;
  private lastQuestionId: number | null = null;
  monacoReady = false;
  showPlainEditor = false;
  private monacoInitFallbackTimer: ReturnType<typeof setTimeout> | null = null;

  ngOnInit(): void {
    this.editorCode = this.buildStarterCode(this.selectedLanguage);
    this.totalCount = this.resolveTotalCountFromMetadata();
    this.lastQuestionId = this.question?.id ?? null;
    this.scheduleMonacoFallback();
  }

  ngOnDestroy(): void {
    this.clearMonacoFallbackTimer();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (!changes['question']) {
      return;
    }

    const nextQuestionId = this.question?.id ?? null;
    const previousQuestionId = this.lastQuestionId;
    const hasQuestionChanged = nextQuestionId !== previousQuestionId;

    this.lastQuestionId = nextQuestionId;

    if (!hasQuestionChanged) {
      return;
    }

    this.resetUiForNextQuestion();
  }

  get filename(): string {
    if (this.selectedLanguage === 'python') {
      return 'solution.py';
    }
    if (this.selectedLanguage === 'javascript') {
      return 'solution.js';
    }
    return 'solution.java';
  }

  get difficultyBadgeClass(): string {
    const diff = this.question?.difficulty;
    if (diff === 'BEGINNER') {
      return 'diff-beginner';
    }
    if (diff === 'INTERMEDIATE') {
      return 'diff-intermediate';
    }
    return 'diff-advanced';
  }

  get questionMetadata(): any {
    return this.metadata || {};
  }

  get currentQuestion(): InterviewQuestionDto | null {
    return this.question;
  }

  onLanguageChange(lang: string): void {
    this.selectedLanguage = lang;
    this.editorOptions = { ...this.editorOptions, language: lang };
    this.editorCode = this.buildStarterCode(lang);
    this.testCaseResults = [];
    this.hasRunOnce = false;
    this.passedCount = 0;
    this.totalCount = this.resolveTotalCountFromMetadata();
    this.executionStatus = '';
    this.executionError = '';
    this.compilationError = '';
  }

  onEditorInit(editor: any): void {
    this.monacoReady = true;
    this.showPlainEditor = false;
    this.clearMonacoFallbackTimer();

    try {
      editor?.updateOptions?.({
        readOnly: false,
        domReadOnly: false,
      });
      editor?.focus?.();
    } catch {
      // Keep default editor behavior if Monaco instance shape changes.
    }
  }

  runCode(): void {
    if (!this.question || this.isRunning) {
      return;
    }

    this.isRunning = true;
    this.testCaseResults = [];
    this.executionStatus = '';
    this.executionError = '';
    this.compilationError = '';

    this.codeExecutionService
      .runCode(this.answerId || 0, this.question.id, this.editorCode, this.selectedLanguage)
      .subscribe({
        next: (result: CodeExecutionResponse) => {
          this.isRunning = false;
          this.testCaseResults = result.testCaseResults || [];
          this.passedCount = result.passedCount || 0;
          this.totalCount = result.totalCount || this.testCaseResults.length;
          this.hasRunOnce = true;
          this.compilationError = (result.stderr || '').trim();

          const details = this.extractErrorDetails(result);
          this.executionStatus = details.status;
          this.executionError = details.message;

          setTimeout(() => {
            if (this.testResultsSection?.nativeElement) {
              this.testResultsSection.nativeElement.scrollIntoView({
                behavior: 'smooth',
                block: 'start',
              });
            }
          }, 150);
        },
        error: (error: HttpErrorResponse) => {
          this.isRunning = false;
          this.hasRunOnce = true;
          this.executionStatus = 'Execution Error';
          this.executionError = this.extractHttpErrorMessage(error);
          this.compilationError = this.executionError;
        },
      });
  }

  get hasExecutionProblem(): boolean {
    return !!this.executionError.trim().length;
  }

  copyInput(input: string, index: number): void {
    const value = input || '';
    const markCopied = (): void => {
      this.copiedIndex = index;
      setTimeout(() => {
        if (this.copiedIndex === index) {
          this.copiedIndex = null;
        }
      }, 2000);
    };

    const canUseClipboard = !!globalThis.navigator?.clipboard?.writeText;
    if (canUseClipboard) {
      globalThis.navigator.clipboard.writeText(value)
        .then(() => markCopied())
        .catch(() => this.copyInputFallback(value, markCopied));
      return;
    }

    this.copyInputFallback(value, markCopied);
  }

  openExplanationPanel(): void {
    this.showExplanationPanel = true;
    this.aiSpeaking = true;

    const prompt = 'Great. Now please explain your approach. Walk me through your solution, the data structure you chose, and the time and space complexity of your algorithm.';

    this.http
      .post(`${this.apiRoot}/audio/tts/speak`, { text: prompt })
      .subscribe({
        next: (response: any) => {
          if (!response?.audioUrl) {
            this.aiSpeaking = false;
            return;
          }

          let cleanedUp = false;
          let cleanupTimer: ReturnType<typeof setTimeout> | null = null;
          const audioDeleteUrl = this.interviewApi.resolveBackendAssetUrl(response.audioUrl);

          const cleanupAudio = (): void => {
            if (cleanedUp) {
              return;
            }

            cleanedUp = true;
            this.aiSpeaking = false;

            if (cleanupTimer) {
              clearTimeout(cleanupTimer);
              cleanupTimer = null;
            }

            this.http.delete(audioDeleteUrl).subscribe({
              error: () => {
                // Cleanup failure is non-blocking.
              },
            });
          };

          const absoluteAudioUrl = this.interviewApi.resolveBackendAssetUrl(response.audioUrl);
          cleanupTimer = setTimeout(() => cleanupAudio(), 20000);
          this.ttsService.playAbsoluteUrl(absoluteAudioUrl).then(() => cleanupAudio()).catch(() => cleanupAudio());
        },
        error: () => {
          this.aiSpeaking = false;
        },
      });
  }

  onExplanationAudio(blob: Blob): void {
    const formData = new FormData();
    formData.append('audio', blob, 'explanation.webm');

    this.http.post(`${this.apiBase}/answers/transcribe-only`, formData).subscribe({
      next: (res: any) => {
        if (res?.transcript) {
          this.explanation = res.transcript;
        }
      },
      error: () => {
        // Endpoint may be unavailable; typed explanation remains usable.
      },
    });
  }

  submitFinalAnswer(): void {
    if (!this.question || this.isSubmitting || !this.explanation.trim()) {
      return;
    }

    this.isSubmitting = true;

    this.codeExecutionService
      .submitFinal(this.sessionId, this.question.id, this.editorCode, this.selectedLanguage, this.explanation)
      .subscribe({
        next: (answer) => {
          this.isSubmitting = false;
          this.answerId = answer?.id ?? this.answerId;
          this.answerSubmitted.emit(answer);
        },
        error: () => {
          this.isSubmitting = false;
        },
      });
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

  private resetUiForNextQuestion(): void {
    this.selectedLanguage = 'java';
    this.editorOptions = { ...this.editorOptions, language: this.selectedLanguage };
    this.editorCode = this.buildStarterCode(this.selectedLanguage);

    this.isRunning = false;
    this.hasRunOnce = false;
    this.testCaseResults = [];
    this.passedCount = 0;
    this.totalCount = this.resolveTotalCountFromMetadata();
    this.executionStatus = '';
    this.executionError = '';
    this.compilationError = '';

    this.showExplanationPanel = false;
    this.explanation = '';
    this.isSubmitting = false;
    this.aiSpeaking = false;

    this.copiedIndex = null;
    this.answerId = null;
  }

  private scheduleMonacoFallback(): void {
    this.clearMonacoFallbackTimer();
    this.monacoInitFallbackTimer = setTimeout(() => {
      if (!this.monacoReady) {
        this.showPlainEditor = true;
      }
    }, 2500);
  }

  private clearMonacoFallbackTimer(): void {
    if (!this.monacoInitFallbackTimer) {
      return;
    }

    clearTimeout(this.monacoInitFallbackTimer);
    this.monacoInitFallbackTimer = null;
  }

  private copyInputFallback(value: string, onCopied: () => void): void {
    const textarea = document.createElement('textarea');
    textarea.value = value;
    textarea.style.position = 'fixed';
    textarea.style.opacity = '0';
    document.body.appendChild(textarea);
    textarea.focus();
    textarea.select();

    try {
      document.execCommand('copy');
      onCopied();
    } catch {
      this.copiedIndex = null;
    } finally {
      document.body.removeChild(textarea);
    }
  }

  private extractErrorDetails(result: CodeExecutionResponse): { status: string; message: string } {
    const topLevelStatus = (result.statusDescription || '').trim();
    const topLevelError = (result.stderr || '').trim();

    if (topLevelError) {
      return {
        status: topLevelStatus || 'Execution Error',
        message: topLevelError,
      };
    }

    const failing = (result.testCaseResults || []).find((test) => {
      const status = (test.statusDescription || '').trim().toLowerCase();
      const stderr = (test.stderr || '').trim();
      const output = (test.actualOutput || '').trim();
      return !!stderr || status.includes('error') || status.includes('internal') || (!test.passed && !!output);
    });

    if (!failing) {
      return { status: '', message: '' };
    }

    const failingStatus = (failing.statusDescription || '').trim();
    const failingError = (failing.stderr || '').trim();
    const fallbackOutput = (failing.actualOutput || '').trim();

    return {
      status: failingStatus || topLevelStatus || 'Execution Error',
      message: failingError || fallbackOutput,
    };
  }

  private extractHttpErrorMessage(error: HttpErrorResponse): string {
    const payload = error.error;

    if (typeof payload === 'string' && payload.trim().length) {
      return payload.trim();
    }

    if (payload && typeof payload === 'object') {
      if (typeof payload.stderr === 'string' && payload.stderr.trim().length) {
        return payload.stderr.trim();
      }

      if (typeof payload.message === 'string' && payload.message.trim().length) {
        return payload.message.trim();
      }
    }

    return 'Execution failed. Please check your code and try again.';
  }

  private buildStarterCode(language: string): string {
    const normalizedLanguage = (language || '').toLowerCase();
    const key = this.resolveProblemKey();

    if (normalizedLanguage === 'java') {
      return this.question?.sampleCode || 'class Solution {\n    // TODO: implement\n}\n';
    }

    if (normalizedLanguage === 'python') {
      return this.pythonStarterByKey(key);
    }

    if (normalizedLanguage === 'javascript') {
      return this.javascriptStarterByKey(key);
    }

    return this.question?.sampleCode || '';
  }

  private resolveTotalCountFromMetadata(): number {
    const tests = this.metadata?.testCases;
    if (Array.isArray(tests) && tests.length > 0) {
      return tests.length;
    }

    const tryThese = this.metadata?.tryThese;
    if (Array.isArray(tryThese) && tryThese.length > 0) {
      return tryThese.length;
    }

    return 0;
  }

  private resolveProblemKey(): string {
    const signature = (this.metadata?.functionSignature || '').toString().toLowerCase();
    if (signature.includes('twosum')) {
      return 'twoSum';
    }
    if (signature.includes('ispalindrome')) {
      return 'isPalindrome';
    }
    if (signature.includes('isvalid')) {
      return 'isValid';
    }
    if (signature.includes('maxsubarray')) {
      return 'maxSubArray';
    }
    if (signature.includes('reverselist')) {
      return 'reverseList';
    }
    if (signature.includes('findduplicates')) {
      return 'findDuplicates';
    }
    if (signature.includes('minstack')) {
      return 'minStack';
    }
    if (signature.includes('merge(')) {
      return 'mergeIntervals';
    }
    if (signature.includes('uniquepaths')) {
      return 'uniquePaths';
    }
    if (signature.includes('lrucache')) {
      return 'lruCache';
    }

    const questionText = (this.question?.questionText || '').toLowerCase();
    if (questionText.includes('two numbers that add up')) {
      return 'twoSum';
    }
    if (questionText.includes('palindrome')) {
      return 'isPalindrome';
    }
    if (questionText.includes('bracket sequence')) {
      return 'isValid';
    }
    if (questionText.includes('largest sum')) {
      return 'maxSubArray';
    }
    if (questionText.includes('reverse a singly linked list')) {
      return 'reverseList';
    }
    if (questionText.includes('find all duplicates')) {
      return 'findDuplicates';
    }
    if (questionText.includes('minimum element in constant time')) {
      return 'minStack';
    }
    if (questionText.includes('merge all overlapping intervals')) {
      return 'mergeIntervals';
    }
    if (questionText.includes('number of unique paths')) {
      return 'uniquePaths';
    }
    if (questionText.includes('least recently used cache')) {
      return 'lruCache';
    }

    return 'generic';
  }

  private pythonStarterByKey(key: string): string {
    if (key === 'twoSum') {
      return [
        'class Solution:',
        '    def twoSum(self, nums, target):',
        '        # TODO: implement',
        '        return []',
      ].join('\n');
    }

    if (key === 'isPalindrome') {
      return [
        'def isPalindrome(s):',
        '    # TODO: implement',
        '    return True',
      ].join('\n');
    }

    if (key === 'isValid') {
      return [
        'def isValid(s):',
        '    # TODO: implement',
        '    return True',
      ].join('\n');
    }

    if (key === 'maxSubArray') {
      return [
        'def maxSubArray(nums):',
        '    # TODO: implement',
        '    return 0',
      ].join('\n');
    }

    if (key === 'reverseList') {
      return [
        'class ListNode:',
        '    def __init__(self, val=0, next=None):',
        '        self.val = val',
        '        self.next = next',
        '',
        'def reverseList(head):',
        '    # TODO: implement',
        '    return None',
      ].join('\n');
    }

    if (key === 'findDuplicates') {
      return [
        'def findDuplicates(nums):',
        '    # TODO: implement',
        '    return []',
      ].join('\n');
    }

    if (key === 'minStack') {
      return [
        'class MinStack:',
        '    def __init__(self):',
        '        # TODO: initialize',
        '        pass',
        '',
        '    def push(self, val):',
        '        # TODO: implement',
        '        pass',
        '',
        '    def pop(self):',
        '        # TODO: implement',
        '        pass',
        '',
        '    def top(self):',
        '        # TODO: implement',
        '        return 0',
        '',
        '    def getMin(self):',
        '        # TODO: implement',
        '        return 0',
      ].join('\n');
    }

    if (key === 'mergeIntervals') {
      return [
        'def merge(intervals):',
        '    # TODO: implement',
        '    return []',
      ].join('\n');
    }

    if (key === 'uniquePaths') {
      return [
        'def uniquePaths(m, n):',
        '    # TODO: implement',
        '    return 0',
      ].join('\n');
    }

    if (key === 'lruCache') {
      return [
        'class LRUCache:',
        '    def __init__(self, capacity):',
        '        # TODO: implement',
        '        pass',
        '',
        '    def get(self, key):',
        '        # TODO: implement',
        '        return -1',
        '',
        '    def put(self, key, value):',
        '        # TODO: implement',
        '        pass',
      ].join('\n');
    }

    return [
      '# Write your solution here',
      'def solve():',
      '    pass',
    ].join('\n');
  }

  private javascriptStarterByKey(key: string): string {
    if (key === 'twoSum') {
      return [
        'function twoSum(nums, target) {',
        '  // TODO: implement',
        '  return [];',
        '}',
      ].join('\n');
    }

    if (key === 'isPalindrome') {
      return [
        'function isPalindrome(s) {',
        '  // TODO: implement',
        '  return true;',
        '}',
      ].join('\n');
    }

    if (key === 'isValid') {
      return [
        'function isValid(s) {',
        '  // TODO: implement',
        '  return true;',
        '}',
      ].join('\n');
    }

    if (key === 'maxSubArray') {
      return [
        'function maxSubArray(nums) {',
        '  // TODO: implement',
        '  return 0;',
        '}',
      ].join('\n');
    }

    if (key === 'reverseList') {
      return [
        'class ListNode {',
        '  constructor(val = 0, next = null) {',
        '    this.val = val;',
        '    this.next = next;',
        '  }',
        '}',
        '',
        'function reverseList(head) {',
        '  // TODO: implement',
        '  return null;',
        '}',
      ].join('\n');
    }

    if (key === 'findDuplicates') {
      return [
        'function findDuplicates(nums) {',
        '  // TODO: implement',
        '  return [];',
        '}',
      ].join('\n');
    }

    if (key === 'minStack') {
      return [
        'class MinStack {',
        '  constructor() {',
        '    // TODO: initialize',
        '  }',
        '',
        '  push(val) {',
        '    // TODO: implement',
        '  }',
        '',
        '  pop() {',
        '    // TODO: implement',
        '  }',
        '',
        '  top() {',
        '    // TODO: implement',
        '    return 0;',
        '  }',
        '',
        '  getMin() {',
        '    // TODO: implement',
        '    return 0;',
        '  }',
        '}',
      ].join('\n');
    }

    if (key === 'mergeIntervals') {
      return [
        'function merge(intervals) {',
        '  // TODO: implement',
        '  return [];',
        '}',
      ].join('\n');
    }

    if (key === 'uniquePaths') {
      return [
        'function uniquePaths(m, n) {',
        '  // TODO: implement',
        '  return 0;',
        '}',
      ].join('\n');
    }

    if (key === 'lruCache') {
      return [
        'class LRUCache {',
        '  constructor(capacity) {',
        '    // TODO: implement',
        '  }',
        '',
        '  get(key) {',
        '    // TODO: implement',
        '    return -1;',
        '  }',
        '',
        '  put(key, value) {',
        '    // TODO: implement',
        '  }',
        '}',
      ].join('\n');
    }

    return [
      '// Write your solution here',
      'function solve() {}',
    ].join('\n');
  }
}
