import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { catchError, finalize, forkJoin, of, switchMap } from 'rxjs';
import { LUCIDE_ICONS } from '../../../shared/lucide-icons';
import {
  MilestoneDto,
  NotificationDto,
  ProgressSummaryDto,
  RoadmapApiService,
  RoadmapNodeDto,
  RoadmapVisualResponse,
} from '../../../services/roadmap-api.service';
import { resolveRoadmapUserId } from './roadmap/roadmap-user-context';

interface DashboardStatCard {
  label: string;
  value: string;
  trend: string;
  up: boolean;
  icon: string;
}

interface DashboardRecommendation {
  priority: 'High' | 'Medium' | 'Low';
  badgeColor: string;
  title: string;
  desc: string;
  time: string;
}

interface DashboardSkill {
  name: string;
  pct: number;
  color: string;
}

interface DashboardUpcomingStep {
  id: number;
  text: string;
  due: string;
  done: boolean;
}

interface DashboardWeekDay {
  day: string;
  active: boolean;
  today: boolean;
}

interface DashboardActivity {
  text: string;
  time: string;
  color: string;
  timestamp: number;
}

@Component({
  selector: 'app-dashboard-home',
  standalone: true,
  imports: [CommonModule, LUCIDE_ICONS],
  templateUrl: './dashboard-home.component.html',
  styleUrl: './dashboard-home.component.scss'
})
export class DashboardHomeComponent implements OnInit {
  private readonly roadmapApi = inject(RoadmapApiService);

  loading = false;
  errorMessage: string | null = null;

  readinessScore = 0;
  readinessBand = 'starting to build momentum';
  circumference = 2 * Math.PI * 50;

  statCards: DashboardStatCard[] = [];
  recommendations: DashboardRecommendation[] = [];
  skills: DashboardSkill[] = [];
  upcomingSteps: DashboardUpcomingStep[] = [];

  streakDays = 0;
  weekDays: DashboardWeekDay[] = this.buildWeekDays(0);

  activities: Array<Omit<DashboardActivity, 'timestamp'>> = [];

  get dashOffset(): number {
    return this.circumference * (1 - this.readinessScore / 100);
  }

  ngOnInit(): void {
    this.loadDashboard();
  }

  private loadDashboard(): void {
    const userId = resolveRoadmapUserId();
    if (!userId) {
      this.errorMessage = 'No authenticated user found. Please sign in again.';
      return;
    }

    this.loading = true;
    this.errorMessage = null;

    this.roadmapApi
      .getUserRoadmap(userId)
      .pipe(
        catchError(() => {
          // Roadmap service may not be available; continue without roadmap data
          this.applyFallbackState();
          this.loading = false;
          return of(null);
        }),
        switchMap((roadmap) => {
          if (!roadmap) {
            return of(null);
          }
          return forkJoin({
            roadmap: of(roadmap),
            progress: this.roadmapApi
              .getProgressSummary(roadmap.id)
              .pipe(catchError(() => of(null as ProgressSummaryDto | null))),
            graph: this.roadmapApi
              .getRoadmapGraph(roadmap.id)
              .pipe(catchError(() => of(null as RoadmapVisualResponse | null))),
            streak: this.roadmapApi
              .getStreakInfo(userId, roadmap.id)
              .pipe(catchError(() => of({ currentStreak: 0, longestStreak: 0 }))),
            notifications: this.roadmapApi
              .getRoadmapNotifications(roadmap.id, userId)
              .pipe(
                catchError(() => this.roadmapApi.getUserNotifications(userId)),
                catchError(() => of([] as NotificationDto[]))
              ),
            milestones: this.roadmapApi
              .getMilestones(roadmap.id)
              .pipe(catchError(() => of([] as MilestoneDto[]))),
          });
        }),
        finalize(() => {
          this.loading = false;
        })
      )
      .subscribe({
        next: (data) => {
          if (data) {
            this.bindDashboardData(
              data.progress,
              data.graph,
              data.streak.currentStreak ?? 0,
              data.notifications,
              data.milestones
            );
          }
        },
        error: () => {
          this.errorMessage = 'Unable to load dashboard metrics from backend.';
          this.applyFallbackState();
        },
      });
  }

