import { CommonModule } from '@angular/common';
import { AfterViewInit, Component, ElementRef, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { Chart, ChartConfiguration, TooltipItem, registerables } from 'chart.js';
import { LUCIDE_ICONS } from '../../shared/lucide-icons';
import { InterviewApiService } from '../../features/front-office/dashboard/interview/interview-api.service';
import {
  DifficultyLevel,
  InterviewQuestionDto,
  QuestionType,
  RoleType,
} from '../../features/front-office/dashboard/interview/interview.models';

type DashboardSection = 'overview' | 'analytics' | 'intelligence' | 'leaderboards' | 'questions';

interface AdminStats {
  totalSessions: number;
  sessionsToday: number;
  completionRate: number;
  totalCandidates: number;
  activeCandidatesNow: number;
  avgSessionScore: number;
  totalQuestions: number;
  activeQuestions: number;
  topStreakAllTime: number;
  totalAnswers: number;
  sessionsThisWeek?: number;
  completedSessions?: number;
  abandonedSessions?: number;
  avgSessionDuration?: number;
}

interface QuestionFormModel {
  careerPathId: number;
  questionText: string;
  roleType: RoleType;
  type: QuestionType;
  difficulty: DifficultyLevel;
  domain: string;
  category: string;
  expectedPoints: string[];
  followUps: string[];
  hints: string[];
  idealAnswer: string;
  sampleCode: string;
  isActive: boolean;
}

@Component({
  selector: 'app-interview-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, LUCIDE_ICONS],
  templateUrl: './interview-dashboard.component.html',
  styleUrl: './interview-dashboard.component.scss',
})
export class InterviewDashboardComponent implements OnInit, AfterViewInit, OnDestroy {
  private static chartRegistered = false;

  private readonly api = inject(InterviewApiService);

  @ViewChild('sessionsLineCanvas') sessionsLineCanvas?: ElementRef<HTMLCanvasElement>;
  @ViewChild('outcomeDonutCanvas') outcomeDonutCanvas?: ElementRef<HTMLCanvasElement>;
  @ViewChild('scoreDistributionCanvas') scoreDistributionCanvas?: ElementRef<HTMLCanvasElement>;
  @ViewChild('roleRadarCanvas') roleRadarCanvas?: ElementRef<HTMLCanvasElement>;
  @ViewChild('typeBarCanvas') typeBarCanvas?: ElementRef<HTMLCanvasElement>;

  stats: AdminStats | null = null;
  sessionsOverTime: any[] = [];
  scoresOverTime: any[] = [];
  scoreDistribution: any[] = [];
  byRole: any[] = [];
  byType: any[] = [];
  byDifficulty: any[] = [];
  leaderboard: any[] = [];
  topStreaks: any[] = [];
  questions: InterviewQuestionDto[] = [];
  totalQuestionCount = 0;

  questionFilters = { role: '', type: '', difficulty: '', search: '', active: '' };
  questionPage = 0;
  questionPageSize = 20;
  totalQuestionPages = 0;
  expandedQuestionId: number | null = null;

  activeSection: DashboardSection = 'overview';
  readonly sectionTabs: Array<{
    key: DashboardSection;
    label: string;
    icon: string;
    description: string;
  }> = [
    { key: 'overview', label: 'Overview', icon: 'layout-grid', description: 'Headline KPIs' },
    { key: 'analytics', label: 'Analytics', icon: 'chart-bar', description: 'Session charts' },
    { key: 'intelligence', label: 'Intelligence', icon: 'brain-circuit', description: 'Question signals' },
    { key: 'leaderboards', label: 'Leaderboards', icon: 'star', description: 'Top performers' },
    { key: 'questions', label: 'Question Bank', icon: 'circle-question-mark', description: 'CRUD and filters' },
  ];

  drawerOpen = false;
  drawerMode: 'add' | 'edit' = 'add';
  editingQuestion: InterviewQuestionDto | null = null;
  questionForm: QuestionFormModel = this.createDefaultQuestionForm();
  chipDrafts = {
    expectedPoints: '',
    followUps: '',
    hints: '',
  };

  activeIntelligenceTab: 'most-asked' | 'hardest' | 'longest-answers' | 'best-performing' = 'most-asked';
  mostAsked: any[] = [];
  hardest: any[] = [];
  longestAnswers: any[] = [];
  bestPerforming: any[] = [];

  selectedRoleFilter: string | null = null;
  isLoading = true;
  isSavingQuestion = false;
  isRefreshing = false;
  lastUpdated: Date = new Date();
  currentRangeDays = 30;
  formError = '';
  formWarning = '';
  toastMessage = '';

  coverage: any[] = [];
  coverageMatrix: Array<{ role: string; cells: Array<{ type: string; count: number; active: number }> }> = [];
  coverageMax = 1;

  private sessionsLineChart: Chart | null = null;
  private outcomeDonutChart: Chart | null = null;
  private scoreDistributionChart: Chart | null = null;
  private roleRadarChart: Chart | null = null;
  private typeBarChart: Chart | null = null;

  private searchDebounceTimer: ReturnType<typeof setTimeout> | null = null;
  private countAnimationTimerIds: number[] = [];

  constructor() {
    if (!InterviewDashboardComponent.chartRegistered) {
      Chart.register(...registerables);
      InterviewDashboardComponent.chartRegistered = true;
    }
  }

  async ngOnInit(): Promise<void> {
    await this.loadEverything();
  }

  ngAfterViewInit(): void {
    // Chart initialization is deferred until API data is loaded.
  }

  ngOnDestroy(): void {
    this.destroyCharts();
    this.clearCounterAnimations();
    if (this.searchDebounceTimer) {
      clearTimeout(this.searchDebounceTimer);
      this.searchDebounceTimer = null;
    }
  }

  async refreshAll(): Promise<void> {
    this.isRefreshing = true;
    await this.loadEverything();
    this.isRefreshing = false;
    this.showToast('Dashboard refreshed');
  }

  async onRangeChange(days: number): Promise<void> {
    this.currentRangeDays = days;
    await this.loadSessionsOverTime(days);
    this.createSessionsLineChart();
  }

  async onIntelligenceTabChange(tab: 'most-asked' | 'hardest' | 'longest-answers' | 'best-performing'): Promise<void> {
    this.activeIntelligenceTab = tab;
    if (tab === 'longest-answers' && this.longestAnswers.length === 0) {
      await this.loadLongestAnswers();
    }
    if (tab === 'best-performing' && this.bestPerforming.length === 0) {
      await this.loadBestPerforming();
    }
  }

  get intelligenceRows(): any[] {
    switch (this.activeIntelligenceTab) {
      case 'hardest':
        return this.hardest;
      case 'longest-answers':
        return this.longestAnswers;
      case 'best-performing':
        return this.bestPerforming;
      default:
        return this.mostAsked;
    }
  }

  get intelligenceMaxValue(): number {
    const rows = this.intelligenceRows;
    if (!rows.length) {
      return 1;
    }

    if (this.activeIntelligenceTab === 'most-asked') {
      return Math.max(1, ...rows.map((x) => Number(x.timesAsked || 0)));
    }

    if (this.activeIntelligenceTab === 'longest-answers') {
      return Math.max(1, ...rows.map((x) => Number(x.avgAnswerLength || 0)));
    }

    return Math.max(1, ...rows.map((x) => Number(x.avgScore || 0)));
  }

  get activeFilterCount(): number {
    let count = 0;
    if (this.questionFilters.role) {
      count += 1;
    }
    if (this.questionFilters.type) {
      count += 1;
    }
    if (this.questionFilters.difficulty) {
      count += 1;
    }
    if (this.questionFilters.active) {
      count += 1;
    }
    if (this.questionFilters.search.trim()) {
      count += 1;
    }
    return count;
  }

  get pageStart(): number {
    if (this.totalQuestionCount === 0) {
      return 0;
    }
    return this.questionPage * this.questionPageSize + 1;
  }

  get pageEnd(): number {
    return Math.min((this.questionPage + 1) * this.questionPageSize, this.totalQuestionCount);
  }

  get pageNumbers(): number[] {
    return Array.from({ length: this.totalQuestionPages }, (_, i) => i);
  }

  get codingNoSampleWarning(): boolean {
    return this.questionForm.type === 'CODING' && this.questionForm.sampleCode.trim().length === 0;
  }

  get roleCards(): Array<{ key: RoleType; title: string; icon: string; className: string; subtitle: string }> {
    return [
      {
        key: 'SE',
        title: 'Software Engineer',
        icon: 'code',
        className: 'se',
        subtitle: this.roleCoverageSubtitle('SE'),
      },
      {
        key: 'CLOUD',
        title: 'Cloud Engineer',
        icon: 'server',
        className: 'cloud',
        subtitle: this.roleCoverageSubtitle('CLOUD'),
      },
      {
        key: 'AI',
        title: 'AI Engineer',
        icon: 'brain-circuit',
        className: 'ai',
        subtitle: this.roleCoverageSubtitle('AI'),
      },
    ];
  }

  setActiveSection(section: DashboardSection): void {
    if (this.activeSection === section) {
      return;
    }

    this.activeSection = section;
    setTimeout(() => {
      this.initCharts();
      this.animateCounters();
    }, 0);
  }

  isSectionActive(section: DashboardSection): boolean {
    return this.activeSection === section;
  }

  async applyRoleCardFilter(role: RoleType): Promise<void> {
    if (this.selectedRoleFilter === role) {
      this.selectedRoleFilter = null;
      this.questionFilters.role = '';
    } else {
      this.selectedRoleFilter = role;
      this.questionFilters.role = role;
    }
    this.questionPage = 0;
    await this.loadQuestions();
  }

  async onFilterChange(): Promise<void> {
    this.questionPage = 0;
    await this.loadQuestions();
  }

  onSearchInput(value: string): void {
    this.questionFilters.search = value;
    this.questionPage = 0;

    if (this.searchDebounceTimer) {
      clearTimeout(this.searchDebounceTimer);
    }

    this.searchDebounceTimer = setTimeout(() => {
      this.loadQuestions().catch(() => undefined);
    }, 300);
  }

  async clearFilters(): Promise<void> {
    this.questionFilters = { role: '', type: '', difficulty: '', search: '', active: '' };
    this.selectedRoleFilter = null;
    this.questionPage = 0;
    await this.loadQuestions();
  }

  async goToPage(page: number): Promise<void> {
    if (page < 0 || page >= this.totalQuestionPages) {
      return;
    }
    this.questionPage = page;
    await this.loadQuestions();
  }

  toggleQuestionExpand(questionId: number): void {
    this.expandedQuestionId = this.expandedQuestionId === questionId ? null : questionId;
  }

  openAddDrawer(): void {
    this.drawerMode = 'add';
    this.editingQuestion = null;
    this.questionForm = this.createDefaultQuestionForm();
    this.chipDrafts = { expectedPoints: '', followUps: '', hints: '' };
    this.formError = '';
    this.formWarning = '';
    this.drawerOpen = true;
  }

  openEditDrawer(question: InterviewQuestionDto): void {
    this.drawerMode = 'edit';
    this.editingQuestion = question;
    this.questionForm = {
      careerPathId: question.careerPathId,
      questionText: question.questionText || '',
      roleType: question.roleType,
      type: question.type,
      difficulty: question.difficulty,
      domain: question.domain || '',
      category: question.category || '',
      expectedPoints: this.parseTagText(question.expectedPoints),
      followUps: this.parseTagText(question.followUps),
      hints: this.parseTagText(question.hints),
      idealAnswer: question.idealAnswer || '',
      sampleCode: question.sampleCode || '',
      isActive: Boolean(question.active),
    };
    this.chipDrafts = { expectedPoints: '', followUps: '', hints: '' };
    this.formError = '';
    this.formWarning = '';
    this.drawerOpen = true;
  }

  closeDrawer(): void {
    this.drawerOpen = false;
    this.formError = '';
    this.formWarning = '';
  }

  addChip(field: 'expectedPoints' | 'followUps' | 'hints'): void {
    const raw = this.chipDrafts[field].trim();
    if (!raw) {
      return;
    }
    if (this.questionForm[field].includes(raw)) {
      this.chipDrafts[field] = '';
      return;
    }
    this.questionForm[field] = [...this.questionForm[field], raw];
    this.chipDrafts[field] = '';
  }

  removeChip(field: 'expectedPoints' | 'followUps' | 'hints', index: number): void {
    this.questionForm[field] = this.questionForm[field].filter((_, i) => i !== index);
  }

  onChipInputKeydown(event: KeyboardEvent, field: 'expectedPoints' | 'followUps' | 'hints'): void {
    if (event.key !== 'Enter') {
      return;
    }
    event.preventDefault();
    this.addChip(field);
  }

  async saveQuestion(): Promise<void> {
    this.formError = '';
    this.formWarning = '';

    const validationError = this.validateQuestionForm();
    if (validationError) {
      this.formError = validationError;
      return;
    }

    if (this.codingNoSampleWarning) {
      this.formWarning = 'Coding questions work best with starter code.';
    }

    const payload = {
      careerPathId: this.questionForm.careerPathId,
      questionText: this.questionForm.questionText.trim(),
      roleType: this.questionForm.roleType,
      type: this.questionForm.type,
      difficulty: this.questionForm.difficulty,
      domain: this.toNullable(this.questionForm.domain),
      category: this.toNullable(this.questionForm.category),
      expectedPoints: this.toNullable(this.serializeTags(this.questionForm.expectedPoints)),
      followUps: this.toNullable(this.serializeTags(this.questionForm.followUps)),
      hints: this.toNullable(this.serializeTags(this.questionForm.hints)),
      idealAnswer: this.toNullable(this.questionForm.idealAnswer),
      sampleCode: this.questionForm.type === 'CODING' ? this.toNullable(this.questionForm.sampleCode) : null,
      isActive: this.questionForm.isActive,
    };

    this.isSavingQuestion = true;
    try {
      if (this.drawerMode === 'edit' && this.editingQuestion) {
        await firstValueFrom(this.api.updateAdminQuestion(this.editingQuestion.id, payload));
        this.showToast('Question updated successfully');
      } else {
        await firstValueFrom(this.api.createAdminQuestion(payload));
        this.showToast('Question saved successfully');
      }

      this.closeDrawer();
      await Promise.all([this.loadQuestions(), this.loadStats(), this.loadCoverage()]);
    } catch (error) {
      this.formError = 'Unable to save question right now. Please try again.';
    } finally {
      this.isSavingQuestion = false;
    }
  }

  async deleteQuestion(): Promise<void> {
    if (!this.editingQuestion) {
      return;
    }

    const confirmed = window.confirm('Delete this question? This performs a soft delete and marks it inactive.');
    if (!confirmed) {
      return;
    }

    try {
      await firstValueFrom(this.api.deleteAdminQuestion(this.editingQuestion.id));
      this.showToast('Question archived');
      this.closeDrawer();
      await Promise.all([this.loadQuestions(), this.loadStats(), this.loadCoverage()]);
    } catch {
      this.formError = 'Unable to delete this question.';
    }
  }

  async toggleQuestionActive(question: InterviewQuestionDto): Promise<void> {
    try {
      await firstValueFrom(this.api.toggleAdminQuestionActive(question.id));
      this.showToast('Question status updated');
      await Promise.all([this.loadQuestions(), this.loadStats(), this.loadCoverage()]);
    } catch {
      this.showToast('Could not toggle status right now');
    }
  }

  async softDeleteQuestion(question: InterviewQuestionDto): Promise<void> {
    const confirmed = window.confirm('Archive this question?');
    if (!confirmed) {
      return;
    }

    try {
      await firstValueFrom(this.api.deleteAdminQuestion(question.id));
      this.showToast('Question archived');
      await Promise.all([this.loadQuestions(), this.loadStats(), this.loadCoverage()]);
    } catch {
      this.showToast('Could not archive question');
    }
  }

  async loadEverything(): Promise<void> {
    this.isLoading = true;

    try {
      await Promise.all([
        this.loadStats(),
        this.loadSessionsOverTime(30),
        this.loadScoresOverTime(30),
        this.loadScoreDistribution(),
        this.loadByRole(),
        this.loadByType(),
        this.loadByDifficulty(),
        this.loadLeaderboard(),
        this.loadTopStreaks(),
        this.loadQuestions(),
        this.loadMostAsked(),
        this.loadHardest(),
        this.loadLongestAnswers(),
        this.loadBestPerforming(),
        this.loadCoverage(),
      ]);

      this.isLoading = false;
      this.lastUpdated = new Date();
      setTimeout(() => {
        this.initCharts();
        this.animateCounters();
      }, 0);
    } catch {
      this.isLoading = false;
      this.showToast('Some dashboard data could not be loaded');
    }
  }

  private async loadStats(): Promise<void> {
    const stats = await firstValueFrom(this.api.getAdminOverviewStats());
    this.stats = stats;
  }

  private async loadSessionsOverTime(days: number): Promise<void> {
    this.sessionsOverTime = await firstValueFrom(this.api.getAdminSessionsOverTime(days));
  }

  private async loadScoresOverTime(days: number): Promise<void> {
    this.scoresOverTime = await firstValueFrom(this.api.getAdminScoresOverTime(days));
  }

  private async loadScoreDistribution(): Promise<void> {
    this.scoreDistribution = await firstValueFrom(this.api.getAdminScoreDistribution());
  }

  private async loadByRole(): Promise<void> {
    this.byRole = await firstValueFrom(this.api.getAdminByRole());
  }

  private async loadByType(): Promise<void> {
    this.byType = await firstValueFrom(this.api.getAdminByType());
  }

  private async loadByDifficulty(): Promise<void> {
    this.byDifficulty = await firstValueFrom(this.api.getAdminByDifficulty());
  }

  private async loadLeaderboard(): Promise<void> {
    this.leaderboard = await firstValueFrom(this.api.getAdminLeaderboard(10));
  }

  private async loadTopStreaks(): Promise<void> {
    this.topStreaks = await firstValueFrom(this.api.getAdminTopStreaks(10));
  }

  private async loadQuestions(): Promise<void> {
    const response = await firstValueFrom(
      this.api.getAdminQuestions({
        role: this.questionFilters.role || undefined,
        type: this.questionFilters.type || undefined,
        difficulty: this.questionFilters.difficulty || undefined,
        active: this.questionFilters.active || undefined,
        search: this.questionFilters.search || undefined,
        page: this.questionPage,
        size: this.questionPageSize,
      })
    );

    this.questions = response?.content ?? [];
    this.totalQuestionCount = Number(response?.totalElements ?? 0);
    this.totalQuestionPages = Number(response?.totalPages ?? 0);
  }

  private async loadMostAsked(): Promise<void> {
    this.mostAsked = await firstValueFrom(this.api.getAdminMostAsked(10));
  }

  private async loadHardest(): Promise<void> {
    this.hardest = await firstValueFrom(this.api.getAdminHardest(10));
  }

  private async loadLongestAnswers(): Promise<void> {
    this.longestAnswers = await firstValueFrom(this.api.getAdminLongestAnswers(10));
  }

  private async loadBestPerforming(): Promise<void> {
    this.bestPerforming = await firstValueFrom(this.api.getAdminBestPerforming(10));
  }

  private async loadCoverage(): Promise<void> {
    this.coverage = await firstValueFrom(this.api.getAdminCoverage());
    this.createCoverageHeatmap();
  }

  private initCharts(): void {
    this.destroyCharts();
    this.createSessionsLineChart();
    this.createOutcomeDonutChart();
    this.createScoreDistributionChart();
    this.createByRoleRadarChart();
    this.createByTypeHorizontalBarChart();
    this.createCoverageHeatmap();
  }

  private destroyCharts(): void {
    this.sessionsLineChart?.destroy();
    this.outcomeDonutChart?.destroy();
    this.scoreDistributionChart?.destroy();
    this.roleRadarChart?.destroy();
    this.typeBarChart?.destroy();
    this.sessionsLineChart = null;
    this.outcomeDonutChart = null;
    this.scoreDistributionChart = null;
    this.roleRadarChart = null;
    this.typeBarChart = null;
  }

  private createSessionsLineChart(): void {
    const canvas = this.sessionsLineCanvas?.nativeElement;
    if (!canvas || !this.sessionsOverTime.length) {
      return;
    }

    const context = canvas.getContext('2d');
    if (!context) {
      return;
    }

    const labels = this.sessionsOverTime.map((item) => this.formatShortDate(item.date));
    const totalData = this.sessionsOverTime.map((item) => Number(item.total || 0));
    const completedData = this.sessionsOverTime.map((item) => Number(item.completed || 0));

    const totalGradient = context.createLinearGradient(0, 0, 0, 260);
    totalGradient.addColorStop(0, 'rgba(74, 158, 255, 0.24)');
    totalGradient.addColorStop(1, 'rgba(74, 158, 255, 0)');

    const completedGradient = context.createLinearGradient(0, 0, 0, 260);
    completedGradient.addColorStop(0, 'rgba(104, 211, 145, 0.24)');
    completedGradient.addColorStop(1, 'rgba(104, 211, 145, 0)');

    const config: ChartConfiguration<'line'> = {
      type: 'line',
      data: {
        labels,
        datasets: [
          {
            label: 'Total sessions',
            data: totalData,
            borderColor: '#4a9eff',
            backgroundColor: totalGradient,
            fill: true,
            pointRadius: 0,
            pointHoverRadius: 5,
            tension: 0.4,
            borderWidth: 2,
          },
          {
            label: 'Completed sessions',
            data: completedData,
            borderColor: '#68D391',
            backgroundColor: completedGradient,
            fill: true,
            pointRadius: 0,
            pointHoverRadius: 5,
            tension: 0.4,
            borderWidth: 2,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        animation: { duration: 800, easing: 'easeInOutQuart' },
        plugins: {
          legend: {
            position: 'bottom',
            labels: { color: '#cbd5e0' },
          },
        },
        scales: {
          x: {
            grid: { display: false },
            ticks: { color: '#94a3b8' },
          },
          y: {
            grid: { color: 'rgba(255,255,255,0.05)' },
            ticks: { color: '#94a3b8' },
          },
        },
      },
    };

    this.sessionsLineChart = new Chart(context, config);
  }

  private createOutcomeDonutChart(): void {
    const canvas = this.outcomeDonutCanvas?.nativeElement;
    if (!canvas || !this.stats) {
      return;
    }

    const context = canvas.getContext('2d');
    if (!context) {
      return;
    }

    const completed = Number(this.stats.completedSessions || 0);
    const abandoned = Number(this.stats.abandonedSessions || 0);
    const inProgress = Number(this.stats.activeCandidatesNow || 0);

    const centerTextPlugin = {
      id: 'centerText',
      afterDraw: (chart: Chart) => {
        const { ctx } = chart;
        const meta = chart.getDatasetMeta(0);
        if (!meta?.data?.[0]) {
          return;
        }

        const x = meta.data[0].x;
        const y = meta.data[0].y;
        ctx.save();
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillStyle = '#ffffff';
        ctx.font = '700 24px Poppins, sans-serif';
        ctx.fillText(`${Math.round(this.stats?.completionRate || 0)}%`, x, y - 8);
        ctx.fillStyle = '#94a3b8';
        ctx.font = '12px Poppins, sans-serif';
        ctx.fillText('Completion', x, y + 14);
        ctx.restore();
      },
    };

    const config: ChartConfiguration<'doughnut'> = {
      type: 'doughnut',
      data: {
        labels: ['Completed', 'Abandoned', 'In Progress'],
        datasets: [
          {
            data: [completed, abandoned, inProgress],
            backgroundColor: ['#68D391', '#FC8181', '#4a9eff'],
            borderWidth: 0,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        cutout: '72%',
        animation: {
          duration: 900,
        },
        plugins: {
          legend: {
            position: 'bottom',
            labels: {
              color: '#cbd5e0',
            },
          },
        },
      },
      plugins: [centerTextPlugin],
    };

    this.outcomeDonutChart = new Chart(context, config);
  }

  private createScoreDistributionChart(): void {
    const canvas = this.scoreDistributionCanvas?.nativeElement;
    if (!canvas || !this.scoreDistribution.length) {
      return;
    }

    const context = canvas.getContext('2d');
    if (!context) {
      return;
    }

    const labels = this.scoreDistribution.map((x) => x.bucket);
    const data = this.scoreDistribution.map((x) => Number(x.count || 0));
    const total = data.reduce((sum, value) => sum + value, 0) || 1;

    const config: ChartConfiguration<'bar'> = {
      type: 'bar',
      data: {
        labels,
        datasets: [
          {
            label: 'Sessions',
            data,
            backgroundColor: ['#FC8181', '#F6AD55', '#F6E05E', '#68D391', '#4299E1'],
            borderRadius: 8,
            maxBarThickness: 56,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        animation: {
          duration: 900,
        },
        plugins: {
          legend: {
            display: false,
          },
          tooltip: {
            callbacks: {
              label: (contextItem: TooltipItem<'bar'>) => {
                const count = Number(contextItem.raw || 0);
                const pct = Math.round((count * 100) / total);
                return `${count} sessions (${pct}%)`;
              },
            },
          },
        },
        scales: {
          x: {
            ticks: { color: '#94a3b8' },
            grid: { display: false },
          },
          y: {
            ticks: { color: '#94a3b8' },
            grid: { color: 'rgba(255,255,255,0.06)' },
          },
        },
      },
    };

    this.scoreDistributionChart = new Chart(context, config);
  }

  private createByRoleRadarChart(): void {
    const canvas = this.roleRadarCanvas?.nativeElement;
    if (!canvas || !this.byRole.length) {
      return;
    }

    const context = canvas.getContext('2d');
    if (!context) {
      return;
    }

    const axis = ['Avg Score', 'Completion Rate', 'Session Count', 'Streak Activity'];
    const roles = ['SE', 'CLOUD', 'AI'];
    const maxCount = Math.max(1, ...this.byRole.map((x) => Number(x.count || 0)));

    const streakByRole = this.computeStreakByRole();

    const datasetStyle: Record<string, { border: string; fill: string }> = {
      SE: { border: '#63B3ED', fill: 'rgba(99,179,237,0.3)' },
      CLOUD: { border: '#68D391', fill: 'rgba(104,211,145,0.3)' },
      AI: { border: '#B794F4', fill: 'rgba(183,148,244,0.3)' },
    };

    const datasets = roles.map((role) => {
      const point = this.byRole.find((x) => x.role === role) || { count: 0, avgScore: 0, completionRate: 0 };
      const streakValue = Number(streakByRole.get(role) || 0);
      const normalizedCount = (Number(point.count || 0) * 100) / maxCount;
      const normalizedScore = (Number(point.avgScore || 0) * 100) / 10;
      const normalizedCompletion = Number(point.completionRate || 0);
      const normalizedStreak = Math.min(100, streakValue * 5);

      return {
        label: role,
        data: [normalizedScore, normalizedCompletion, normalizedCount, normalizedStreak],
        borderColor: datasetStyle[role].border,
        backgroundColor: datasetStyle[role].fill,
        borderWidth: 2,
      };
    });

    const config: ChartConfiguration<'radar'> = {
      type: 'radar',
      data: {
        labels: axis,
        datasets,
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        animation: {
          duration: 900,
        },
        scales: {
          r: {
            suggestedMin: 0,
            suggestedMax: 100,
            grid: { color: 'rgba(255,255,255,0.08)' },
            angleLines: { color: 'rgba(255,255,255,0.08)' },
            pointLabels: { color: '#cbd5e0' },
            ticks: { display: false },
          },
        },
        plugins: {
          legend: {
            labels: { color: '#cbd5e0' },
          },
        },
      },
    };

    this.roleRadarChart = new Chart(context, config);
  }

  private createByTypeHorizontalBarChart(): void {
    const canvas = this.typeBarCanvas?.nativeElement;
    if (!canvas || !this.byType.length) {
      return;
    }

    const context = canvas.getContext('2d');
    if (!context) {
      return;
    }

    const labels = this.byType.map((x) => x.type);
    const data = this.byType.map((x) => Number(x.count || 0));

    const typeColors: Record<string, string> = {
      BEHAVIORAL: '#63B3ED',
      TECHNICAL: '#68D391',
      SITUATIONAL: '#F6E05E',
      CODING: '#F6AD55',
    };

    const config: ChartConfiguration<'bar'> = {
      type: 'bar',
      data: {
        labels,
        datasets: [
          {
            label: 'Question count',
            data,
            backgroundColor: labels.map((label) => typeColors[label] || '#4a9eff'),
            borderRadius: 8,
            maxBarThickness: 24,
          },
        ],
      },
      options: {
        indexAxis: 'y',
        responsive: true,
        maintainAspectRatio: false,
        animation: {
          duration: 800,
        },
        plugins: {
          legend: {
            display: false,
          },
        },
        scales: {
          x: {
            ticks: { color: '#94a3b8' },
            grid: { color: 'rgba(255,255,255,0.06)' },
          },
          y: {
            ticks: { color: '#cbd5e0' },
            grid: { display: false },
          },
        },
      },
    };

    this.typeBarChart = new Chart(context, config);
  }

  private createCoverageHeatmap(): void {
    const roles: RoleType[] = ['SE', 'CLOUD', 'AI', 'ALL'];
    const types: QuestionType[] = ['BEHAVIORAL', 'TECHNICAL', 'SITUATIONAL', 'CODING'];

    this.coverageMax = Math.max(1, ...this.coverage.map((x) => Number(x.count || 0)));

    this.coverageMatrix = roles.map((role) => ({
      role,
      cells: types.map((type) => {
        const found = this.coverage.find((item) => item.role === role && item.type === type) || { count: 0, active: 0 };
        return {
          type,
          count: Number(found.count || 0),
          active: Number(found.active || 0),
        };
      }),
    }));
  }

  animateCounters(): void {
    this.clearCounterAnimations();
    const nodes = Array.from(document.querySelectorAll<HTMLElement>('[data-count]'));
    for (const node of nodes) {
      const end = Number(node.dataset['count'] || 0);
      this.animateCount(node, end, 1500);
    }
  }

  animateCount(element: HTMLElement, end: number, duration = 1500): void {
    const start = 0;
    const increment = end / (duration / 16);
    let current = start;

    const timer = window.setInterval(() => {
      current += increment;
      if (current >= end) {
        current = end;
        clearInterval(timer);
      }

      const isFloat = String(end).includes('.') || end % 1 !== 0;
      element.textContent = isFloat ? current.toFixed(1) : Math.round(current).toLocaleString();
    }, 16);

    this.countAnimationTimerIds.push(timer);
  }

  private clearCounterAnimations(): void {
    for (const id of this.countAnimationTimerIds) {
      clearInterval(id);
    }
    this.countAnimationTimerIds = [];
  }

  badgeScoreClass(score: number): string {
    if (score >= 8) {
      return 'score-high';
    }
    if (score >= 6) {
      return 'score-mid';
    }
    return 'score-low';
  }

  rankClass(index: number): string {
    if (index === 0) {
      return 'rank-gold';
    }
    if (index === 1) {
      return 'rank-silver';
    }
    if (index === 2) {
      return 'rank-bronze';
    }
    return '';
  }

  streakBadge(streak: number): string {
    if (streak >= 30) {
      return '***';
    }
    if (streak >= 14) {
      return '+++';
    }
    if (streak >= 7) {
      return '++';
    }
    return '+';
  }

  formatNumber(value: any, fractionDigits = 0): string {
    const numeric = Number(value || 0);
    return numeric.toLocaleString(undefined, {
      maximumFractionDigits: fractionDigits,
      minimumFractionDigits: fractionDigits,
    });
  }

  formatShortDate(date: string): string {
    const parsed = new Date(date);
    return parsed.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
  }

  intelMetricLabel(row: any): string {
    if (this.activeIntelligenceTab === 'most-asked') {
      return `Asked ${Number(row.timesAsked || 0)} times`;
    }
    if (this.activeIntelligenceTab === 'longest-answers') {
      return `Avg ${Math.round(Number(row.avgAnswerLength || 0))} words`;
    }
    return `Avg score ${Number(row.avgScore || 0).toFixed(1)}`;
  }

  intelMetricValue(row: any): number {
    if (this.activeIntelligenceTab === 'most-asked') {
      return Number(row.timesAsked || 0);
    }
    if (this.activeIntelligenceTab === 'longest-answers') {
      return Number(row.avgAnswerLength || 0);
    }
    return Number(row.avgScore || 0);
  }

  intelBarWidth(row: any): number {
    const value = this.intelMetricValue(row);
    return Math.max(0, Math.min(100, (value * 100) / this.intelligenceMaxValue));
  }

  intelBarClass(): string {
    if (this.activeIntelligenceTab === 'hardest') {
      return 'danger';
    }
    if (this.activeIntelligenceTab === 'best-performing') {
      return 'success';
    }
    if (this.activeIntelligenceTab === 'longest-answers') {
      return 'info';
    }
    return 'primary';
  }

  difficultyScorePct(score: number): number {
    return Math.max(0, Math.min(100, (Number(score || 0) * 100) / 10));
  }

  heatmapCellOpacity(count: number): number {
    return 0.12 + (0.88 * count) / this.coverageMax;
  }

  heatmapCellTitle(cell: { count: number; active: number }): string {
    const inactive = Math.max(0, cell.count - cell.active);
    return `${cell.active} active / ${inactive} inactive questions`;
  }

  roleCoverageSubtitle(role: RoleType): string {
    const points = this.coverage.filter((item) => item.role === role);
    const total = points.reduce((sum, item) => sum + Number(item.count || 0), 0);
    const active = points.reduce((sum, item) => sum + Number(item.active || 0), 0);
    return `${total} questions · ${active} active`;
  }

  askedCountForQuestion(questionId: number): number {
    const rows = this.mostAsked;
    const found = rows.find((item) => Number(item.questionId) === Number(questionId));
    return Number(found?.timesAsked || 0);
  }

  askedMiniWidth(questionId: number): number {
    return Math.min(100, this.askedCountForQuestion(questionId));
  }

  avgScoreForQuestion(questionId: number): number {
    const pools = [this.mostAsked, this.bestPerforming, this.hardest];
    for (const pool of pools) {
      const found = pool.find((item) => Number(item.questionId) === Number(questionId));
      if (found && found.avgScore !== undefined && found.avgScore !== null) {
        return Number(found.avgScore);
      }
    }
    return 0;
  }

  difficultyDots(difficulty: DifficultyLevel): string {
    if (difficulty === 'BEGINNER') {
      return '●○○';
    }
    if (difficulty === 'INTERMEDIATE') {
      return '●●○';
    }
    return '●●●';
  }

  private validateQuestionForm(): string {
    if (!this.questionForm.questionText || this.questionForm.questionText.trim().length < 10) {
      return 'Question text is required and must be at least 10 characters.';
    }
    if (!this.questionForm.roleType) {
      return 'Role type is required.';
    }
    if (!this.questionForm.type) {
      return 'Question type is required.';
    }
    if (!this.questionForm.difficulty) {
      return 'Difficulty is required.';
    }
    if (!Number.isInteger(this.questionForm.careerPathId) || this.questionForm.careerPathId <= 0) {
      return 'Career path id must be a positive number.';
    }
    return '';
  }

  private createDefaultQuestionForm(): QuestionFormModel {
    return {
      careerPathId: 1,
      questionText: '',
      roleType: 'SE',
      type: 'BEHAVIORAL',
      difficulty: 'BEGINNER',
      domain: '',
      category: '',
      expectedPoints: [],
      followUps: [],
      hints: [],
      idealAnswer: '',
      sampleCode: '',
      isActive: true,
    };
  }

  private parseTagText(value: string | null): string[] {
    if (!value) {
      return [];
    }

    return value
      .split(/\n|,/g)
      .map((item) => item.trim())
      .filter((item) => item.length > 0);
  }

  private serializeTags(values: string[]): string {
    return values.map((value) => value.trim()).filter((value) => value.length > 0).join('\n');
  }

  private toNullable(value: string): string | null {
    const trimmed = (value || '').trim();
    return trimmed.length ? trimmed : null;
  }

  private showToast(message: string): void {
    this.toastMessage = message;
    setTimeout(() => {
      this.toastMessage = '';
    }, 2200);
  }

  private computeStreakByRole(): Map<string, number> {
    const map = new Map<string, number>();
    const grouped: Record<string, number[]> = { SE: [], CLOUD: [], AI: [] };

    for (const row of this.leaderboard) {
      const role = String(row.topRole || '').toUpperCase();
      if (grouped[role]) {
        grouped[role].push(Number(row.currentStreak || 0));
      }
    }

    for (const role of Object.keys(grouped)) {
      const values = grouped[role];
      const avg = values.length ? values.reduce((sum, x) => sum + x, 0) / values.length : 0;
      map.set(role, avg);
    }

    return map;
  }
}
