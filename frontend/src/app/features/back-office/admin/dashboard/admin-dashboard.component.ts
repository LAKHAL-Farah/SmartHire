import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LUCIDE_ICONS } from '../../../../shared/lucide-icons';

/* ── Types ── */
interface KpiCard {
  label: string;
  value: string;
  sub?: string;
  trend: string;
  trendUp: boolean;
  sparkline: number[];
}

interface ActivityEvent {
  type: 'user' | 'system' | 'error';
  text: string;
  time: string;
}

interface PendingAction {
  text: string;
  action: string;
}

interface FunnelRow {
  label: string;
  count: number;
  dropoff?: string;
}

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule, LUCIDE_ICONS],
  templateUrl: './admin-dashboard.component.html',
  styleUrl: './admin-dashboard.component.scss'
})
export class AdminDashboardComponent {
  /* ── KPIs ── */
  kpis: KpiCard[] = [
    { label: 'Total Users', value: '12,847', trend: '+3.2%', trendUp: true, sparkline: [30,35,32,40,38,44,50,48,55,52,58] },
    { label: 'Active Subscriptions', value: '2,341', sub: '$28,092 MRR', trend: '+1.8%', trendUp: true, sparkline: [20,22,21,25,24,28,30,29,32,31,34] },
    { label: 'Assessments Today', value: '487', trend: '+12.4%', trendUp: true, sparkline: [40,35,42,45,38,50,55,48,52,60,58] },
    { label: 'Interview Sessions Today', value: '156', trend: '-2.1%', trendUp: false, sparkline: [25,28,30,26,24,22,28,32,30,28,26] },
    { label: 'Job Offers Live', value: '1,203', trend: '+5.7%', trendUp: true, sparkline: [60,58,62,65,63,68,70,72,75,73,78] },
    { label: 'Open Support Flags', value: '7', trend: '-22%', trendUp: true, sparkline: [15,12,14,10,9,8,10,8,7,8,7] },
  ];

  /* ── User Growth chart (simulated 30 days) ── */
  candidateData = [12,18,15,22,20,25,28,24,30,26,32,28,35,30,38,34,40,36,42,38,44,40,46,42,48,44,50,46,52,48];
  recruiterData = [3,5,4,6,5,7,8,6,9,7,10,8,11,9,12,10,13,11,14,12,15,13,16,14,17,15,18,16,19,17];

  getChartPath(data: number[], maxVal: number, width: number, height: number): string {
    const step = width / (data.length - 1);
    return data.map((v, i) => {
      const x = i * step;
      const y = height - (v / maxVal) * height;
      return `${i === 0 ? 'M' : 'L'}${x},${y}`;
    }).join(' ');
  }

  getChartDots(data: number[], maxVal: number, width: number, height: number): { x: number; y: number }[] {
    const step = width / (data.length - 1);
    return data.map((v, i) => ({
      x: i * step,
      y: height - (v / maxVal) * height,
    }));
  }

  /* ── Conversion Funnel ── */
  funnel: FunnelRow[] = [
    { label: 'Registered', count: 12847 },
    { label: 'Completed Onboarding', count: 8421, dropoff: '-34.4%' },
    { label: 'Took Assessment', count: 5872, dropoff: '-30.3%' },
    { label: 'Premium Subscriber', count: 2341, dropoff: '-60.1%' },
  ];

  /* ── Activity Feed ── */
  activities: ActivityEvent[] = [
    { type: 'user', text: 'New recruiter registered — TechCorp', time: '2 min ago' },
    { type: 'user', text: 'Assessment completed — Score 78', time: '5 min ago' },
    { type: 'system', text: 'Job offer published — Backend Engineer', time: '8 min ago' },
    { type: 'error', text: 'Stripe payment failed — user #4421', time: '12 min ago' },
    { type: 'user', text: 'New user registered — sarah.miller@gmail.com', time: '15 min ago' },
    { type: 'system', text: 'AI model retrained — v2.4.1 deployed', time: '22 min ago' },
    { type: 'user', text: 'Interview session completed — Score 8.4', time: '28 min ago' },
    { type: 'error', text: 'Webhook timeout — integration #connect_12', time: '35 min ago' },
    { type: 'user', text: 'CV uploaded — john.dev@outlook.com', time: '41 min ago' },
    { type: 'system', text: 'Cache cleared — CDN refresh', time: '48 min ago' },
    { type: 'user', text: 'Subscription upgraded — Free → Premium', time: '52 min ago' },
    { type: 'system', text: 'Backup completed — 14.2 GB', time: '1 hour ago' },
  ];

  /* ── Pending Actions ── */
  pendingActions: PendingAction[] = [
    { text: '3 recruiters awaiting verification', action: 'Review' },
    { text: '2 job offers flagged for review', action: 'Review' },
    { text: '1 support ticket open > 24h', action: 'Resolve' },
  ];

  /* ── System Health ── */
  systemStatus = [
    { label: 'API Status', status: 'Operational', detail: 'Avg response: 142ms', ok: true },
    { label: 'AI Services', status: 'GPT-4o — Operational', detail: 'Avg response: 1.8s', ok: true },
    { label: 'Database', status: 'PostgreSQL — Normal', detail: 'Avg query: 12ms', ok: true },
  ];

  /* ── Revenue ── */
  revenueToday = '$1,842';
  mrr = '$28,092';
  planBreakdown = [
    { label: 'Free', pct: 62, color: 'rgba(255,255,255,.15)' },
    { label: 'Premium', pct: 26, color: '#2ee8a5' },
    { label: 'Recruiter', pct: 12, color: '#8b5cf6' },
  ];

  getDonutStroke(index: number): string {
    const pcts = this.planBreakdown.map(p => p.pct);
    const circumference = 2 * Math.PI * 36;
    const offset = pcts.slice(0, index).reduce((a, b) => a + b, 0);
    const dashLen = (pcts[index] / 100) * circumference;
    const dashOff = -(offset / 100) * circumference;
    return `${dashLen} ${circumference - dashLen}`;
  }

  getDonutOffset(index: number): number {
    const pcts = this.planBreakdown.map(p => p.pct);
    const circumference = 2 * Math.PI * 36;
    return -(pcts.slice(0, index).reduce((a, b) => a + b, 0) / 100) * circumference;
  }

  getSparklinePath(data: number[]): string {
    const w = 80, h = 24;
    const max = Math.max(...data);
    const step = w / (data.length - 1);
    return data.map((v, i) => {
      const x = i * step;
      const y = h - (v / max) * h;
      return `${i === 0 ? 'M' : 'L'}${x},${y}`;
    }).join(' ');
  }

  getEventColor(type: string): string {
    switch (type) {
      case 'user': return 'var(--accent-teal)';
      case 'system': return '#f59e0b';
      case 'error': return '#ef4444';
      default: return 'rgba(255,255,255,.3)';
    }
  }
}
