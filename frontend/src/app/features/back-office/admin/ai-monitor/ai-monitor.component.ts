import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LUCIDE_ICONS } from '../../../../shared/lucide-icons';

interface MetricCard {
  label: string;
  value: string;
  subValue?: string;
  trend?: number;          // percentage change (positive = up, negative = down)
  trendGood?: boolean;     // is positive trend good?
  icon: string;
  type: 'rating' | 'percent' | 'latency' | 'count';
  ratingValue?: number;    // for star display (out of 5)
  isWarning?: boolean;
}

interface RatingDataPoint {
  date: string;
  skill: number;
  project: number;
  job: number;
  interview: number;
  profile: number;
  course: number;
}

interface LowestRecommendation {
  id: number;
  text: string;
  type: string;
  rating: number;
  comment: string | null;
}

interface ModuleUsage {
  module: string;
  calls: number;
  color: string;
  maxCalls: number;
}

interface PromptPerformance {
  feature: string;
  avgScore: number;
  callsToday: number;
  p95Latency: number;
  status: 'Healthy' | 'Degraded' | 'Error';
}

@Component({
  selector: 'app-ai-monitor',
  standalone: true,
  imports: [CommonModule, FormsModule, LUCIDE_ICONS],
  templateUrl: './ai-monitor.component.html',
  styleUrl: './ai-monitor.component.scss'
})
export class AiMonitorComponent {

  /* ── Date Range ── */
  dateRange = 'Last 30 days';
  dateRanges = ['Last 7 days', 'Last 30 days', 'Last 90 days'];

  /* ── Metric Cards ── */
  metrics: MetricCard[] = [
    {
      label: 'Avg. Recommendation Rating',
      value: '4.2',
      icon: 'star',
      type: 'rating',
      ratingValue: 4.2,
      trend: 3.5,
      trendGood: true
    },
    {
      label: 'Recommendation Acceptance',
      value: '73%',
      icon: 'check',
      type: 'percent',
      trend: 5.2,
      trendGood: true
    },
    {
      label: 'Avg. GPT Response Time',
      value: '1,842ms',
      icon: 'clock',
      type: 'latency',
      trend: -8.3,
      trendGood: true,
      isWarning: false
    },
    {
      label: 'Total AI API Calls Today',
      value: '24,891',
      icon: 'zap',
      type: 'count',
      subValue: '~$47.30 est. cost',
      trend: 12.1,
      trendGood: true
    }
  ];

  /* ── Rating Chart Data (simulated SVG points) ── */
  chartLines = [
    { label: 'Skill',     color: '#2ee8a5', points: [3.8, 4.0, 4.1, 3.9, 4.2, 4.3, 4.1, 4.4, 4.2, 4.5, 4.3, 4.4] },
    { label: 'Project',   color: '#0ea5e9', points: [3.5, 3.7, 3.6, 3.8, 3.9, 3.7, 4.0, 3.8, 4.1, 4.0, 4.2, 4.1] },
    { label: 'Job',       color: '#a78bfa', points: [4.2, 4.1, 4.3, 4.0, 4.2, 4.4, 4.3, 4.5, 4.4, 4.6, 4.5, 4.7] },
    { label: 'Interview', color: '#fbbf24', points: [3.2, 3.4, 3.5, 3.3, 3.6, 3.5, 3.8, 3.7, 3.9, 3.8, 4.0, 3.9] },
    { label: 'Profile',   color: '#f97316', points: [4.0, 3.9, 4.1, 4.2, 4.0, 4.3, 4.1, 4.4, 4.3, 4.5, 4.4, 4.6] },
    { label: 'Course',    color: '#ec4899', points: [3.6, 3.8, 3.7, 3.9, 4.0, 3.8, 4.1, 4.0, 4.2, 4.1, 4.3, 4.2] },
  ];

  chartMonths = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

  getChartPath(points: number[]): string {
    const w = 520, h = 160;
    const minY = 2.5, maxY = 5.0;
    const stepX = w / (points.length - 1);
    return points.map((p, i) => {
      const x = i * stepX;
      const y = h - ((p - minY) / (maxY - minY)) * h;
      return `${i === 0 ? 'M' : 'L'}${x.toFixed(1)},${y.toFixed(1)}`;
    }).join(' ');
  }

