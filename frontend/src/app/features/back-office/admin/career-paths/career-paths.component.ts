import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { LUCIDE_ICONS } from '../../../../shared/lucide-icons';
import {
  CareerPathOptionDto,
  CreateCareerPathRequestDto,
  RoadmapApiService,
} from '../../../../services/roadmap-api.service';

/* â•â•â•â•â•â•â•â•â•â• INTERFACES â•â•â•â•â•â•â•â•â•â• */

interface Resource {
  url: string;
  title: string;
  type: 'Video' | 'Article' | 'Course' | 'Docs';
  free: boolean;
}

interface RoadmapStep {
  id: number;
  title: string;
  description: string;
  estimatedDays: number;
  difficulty: 'Beginner' | 'Intermediate' | 'Advanced' | 'Expert';
  resources: Resource[];
  expanded: boolean;
}

interface PathSkill {
  name: string;
  category: string;
  importance: 'Required' | 'Preferred' | 'Optional';
  weight: number;
}

interface InterviewQ {
  id: number;
  text: string;
  category: 'Technical' | 'Behavioral' | 'System Design';
  difficulty: 'Beginner' | 'Intermediate' | 'Advanced' | 'Expert';
}

interface SkillGap {
  skill: string;
  requiredByJobs: number;   // percentage
  covered: boolean;
  priority: 'High' | 'Medium' | 'Low';
}

interface ActiveJob {
  company: string;
  title: string;
  id: number;
}

interface StepCompletion {
  step: string;
  pct: number;
  sharp: boolean;
}

interface CareerPath {
  id: number;
  emoji: string;
  name: string;
  status: 'Published' | 'Draft';
  enrolled: number;
  stepsCount: number;
  skillsCount: number;
  avgCompletion: number;
  description: string;
  targetRoles: string[];
  salaryMin: number;
  salaryMax: number;
  difficulty: number;         // 1-5
  estimatedWeeks: number;
  hoursPerWeek: number;
  showInOnboarding: boolean;
  showInExplorer: boolean;
  showInAI: boolean;
  createdAt: string;

  skills: PathSkill[];
  roadmapSteps: RoadmapStep[];
  interviewQuestions: InterviewQ[];

  /* Job Alignment */
  marketDemand: { skill: string; demand: number; covered: number }[];
  skillGaps: SkillGap[];
  activeJobs: ActiveJob[];

  /* Analytics */
  totalEnrolled: number;
  activeThisMonth: number;
  avgCompletionRate: number;
  dropoutStep: number;
  dropoutStepName: string;
  enrollmentWeekly: number[];
  stepCompletions: StepCompletion[];
  scoreDistribution: number[];
  scoreBenchmark: number;
}

/* â•â•â•â•â•â•â•â•â•â• SKILL CATALOG â•â•â•â•â•â•â•â•â•â• */
interface CatalogSkill { name: string; category: string; }

@Component({
  selector: 'app-career-paths',
  standalone: true,
  imports: [CommonModule, FormsModule, LUCIDE_ICONS],
  templateUrl: './career-paths.component.html',
  styleUrl: './career-paths.component.scss'
})
export class CareerPathsComponent implements OnInit {
  private readonly roadmapApi = inject(RoadmapApiService);
  private editBackup: CareerPath | null = null;

  isLoading = false;
  isSaving = false;
  apiError: string | null = null;
  apiSuccess: string | null = null;

  /* â”€â”€ Search / Filter â”€â”€ */
  listSearch = '';
  listFilter: 'All' | 'Draft' = 'All';

  /* â”€â”€ Selected path â”€â”€ */
  selectedPathId: number | null = null;
  editMode = false;
  activeTab = 'overview';
  tabs = ['Overview', 'Skills', 'Roadmap Steps', 'Interview Questions', 'Job Alignment', 'Analytics'];