  private bindDashboardData(
    progress: ProgressSummaryDto | null,
    graph: RoadmapVisualResponse | null,
    streakDays: number,
    notifications: NotificationDto[],
    milestones: MilestoneDto[]
  ): void {
    const nodes = (graph?.nodes ?? []).slice().sort((a, b) => a.stepOrder - b.stepOrder);

    const totalSteps = this.resolveTotalSteps(progress, nodes);
    const completedSteps = this.resolveCompletedSteps(progress, nodes);
    const progressPercent = this.resolveProgressPercent(progress, totalSteps, completedSteps, graph);

    const pendingSteps = Math.max(totalSteps - completedSteps, 0);
    const inProgressSteps = nodes.filter((node) => this.isInProgress(node.status)).length;
    const interviewSignals = notifications.filter((item) =>
      /INTERVIEW|ASSESSMENT/i.test(`${item.type || ''} ${item.message || ''}`)
    ).length;

    const skillSummary = this.buildSkillSummary(nodes, progressPercent);
    const recommendationSummary = this.buildRecommendations(nodes, notifications);
    const upcoming = this.buildUpcomingSteps(nodes);
    const activityFeed = this.buildActivityFeed(notifications, milestones);

    this.readinessScore = this.calculateReadiness(progressPercent, streakDays, inProgressSteps);
    this.readinessBand = this.resolveReadinessBand(this.readinessScore);

    this.statCards = [
      {
        label: 'Roadmap Progress',
        value: `${progressPercent}%`,
        trend: `${completedSteps}/${Math.max(totalSteps, 1)} steps completed`,
        up: progressPercent >= 50,
        icon: 'activity',
      },
      {
        label: 'Pending Steps',
        value: String(pendingSteps),
        trend: pendingSteps === 0 ? 'All steps completed' : `${Math.min(pendingSteps, 3)} priority steps left`,
        up: pendingSteps === 0,
        icon: 'clock',
      },
      {
        label: 'Skills Covered',
        value: String(skillSummary.length),
        trend: skillSummary.length > 0 ? 'Derived from roadmap technologies' : 'No mapped skills yet',
        up: skillSummary.length > 0,
        icon: 'star',
      },
      {
        label: 'Interview Signals',
        value: String(interviewSignals),
        trend: interviewSignals > 0 ? 'Recent assessment/interview events' : 'No recent interview events',
        up: interviewSignals > 0,
        icon: 'users',
      },
    ];

    this.recommendations = recommendationSummary;
    this.skills = skillSummary;
    this.upcomingSteps = upcoming;
    this.streakDays = streakDays;
    this.weekDays = this.buildWeekDays(streakDays);
    this.activities = activityFeed.map(({ timestamp: _timestamp, ...rest }) => rest);
  }

  private resolveTotalSteps(
    progress: ProgressSummaryDto | null,
    nodes: RoadmapNodeDto[]
  ): number {
    if ((progress?.totalSteps ?? 0) > 0) {
      return progress!.totalSteps;
    }
    return nodes.length;
  }

  private resolveCompletedSteps(
    progress: ProgressSummaryDto | null,
    nodes: RoadmapNodeDto[]
  ): number {
    if ((progress?.completedSteps ?? 0) > 0) {
      return progress!.completedSteps;
    }
    return nodes.filter((node) => this.isCompleted(node.status)).length;
  }

  private resolveProgressPercent(
    progress: ProgressSummaryDto | null,
    totalSteps: number,
    completedSteps: number,
    graph: RoadmapVisualResponse | null
  ): number {
    if ((progress?.progressPercent ?? 0) > 0) {
      return Math.max(0, Math.min(100, progress!.progressPercent));
    }

    if ((graph?.progressPercent ?? 0) > 0) {
      return Math.max(0, Math.min(100, graph!.progressPercent));
    }

    if (totalSteps > 0) {
      return Math.round((completedSteps / totalSteps) * 100);
    }

    return 0;
  }