  /* ── Lowest Rated Recommendations ── */
  lowestRated: LowestRecommendation[] = [
    { id: 1, text: 'Consider learning Rust for systems programming — it offers memory safety without a garbage collector.', type: 'Skill', rating: 1.5, comment: 'Completely irrelevant to my frontend focus.' },
    { id: 2, text: 'Apply for "Senior Blockchain Developer" at CryptoFin — matches your profile 78%.', type: 'Job', rating: 1.8, comment: 'I have zero blockchain experience.' },
    { id: 3, text: 'Your resume would benefit from adding a "Hobbies" section with personal interests.', type: 'Profile', rating: 2.0, comment: null },
    { id: 4, text: 'Practice the "Implement a Red-Black Tree from scratch" question for your upcoming interview.', type: 'Interview', rating: 2.1, comment: 'Way too advanced for the role I\'m interviewing for.' },
    { id: 5, text: 'Start the "Introduction to Machine Learning with Python" course to broaden your skill set.', type: 'Course', rating: 2.2, comment: 'Already completed this exact course.' },
  ];

  getStarArray(rating: number): ('full' | 'half' | 'empty')[] {
    const stars: ('full' | 'half' | 'empty')[] = [];
    for (let i = 1; i <= 5; i++) {
      if (rating >= i) stars.push('full');
      else if (rating >= i - 0.5) stars.push('half');
      else stars.push('empty');
    }
    return stars;
  }

  /* ── Module Usage (bar chart) ── */
  moduleUsage: ModuleUsage[] = [
    { module: 'Assessment',     calls: 8420,  color: '#2ee8a5', maxCalls: 10000 },
    { module: 'Interview',      calls: 6180,  color: '#0ea5e9', maxCalls: 10000 },
    { module: 'CV Enhancement', calls: 4950,  color: '#a78bfa', maxCalls: 10000 },
    { module: 'Job Matching',   calls: 3740,  color: '#fbbf24', maxCalls: 10000 },
    { module: 'Weekly Reports', calls: 1601,  color: '#f97316', maxCalls: 10000 },
  ];

  getBarWidth(calls: number): number {
    const max = Math.max(...this.moduleUsage.map(m => m.calls));
    return (calls / max) * 100;
  }

  /* ── Prompt Performance table ── */
  promptPerformance: PromptPerformance[] = [
    { feature: 'Skill Assessment Scorer',   avgScore: 4.5, callsToday: 3218, p95Latency: 1240, status: 'Healthy' },
    { feature: 'Interview Question Generator', avgScore: 4.1, callsToday: 2840, p95Latency: 2180, status: 'Healthy' },
    { feature: 'CV Content Enhancer',       avgScore: 4.3, callsToday: 1920, p95Latency: 1850, status: 'Healthy' },
    { feature: 'Job Match Ranker',          avgScore: 3.8, callsToday: 1465, p95Latency: 2940, status: 'Degraded' },
    { feature: 'Weekly Report Summarizer',  avgScore: 4.0, callsToday: 980,  p95Latency: 1560, status: 'Healthy' },
    { feature: 'Profile Recommendation',    avgScore: 3.2, callsToday: 1120, p95Latency: 3450, status: 'Degraded' },
    { feature: 'Course Suggestion Engine',  avgScore: 3.6, callsToday: 870,  p95Latency: 4200, status: 'Error' },
  ];

  getStatusClass(s: string): string {
    return s === 'Healthy' ? 'st--green' : s === 'Degraded' ? 'st--yellow' : 'st--red';
  }

  getLatencyClass(ms: number): string {
    return ms > 3000 ? 'latency--red' : ms > 2500 ? 'latency--yellow' : '';
  }

  /* ── Archive action (simulated) ── */
  archiveType(rec: LowestRecommendation): void {
    this.lowestRated = this.lowestRated.filter(r => r.id !== rec.id);
  }
}