  /* â”€â”€ Skills tab â”€â”€ */
  skillSearch = '';
  addSkillSearch = '';
  collapsedCategories = new Set<string>();
  showNewSkillForm = false;
  newSkillName = '';
  newSkillCategory = 'Frontend';
  newSkillDesc = '';
  skillCategories = ['Frontend', 'Backend', 'DevOps', 'Databases', 'Algorithms', 'Soft Skills'];

  /* â”€â”€ Interview Questions tab â”€â”€ */
  iqSearch = '';
  iqDiffFilter = 'All';
  showAddQuestionsModal = false;
  addQSearch = '';
  addQSelected = new Set<number>();

  /* â”€â”€ Roadmap tab â”€â”€ */
  editingStepId: number | null = null;

  /* â”€â”€ Overview edit helpers â”€â”€ */
  newRoleInput = '';

  /* â”€â”€ Publish confirm â”€â”€ */
  showPublishConfirm = false;

  /* â”€â”€ Emoji picker â”€â”€ */
  showEmojiPicker = false;
  emojis = [
    '\u2601\uFE0F',
    '\u{1F916}',
    '\u{1F4BB}',
    '\u{1F4CA}',
    '\u{1F527}',
    '\u{1F3A8}',
    '\u{1F6E1}\uFE0F',
    '\u{1F9EA}',
    '\u{1F680}',
    '\u{2699}\uFE0F',
    '\u{1F9E0}'
  ];

  /* Loaded dynamically from backend services */
  globalSkillCatalog: CatalogSkill[] = [];

  /* Loaded dynamically from backend services */
  globalQuestionBank: InterviewQ[] = [];

  /* Loaded dynamically from backend services */
  paths: CareerPath[] = [];

  ngOnInit(): void {
    this.loadCareerPathsFromBackend();
  }

  private loadCareerPathsFromBackend(): void {
    this.isLoading = true;
    this.apiError = null;

    this.roadmapApi
      .getAdminCareerPaths()
      .pipe(finalize(() => (this.isLoading = false)))
      .subscribe({
        next: (templates) => {
          if (!templates || templates.length === 0) {
            this.apiError = 'No career path templates were returned by backend.';
            return;
          }

          this.paths = templates.map((template) => this.toCareerPath(template));
          if (!this.selectedPathId || !this.paths.some((path) => path.id === this.selectedPathId)) {
            this.selectedPathId = this.paths[0]?.id ?? null;
          }
        },
        error: () => {
          this.apiError = 'Could not sync career paths from backend.';
        },
      });
  }

  private toCareerPath(template: CareerPathOptionDto): CareerPath {
    const topics = this.parseDefaultTopics(template.defaultTopics);
    const difficultyLabel = this.toDifficultyLabel(template.difficulty);
    const createdAt = this.formatDate(template.createdAt);
    const emoji = this.resolveCareerEmoji(template.title);

    return {
      id: template.id,
      emoji,
      name: template.title,
      status: template.isPublished ? 'Published' : 'Draft',
      enrolled: 0,
      stepsCount: topics.length,
      skillsCount: topics.length,
      avgCompletion: 0,
      description: template.description || '',
      targetRoles: [],
      salaryMin: 0,
      salaryMax: 0,
      difficulty: this.toDifficultyScore(difficultyLabel),
      estimatedWeeks: template.estimatedWeeks || 8,
      hoursPerWeek: 10,
      showInOnboarding: template.isPublished ?? false,
      showInExplorer: template.isPublished ?? false,
      showInAI: template.isPublished ?? false,
      createdAt,
      skills: topics.map((topic) => ({
        name: topic,
        category: 'Frontend',
        importance: 'Preferred',
        weight: 5,
      })),
      roadmapSteps: topics.map((topic, index) => ({
        id: index + 1,
        title: topic,
        description: `Learn and practice ${topic}.`,
        estimatedDays: 5,
        difficulty: difficultyLabel,
        resources: [],
        expanded: false,
      })),
      interviewQuestions: [],
      marketDemand: [],
      skillGaps: [],
      activeJobs: [],
      totalEnrolled: 0,
      activeThisMonth: 0,
      avgCompletionRate: 0,
      dropoutStep: 0,
      dropoutStepName: 'N/A',
      enrollmentWeekly: [0],
      stepCompletions: [],
      scoreDistribution: [0],
      scoreBenchmark: 50,
    };
  }