  private buildRecommendations(
    nodes: RoadmapNodeDto[],
    notifications: NotificationDto[]
  ): DashboardRecommendation[] {
    const items: DashboardRecommendation[] = [];
    const sortedNodes = nodes.slice().sort((a, b) => a.stepOrder - b.stepOrder);

    const firstInProgress = sortedNodes.find((node) => this.isInProgress(node.status));
    if (firstInProgress) {
      items.push({
        priority: 'High',
        badgeColor: '#ef4444',
        title: `Continue ${firstInProgress.title}`,
        desc: `Step ${firstInProgress.stepOrder} is currently in progress. Finishing it will unlock the next node.`,
        time: firstInProgress.unlockedAt
          ? this.formatRelativeTime(firstInProgress.unlockedAt)
          : 'Current focus',
      });
    }

    const firstAvailable = sortedNodes.find(
      (node) => node.status === 'AVAILABLE' || node.status === 'LOCKED'
    );
    if (firstAvailable) {
      items.push({
        priority: firstAvailable.status === 'AVAILABLE' ? 'Medium' : 'Low',
        badgeColor: firstAvailable.status === 'AVAILABLE' ? '#f59e0b' : '#6366f1',
        title: `Prepare ${firstAvailable.title}`,
        desc: `Upcoming roadmap step ${firstAvailable.stepOrder} with an estimate of ${Math.max(firstAvailable.estimatedDays || 0, 1)} day(s).`,
        time: firstAvailable.status === 'AVAILABLE' ? 'Ready now' : 'Locked',
      });
    }

    const notifRecs = notifications
      .slice()
      .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
      .slice(0, 3)
      .map((item) => {
        const priority = this.resolvePriority(item.type);
        return {
          priority,
          badgeColor:
            priority === 'High' ? '#ef4444' : priority === 'Medium' ? '#f59e0b' : '#6366f1',
          title: item.type ? this.toTitleCase(item.type.replace(/_/g, ' ')) : 'Roadmap update',
          desc: item.message,
          time: this.formatRelativeTime(item.createdAt),
        } as DashboardRecommendation;
      });

    const merged = [...items, ...notifRecs];
    if (merged.length > 0) {
      return merged.slice(0, 4);
    }

    return [
      {
        priority: 'Low',
        badgeColor: '#6366f1',
        title: 'Roadmap generated',
        desc: 'Your dashboard is waiting for the first roadmap updates from backend events.',
        time: 'Just now',
      },
    ];
  }

  private buildSkillSummary(nodes: RoadmapNodeDto[], progressPercent: number): DashboardSkill[] {
    const technologies = nodes
      .flatMap((node) => (node.technologies || '').split(','))
      .map((item) => item.trim())
      .filter((item) => item.length > 0);

    if (technologies.length === 0) {
      return [];
    }

    const weights = new Map<string, number>();
    for (const tech of technologies) {
      const key = this.toTitleCase(tech);
      weights.set(key, (weights.get(key) ?? 0) + 1);
    }

    const palette = ['#2ee8a5', '#3b82f6', '#f59e0b', '#8b5cf6', '#ef4444', '#14b8a6'];
    const maxWeight = Math.max(...weights.values());

    return [...weights.entries()]
      .sort((a, b) => b[1] - a[1])
      .slice(0, 5)
      .map(([name, weight], index) => {
        const normalized = maxWeight > 0 ? weight / maxWeight : 0;
        const pct = Math.max(20, Math.min(95, Math.round(normalized * 75 + progressPercent * 0.2)));
        return {
          name,
          pct,
          color: palette[index % palette.length],
        };
      });
  }

  private buildUpcomingSteps(nodes: RoadmapNodeDto[]): DashboardUpcomingStep[] {
    return nodes
      .slice()
      .sort((a, b) => a.stepOrder - b.stepOrder)
      .slice(0, 4)
      .map((node) => ({
        id: node.id,
        text: node.title,
        due: this.describeStepTiming(node),
        done: this.isCompleted(node.status),
      }));
  }

  private buildActivityFeed(
    notifications: NotificationDto[],
    milestones: MilestoneDto[]
  ): DashboardActivity[] {
    const notificationActivity: DashboardActivity[] = notifications.map((item) => ({
      text: item.message,
      time: this.formatRelativeTime(item.createdAt),
      color: this.resolveActivityColor(item.type),
      timestamp: new Date(item.createdAt).getTime() || 0,
    }));

    const milestoneActivity: DashboardActivity[] = milestones
      .filter((item) => !!item.reachedAt)
      .map((item) => ({
        text: `Milestone reached: ${item.title}`,
        time: this.formatRelativeTime(item.reachedAt!),
        color: '#2ee8a5',
        timestamp: new Date(item.reachedAt!).getTime() || 0,
      }));

    return [...notificationActivity, ...milestoneActivity]
      .sort((a, b) => b.timestamp - a.timestamp)
      .slice(0, 6);
  }