  private toCreatePayload(path: CareerPath): CreateCareerPathRequestDto {
    return {
      title: (path.name || '').trim(),
      description: (path.description || '').trim(),
      defaultTopics: path.skills.map((skill) => skill.name).join(', '),
      difficulty: this.getDifficultyLabel(path.difficulty).toUpperCase(),
      estimatedWeeks: path.estimatedWeeks,
    };
  }

  private resolveCareerEmoji(title: string | undefined): string {
    const normalized = (title || '').toLowerCase();
    if (normalized.includes('cloud')) {
      return '\u2601\uFE0F';
    }
    if (normalized.includes('ai') || normalized.includes('machine learning') || normalized.includes('ml')) {
      return '\u{1F916}';
    }
    if (normalized.includes('software') || normalized.includes('web') || normalized.includes('full-stack')) {
      return '\u{1F4BB}';
    }
    return this.emojis[0];
  }

  private parseDefaultTopics(value: string | undefined): string[] {
    if (!value) {
      return [];
    }
    return value
      .split(/[\n,;]+/)
      .map((topic) => topic.trim())
      .filter((topic) => topic.length > 0);
  }

  private formatDate(value: string | undefined): string {
    if (!value) {
      return 'N/A';
    }
    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) {
      return value;
    }
    return parsed.toLocaleDateString('en-US', {
      month: 'short',
      day: '2-digit',
      year: 'numeric',
    });
  }

  private toDifficultyLabel(value: string | undefined): RoadmapStep['difficulty'] {
    const normalized = (value || '').trim().toUpperCase();
    if (normalized === 'BEGINNER') {
      return 'Beginner';
    }
    if (normalized === 'INTERMEDIATE') {
      return 'Intermediate';
    }
    if (normalized === 'ADVANCED') {
      return 'Advanced';
    }
    return 'Expert';
  }

  private toDifficultyScore(value: RoadmapStep['difficulty']): number {
    if (value === 'Beginner') {
      return 1;
    }
    if (value === 'Intermediate') {
      return 2;
    }
    if (value === 'Advanced') {
      return 4;
    }
    return 5;
  }

  private clonePath(path: CareerPath): CareerPath {
    return JSON.parse(JSON.stringify(path)) as CareerPath;
  }

  /* â•â•â•â•â•â•â•â•â•â• COMPUTED â•â•â•â•â•â•â•â•â•â• */

  get filteredPaths(): CareerPath[] {
    return this.paths.filter(p => {
      const matchSearch = !this.listSearch || p.name.toLowerCase().includes(this.listSearch.toLowerCase());
      const matchFilter = this.listFilter === 'All' || p.status === this.listFilter;
      return matchSearch && matchFilter;
    });
  }

  get selectedPath(): CareerPath | null {
    return this.paths.find(p => p.id === this.selectedPathId) || null;
  }

  get totalActivePaths(): number { return this.paths.filter(p => p.status === 'Published').length; }
  get mostPopular(): CareerPath | null {
    return this.paths.filter(p => p.status === 'Published').sort((a, b) => b.enrolled - a.enrolled)[0] || null;
  }
  get highestCompletion(): CareerPath | null {
    return this.paths.filter(p => p.status === 'Published').sort((a, b) => b.avgCompletion - a.avgCompletion)[0] || null;
  }
  get pendingReviewCount(): number { return this.paths.filter(p => p.status === 'Draft').length; }

  /* â”€â”€ Skills tab helpers â”€â”€ */
  get selectedSkillCategories(): string[] {
    if (!this.selectedPath) return [];
    const cats = new Set(this.selectedPath.skills.map(s => s.category));
    return [...cats];
  }
  filteredSkillsInCategory(category: string): PathSkill[] {
    if (!this.selectedPath) return [];
    return this.selectedPath.skills.filter(s => {
      const matchCat = s.category === category;
      const matchSearch = !this.skillSearch || s.name.toLowerCase().includes(this.skillSearch.toLowerCase());
      return matchCat && matchSearch;
    });
  }
  isCategoryCollapsed(cat: string): boolean { return this.collapsedCategories.has(cat); }
  toggleCategory(cat: string): void {
    this.collapsedCategories.has(cat) ? this.collapsedCategories.delete(cat) : this.collapsedCategories.add(cat);
  }
  get requiredSkillsCount(): number { return this.selectedPath?.skills.filter(s => s.importance === 'Required').length || 0; }
  get preferredSkillsCount(): number { return this.selectedPath?.skills.filter(s => s.importance === 'Preferred').length || 0; }
  get optionalSkillsCount(): number { return this.selectedPath?.skills.filter(s => s.importance === 'Optional').length || 0; }

  get catalogSearchResults(): CatalogSkill[] {
    if (!this.addSkillSearch.trim() || !this.selectedPath) return [];
    const existing = new Set(this.selectedPath.skills.map(s => s.name));
    return this.globalSkillCatalog.filter(s =>
      !existing.has(s.name) && s.name.toLowerCase().includes(this.addSkillSearch.toLowerCase())
    ).slice(0, 8);
  }

  addSkillFromCatalog(s: CatalogSkill): void {
    if (!this.selectedPath) return;
    this.selectedPath.skills.push({ name: s.name, category: s.category, importance: 'Preferred', weight: 5 });
    this.addSkillSearch = '';
  }
  removeSkill(skill: PathSkill): void {
    if (!this.selectedPath) return;
    this.selectedPath.skills = this.selectedPath.skills.filter(s => s !== skill);
  }
  createNewSkill(): void {
    if (!this.newSkillName.trim() || !this.selectedPath) return;
    this.globalSkillCatalog.push({ name: this.newSkillName, category: this.newSkillCategory });
    this.selectedPath.skills.push({ name: this.newSkillName, category: this.newSkillCategory, importance: 'Preferred', weight: 5 });
    this.newSkillName = '';
    this.newSkillDesc = '';
    this.showNewSkillForm = false;
  }

  /* â”€â”€ IQ helpers â”€â”€ */
  get filteredIQs(): InterviewQ[] {
    if (!this.selectedPath) return [];
    return this.selectedPath.interviewQuestions.filter(q => {
      const matchSearch = !this.iqSearch || q.text.toLowerCase().includes(this.iqSearch.toLowerCase());
      const matchDiff = this.iqDiffFilter === 'All' || q.difficulty === this.iqDiffFilter;
      return matchSearch && matchDiff;
    });
  }
  get iqTechnical(): number { return this.selectedPath?.interviewQuestions.filter(q => q.category === 'Technical').length || 0; }
  get iqBehavioral(): number { return this.selectedPath?.interviewQuestions.filter(q => q.category === 'Behavioral').length || 0; }
  get iqSystemDesign(): number { return this.selectedPath?.interviewQuestions.filter(q => q.category === 'System Design').length || 0; }

  get availableQuestionsForModal(): InterviewQ[] {
    if (!this.selectedPath) return [];
    const existing = new Set(this.selectedPath.interviewQuestions.map(q => q.id));
    return this.globalQuestionBank.filter(q => {
      const notLinked = !existing.has(q.id);
      const matchSearch = !this.addQSearch || q.text.toLowerCase().includes(this.addQSearch.toLowerCase());
      return notLinked && matchSearch;
    });
  }
  toggleAddQ(id: number): void {
    this.addQSelected.has(id) ? this.addQSelected.delete(id) : this.addQSelected.add(id);
  }
  addSelectedQuestions(): void {
    if (!this.selectedPath) return;
    this.globalQuestionBank.filter(q => this.addQSelected.has(q.id)).forEach(q => {
      this.selectedPath!.interviewQuestions.push({ ...q });
    });
    this.addQSelected.clear();
    this.showAddQuestionsModal = false;
  }
  removeIQ(q: InterviewQ): void {
    if (!this.selectedPath) return;
    this.selectedPath.interviewQuestions = this.selectedPath.interviewQuestions.filter(x => x.id !== q.id);
  }

  /* â”€â”€ Roadmap helpers â”€â”€ */
  toggleStepEdit(step: RoadmapStep): void {
    this.editingStepId = this.editingStepId === step.id ? null : step.id;
    step.expanded = !step.expanded;
  }
  addNewStep(): void {
    if (!this.selectedPath) return;
    const newId = this.selectedPath.roadmapSteps.length > 0
      ? Math.max(...this.selectedPath.roadmapSteps.map(s => s.id)) + 1 : 1;
    this.selectedPath.roadmapSteps.push({
      id: newId, title: 'New Step', description: '', estimatedDays: 5,
      difficulty: 'Beginner', resources: [], expanded: true
    });
    this.editingStepId = newId;
  }
  removeStep(step: RoadmapStep): void {
    if (!this.selectedPath) return;
    this.selectedPath.roadmapSteps = this.selectedPath.roadmapSteps.filter(s => s.id !== step.id);
    if (this.editingStepId === step.id) this.editingStepId = null;
  }
  addResource(step: RoadmapStep): void {
    step.resources.push({ url: '', title: '', type: 'Article', free: true });
  }
  removeResource(step: RoadmapStep, idx: number): void {
    step.resources.splice(idx, 1);
  }
  getTotalHours(): number {
    return this.selectedPath?.roadmapSteps.reduce((s, st) => s + st.estimatedDays, 0) || 0;
  }

  /* â”€â”€ Path actions â”€â”€ */
  selectPath(id: number): void {
    this.selectedPathId = id;
    this.editMode = false;
    this.activeTab = 'overview';
    this.editBackup = null;
    this.apiError = null;
    this.apiSuccess = null;
  }
  enterEdit(): void {
    if (!this.selectedPath) {
      return;
    }

    this.editBackup = this.clonePath(this.selectedPath);
    this.editMode = true;
    this.apiError = null;
    this.apiSuccess = null;
  }

  discardEdit(): void {
    if (this.editBackup) {
      const index = this.paths.findIndex((path) => path.id === this.editBackup?.id);
      if (index >= 0) {
        this.paths[index] = this.clonePath(this.editBackup);
      }
    }

    this.editBackup = null;
    this.editMode = false;
    this.apiError = null;
    this.apiSuccess = null;
  }

  saveChanges(): void {
    const selected = this.selectedPath;
    if (!selected) {
      return;
    }

    this.isSaving = true;
    this.apiError = null;
    this.apiSuccess = null;

    this.roadmapApi
      .updateCareerPath(selected.id, this.toCreatePayload(selected))
      .pipe(finalize(() => (this.isSaving = false)))
      .subscribe({
        next: (updated) => {
          const index = this.paths.findIndex((path) => path.id === updated.id);
          if (index >= 0) {
            const current = this.paths[index];
            const mapped = this.toCareerPath(updated);
            this.paths[index] = {
              ...mapped,
              emoji: current.emoji,
              enrolled: current.enrolled,
              totalEnrolled: current.totalEnrolled,
              activeThisMonth: current.activeThisMonth,
              avgCompletionRate: current.avgCompletionRate,
              avgCompletion: current.avgCompletion,
              marketDemand: current.marketDemand,
              skillGaps: current.skillGaps,
              activeJobs: current.activeJobs,
              interviewQuestions: current.interviewQuestions,
              enrollmentWeekly: current.enrollmentWeekly,
              stepCompletions: current.stepCompletions,
              scoreDistribution: current.scoreDistribution,
              scoreBenchmark: current.scoreBenchmark,
            };
          }

          this.editMode = false;
          this.editBackup = null;
          this.apiSuccess = 'Career path saved successfully.';
        },
        error: () => {
          this.apiError = 'Could not save career path changes. Please retry.';
        },
      });
  }

  createNewPath(): void {
    const payload: CreateCareerPathRequestDto = {
      title: 'New Career Path',
      description: '',
      defaultTopics: '',
      difficulty: 'BEGINNER',
      estimatedWeeks: 8,
    };

    this.isSaving = true;
    this.apiError = null;
    this.apiSuccess = null;

    this.roadmapApi
      .createCareerPath(payload)
      .pipe(finalize(() => (this.isSaving = false)))
      .subscribe({
        next: (created) => {
          const mapped = this.toCareerPath(created);
          mapped.emoji = '🚀';
          this.paths = [mapped, ...this.paths];
          this.selectedPathId = mapped.id;
          this.editMode = true;
          this.activeTab = 'overview';
          this.editBackup = this.clonePath(mapped);
          this.apiSuccess = 'Career path created. You can now edit details.';
        },
        error: () => {
          this.apiError = 'Could not create a new career path. Please retry.';
        },
      });
  }

  addRole(): void {
    if (!this.newRoleInput.trim() || !this.selectedPath) return;
    this.selectedPath.targetRoles.push(this.newRoleInput.trim());
    this.newRoleInput = '';
  }
  removeRole(idx: number): void {
    this.selectedPath?.targetRoles.splice(idx, 1);
  }

  getDifficultyLabel(d: number): string {
    return ['', 'Beginner', 'Intermediate', 'Advanced', 'Expert', 'Master'][d] || '';
  }
  getDifficultyClass(d: string): string {
    switch (d) {
      case 'Beginner': return 'dbadge--green';
      case 'Intermediate': return 'dbadge--teal';
      case 'Advanced': return 'dbadge--orange';
      case 'Expert': return 'dbadge--red';
      default: return '';
    }
  }
  getCategoryClass(c: string): string {
    switch (c) {
      case 'Technical': return 'cbadge--blue';
      case 'Behavioral': return 'cbadge--purple';
      case 'System Design': return 'cbadge--amber';
      default: return '';
    }
  }

  selectEmoji(e: string): void {
    if (this.selectedPath) this.selectedPath.emoji = e;
    this.showEmojiPicker = false;
  }

  togglePublishConfirm(): void { this.showPublishConfirm = !this.showPublishConfirm; }
  confirmPublish(): void {
    const selected = this.selectedPath;
    if (!selected) {
      this.showPublishConfirm = false;
      return;
    }

    this.isSaving = true;
    this.apiError = null;
    this.apiSuccess = null;

    this.roadmapApi
      .publishCareerPath(selected.id)
      .pipe(finalize(() => (this.isSaving = false)))
      .subscribe({
        next: (published) => {
          const index = this.paths.findIndex((path) => path.id === published.id);
          if (index >= 0) {
            this.paths[index].status = 'Published';
            this.paths[index].showInOnboarding = true;
            this.paths[index].showInExplorer = true;
            this.paths[index].showInAI = true;
          }

          this.apiSuccess = 'Career path published successfully.';
          this.showPublishConfirm = false;
        },
        error: () => {
          this.apiError = 'Could not publish this career path. Please retry.';
          this.showPublishConfirm = false;
        },
      });
  }

  get maxEnrollmentWeekly(): number {
    return Math.max(...(this.selectedPath?.enrollmentWeekly || [1]));
  }
  get maxScoreDist(): number {
    return Math.max(...(this.selectedPath?.scoreDistribution || [1]));
  }
}