  private buildWeekDays(streakDays: number): DashboardWeekDay[] {
    const labels = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
    const today = new Date();
    const todayIndex = (today.getDay() + 6) % 7;
    const active = new Set<number>();
    const streakSpan = Math.min(Math.max(streakDays, 0), labels.length);

    for (let offset = 0; offset < streakSpan; offset += 1) {
      active.add((todayIndex - offset + labels.length) % labels.length);
    }

    return labels.map((day, index) => ({
      day,
      active: active.has(index),
      today: index === todayIndex,
    }));
  }

  private calculateReadiness(
    progressPercent: number,
    streakDays: number,
    inProgressSteps: number
  ): number {
    const score = Math.round(
      progressPercent * 0.65 +
        Math.min(streakDays, 14) * 2 +
        Math.min(inProgressSteps, 2) * 6
    );
    return Math.max(0, Math.min(100, score));
  }

  private resolveReadinessBand(score: number): string {
    if (score >= 80) {
      return 'well ahead of pace';
    }
    if (score >= 60) {
      return 'above average';
    }
    if (score >= 40) {
      return 'building momentum';
    }
    return 'at an early growth stage';
  }

  private describeStepTiming(node: RoadmapNodeDto): string {
    if (this.isCompleted(node.status) && node.completedAt) {
      return `Completed ${this.formatRelativeTime(node.completedAt)}`;
    }

    if (this.isInProgress(node.status)) {
      return 'Currently active';
    }

    if ((node.estimatedDays ?? 0) > 0) {
      return `Planned in ~${node.estimatedDays} day(s)`;
    }

    return node.status === 'LOCKED' ? 'Locked until previous step' : 'Ready when you are';
  }

  private resolvePriority(type: string): 'High' | 'Medium' | 'Low' {
    const normalized = (type || '').toUpperCase();
    if (normalized.includes('ALERT') || normalized.includes('WARNING')) {
      return 'High';
    }
    if (normalized.includes('MILESTONE') || normalized.includes('STEP')) {
      return 'Medium';
    }
    return 'Low';
  }

  private resolveActivityColor(type: string): string {
    const normalized = (type || '').toUpperCase();
    if (normalized.includes('MILESTONE')) {
      return '#2ee8a5';
    }
    if (normalized.includes('STEP')) {
      return '#3b82f6';
    }
    if (normalized.includes('ALERT') || normalized.includes('WARNING')) {
      return '#f59e0b';
    }
    return '#8b5cf6';
  }

  private formatRelativeTime(value: string): string {
    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) {
      return 'recently';
    }

    const deltaMinutes = Math.max(1, Math.floor((Date.now() - parsed.getTime()) / 60000));
    if (deltaMinutes < 60) {
      return `${deltaMinutes} min ago`;
    }

    const deltaHours = Math.floor(deltaMinutes / 60);
    if (deltaHours < 24) {
      return `${deltaHours} hour${deltaHours > 1 ? 's' : ''} ago`;
    }

    const deltaDays = Math.floor(deltaHours / 24);
    return `${deltaDays} day${deltaDays > 1 ? 's' : ''} ago`;
  }

  private isInProgress(status: string | undefined): boolean {
    return (status || '').toUpperCase() === 'IN_PROGRESS';
  }

  private isCompleted(status: string | undefined): boolean {
    const normalized = (status || '').toUpperCase();
    return normalized === 'COMPLETED' || normalized === 'SKIPPED' || normalized === 'DONE';
  }

  private toTitleCase(value: string): string {
    return value
      .split(/\s+/)
      .filter((token) => token.length > 0)
      .map((token) => token.charAt(0).toUpperCase() + token.slice(1).toLowerCase())
      .join(' ');
  }

  private applyFallbackState(): void {
    this.readinessScore = 0;
    this.readinessBand = 'starting to build momentum';
    this.statCards = [];
    this.recommendations = [];
    this.skills = [];
    this.upcomingSteps = [];
    this.streakDays = 0;
    this.weekDays = this.buildWeekDays(0);
    this.activities = [];
  }
}
