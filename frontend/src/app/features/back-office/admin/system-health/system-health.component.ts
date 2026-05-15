import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LUCIDE_ICONS } from '../../../../shared/lucide-icons';

/* ══════════ INTERFACES ══════════ */

type ServiceStatus = 'Operational' | 'Degraded' | 'Down' | 'Maintenance';
type IncidentSeverity = 'Critical' | 'Degraded' | 'Maintenance' | 'Resolved';
type LogLevel = 'Critical' | 'Error' | 'Warning' | 'Info';

interface ServiceMetric {
  label: string;
  value: string;
  amber?: boolean;       // amber highlight (cost metrics)
  barValue?: number;     // 0-100 for bar display
  barMax?: string;       // label for max
}

interface PlatformService {
  id: string;
  name: string;
  description: string;
  icon: string;
  status: ServiceStatus;
  metrics: ServiceMetric[];
  warning?: string;      // inline warning message
}

interface Incident {
  id: number;
  title: string;
  service: string;
  severity: IncidentSeverity;
  duration: string;
  description: string;
  timestamp: string;
  resolved: boolean;
}

interface ErrorLog {
  id: number;
  level: LogLevel;
  timestamp: string;
  service: string;
  message: string;
  stackTrace: string;
}

interface UptimeDay {
  day: number;
  status: 'ok' | 'degraded' | 'outage' | 'none';
}

interface UptimeRow {
  service: string;
  pct: number;
  days: UptimeDay[];
}

interface DeployInfo {
  label: string;
  value: string;
  mono?: boolean;
  link?: string;
}

@Component({
  selector: 'app-system-health',
  standalone: true,
  imports: [CommonModule, FormsModule, LUCIDE_ICONS],
  templateUrl: './system-health.component.html',
  styleUrl: './system-health.component.scss'
})
export class SystemHealthComponent implements OnInit, OnDestroy {

  /* ── Refresh state ── */
  refreshCountdown = 30;
  isRefreshing = false;
  private countdownInterval: ReturnType<typeof setInterval> | null = null;

  /* ── Response time chart ── */
  rtRange: '1h' | '6h' | '24h' | '7d' = '24h';

  /* ── Error stream ── */
  streamMode: 'Live' | 'Paused' = 'Live';
  logLevelFilter: 'All' | LogLevel = 'All';
  expandedLogId: number | null = null;

  /* ── Modals ── */
  showRollbackModal = false;
  rollbackVersion = 'v1.4.1';
  rollbackConfirmText = '';
  showClearCacheModal = false;

  /* ── Available rollback versions ── */
  rollbackVersions = ['v1.4.1', 'v1.4.0', 'v1.3.9', 'v1.3.8', 'v1.3.7'];

  /* ══════════ OVERALL STATUS ══════════ */

  get overallStatus(): 'operational' | 'degraded' | 'critical' {
    const allServices = [...this.coreApi, ...this.aiServices, ...this.integrations, ...this.infrastructure];
    if (allServices.some(s => s.status === 'Down')) return 'critical';
    if (allServices.some(s => s.status === 'Degraded')) return 'degraded';
    return 'operational';
  }

  get degradedServices(): PlatformService[] {
    const all = [...this.coreApi, ...this.aiServices, ...this.integrations, ...this.infrastructure];
    return all.filter(s => s.status === 'Degraded' || s.status === 'Down');
  }

  /* ══════════ CORE API ══════════ */
  coreApi: PlatformService[] = [
    {
      id: 'main-api', name: 'Main REST API',
      description: 'Primary backend API handling all CRUD operations and business logic.',
      icon: 'server', status: 'Operational',
      metrics: [
        { label: 'Response Time', value: '142ms' },
        { label: 'Uptime', value: '99.98%' },
        { label: 'Requests/min', value: '2,840' },
      ]
    },
    {
      id: 'ws-server', name: 'WebSocket Server',
      description: 'Real-time streaming for AI responses and live notifications.',
      icon: 'radio', status: 'Operational',
      metrics: [
        { label: 'Response Time', value: '38ms' },
        { label: 'Uptime', value: '99.95%' },
        { label: 'Requests/min', value: '920' },
      ]
    },
    {
      id: 'auth-svc', name: 'Authentication Service',
      description: 'Handles OAuth flows, JWT issuance, session management, and MFA.',
      icon: 'shield', status: 'Operational',
      metrics: [
        { label: 'Response Time', value: '96ms' },
        { label: 'Uptime', value: '99.99%' },
        { label: 'Requests/min', value: '1,450' },
      ]
    },
  ];

  /* ══════════ AI SERVICES ══════════ */
  aiServices: PlatformService[] = [
    {
      id: 'openai', name: 'OpenAI GPT-4o',
      description: 'Powers skill assessment scoring, interview feedback, and CV enhancement.',
      icon: 'cpu', status: 'Operational',
      metrics: [
        { label: 'Response Time', value: '1,840ms' },
        { label: 'Uptime', value: '99.91%' },
        { label: 'Cost Today', value: '$14.20', amber: true },
      ]
    },
    {
      id: 'anthropic', name: 'Anthropic Claude API',
      description: 'Secondary AI provider for cross-validation and fallback processing.',
      icon: 'cpu', status: 'Degraded',
      metrics: [
        { label: 'Response Time', value: '2,410ms' },
        { label: 'Uptime', value: '98.72%' },
        { label: 'Cost Today', value: '$8.50', amber: true },
      ],
      warning: 'Elevated latency detected — responses averaging 40% slower than baseline.'
    },
    {
      id: 'hume', name: 'Hume AI (Emotion Analysis)',
      description: 'Analyzes interview audio for emotional signals and communication style.',
      icon: 'activity', status: 'Operational',
      metrics: [
        { label: 'Response Time', value: '3,200ms' },
        { label: 'Uptime', value: '99.85%' },
        { label: 'Jobs Queued', value: '12' },
      ]
    },
    {
      id: 'azure-video', name: 'Azure Video Indexer',
      description: 'Processes recorded interviews for transcription and visual analysis.',
      icon: 'film', status: 'Operational',
      metrics: [
        { label: 'Response Time', value: '4,100ms' },
        { label: 'Uptime', value: '99.78%' },
        { label: 'Videos Processing', value: '5' },
      ],
      warning: '2 jobs stalled — may require manual retry.'
    },
  ];

  /* ══════════ EXTERNAL INTEGRATIONS ══════════ */
  integrations: PlatformService[] = [
    {
      id: 'github-oauth', name: 'GitHub OAuth',
      description: 'Social login and repository verification for developer profiles.',
      icon: 'github', status: 'Operational',
      metrics: [
        { label: 'Response Time', value: '180ms' },
        { label: 'Uptime', value: '99.97%' },
        { label: 'Auth Success', value: '99.4%' },
      ]
    },
    {
      id: 'google-oauth', name: 'Google OAuth',
      description: 'Primary social login provider for candidate registration.',
      icon: 'globe', status: 'Operational',
      metrics: [
        { label: 'Response Time', value: '120ms' },
        { label: 'Uptime', value: '99.99%' },
        { label: 'Auth Success', value: '99.8%' },
      ]
    },
    {
      id: 'azure-ad', name: 'Microsoft Azure AD',
      description: 'Enterprise SSO for recruiter and admin accounts.',
      icon: 'grid', status: 'Operational',
      metrics: [
        { label: 'Response Time', value: '210ms' },
        { label: 'Uptime', value: '99.95%' },
        { label: 'Auth Success', value: '99.2%' },
      ]
    },
    {
      id: 'linkedin-oauth', name: 'LinkedIn OAuth',
      description: 'Profile import and professional network verification.',
      icon: 'link', status: 'Degraded',
      metrics: [
        { label: 'Response Time', value: '680ms' },
        { label: 'Uptime', value: '97.80%' },
        { label: 'Auth Success', value: '94.1%' },
      ],
      warning: 'Users may experience login failures — monitor closely.'
    },
    {
      id: 'stripe', name: 'Stripe Payments',
      description: 'Subscription billing, invoicing, and payment webhook processing.',
      icon: 'credit-card', status: 'Operational',
      metrics: [
        { label: 'Response Time', value: '95ms' },
        { label: 'Payment Success', value: '99.7%' },
        { label: 'Webhooks Today', value: '1,247' },
      ]
    },
  ];

  /* ══════════ INFRASTRUCTURE ══════════ */
  infrastructure: PlatformService[] = [
    {
      id: 'postgres', name: 'PostgreSQL Database',
      description: 'Primary relational datastore for users, jobs, assessments, and analytics.',
      icon: 'database', status: 'Operational',
      metrics: [
        { label: 'Query Latency', value: '8ms' },
        { label: 'Uptime', value: '99.99%' },
        { label: 'Active Conns', value: '42 / 100', barValue: 42, barMax: '100' },
      ]
    },
    {
      id: 'redis', name: 'Redis Cache',
      description: 'In-memory cache for sessions, rate limiting, and frequently accessed data.',
      icon: 'zap', status: 'Operational',
      metrics: [
        { label: 'Response Time', value: '1.2ms' },
        { label: 'Uptime', value: '99.99%' },
        { label: 'Cache Hit Rate', value: '94.7%' },
      ]
    },
    {
      id: 'kafka', name: 'Apache Kafka',
      description: 'Event streaming for async processing, notifications, and analytics ingestion.',
      icon: 'layers', status: 'Operational',
      metrics: [
        { label: 'Message Lag', value: '24' },
        { label: 'Uptime', value: '99.97%' },
        { label: 'Topics Active', value: '18' },
      ]
    },
    {
      id: 's3', name: 'AWS S3 / Azure Blob',
      description: 'Object storage for CVs, interview recordings, profile photos, and exports.',
      icon: 'hard-drive', status: 'Operational',
      metrics: [
        { label: 'Response Time', value: '45ms' },
        { label: 'Uptime', value: '99.999%' },
        { label: 'Storage Used', value: '234 GB', barValue: 47, barMax: '500 GB' },
      ]
    },
  ];

  /* ══════════ INCIDENTS ══════════ */
  incidents: Incident[] = [
    { id: 1, title: 'Anthropic Claude API Elevated Latency', service: 'Anthropic Claude', severity: 'Degraded', duration: 'Ongoing', description: 'Response times averaging 2.4s, up from a 1.5s baseline. Investigating rate-limit changes on Anthropic\'s side.', timestamp: 'Mar 01, 2025 — 09:14', resolved: false },
    { id: 2, title: 'LinkedIn OAuth Intermittent Failures', service: 'LinkedIn OAuth', severity: 'Degraded', duration: 'Ongoing', description: 'Auth success rate dropped to 94%. LinkedIn status page reports partial degradation on their end.', timestamp: 'Mar 01, 2025 — 07:48', resolved: false },
    { id: 3, title: 'OpenAI API Elevated Latency', service: 'OpenAI GPT-4o', severity: 'Degraded', duration: 'Lasted 43 minutes', description: 'GPT-4o response times spiked to 4.2s. Root cause was a regional Azure outage affecting OpenAI infrastructure. Auto-failover to Claude handled 60% of traffic.', timestamp: 'Feb 27, 2025 — 14:22', resolved: true },
    { id: 4, title: 'Scheduled PostgreSQL Maintenance', service: 'PostgreSQL', severity: 'Maintenance', duration: 'Lasted 8 minutes', description: 'Minor version upgrade to PostgreSQL 16.1.2 with zero-downtime migration. READ replicas handled traffic during primary failover.', timestamp: 'Feb 25, 2025 — 03:00', resolved: true },
    { id: 5, title: 'Redis Cache Eviction Storm', service: 'Redis Cache', severity: 'Critical', duration: 'Lasted 12 minutes', description: 'Memory pressure caused rapid key evictions, dropping cache hit rate to 34%. Scaled memory from 8GB to 16GB and tuned eviction policies.', timestamp: 'Feb 22, 2025 — 16:05', resolved: true },
    { id: 6, title: 'Stripe Webhook Delivery Delays', service: 'Stripe Payments', severity: 'Degraded', duration: 'Lasted 28 minutes', description: 'Webhook deliveries delayed up to 5 minutes due to Stripe infrastructure issues. No payment failures, only delayed subscription status updates.', timestamp: 'Feb 20, 2025 — 11:30', resolved: true },
    { id: 7, title: 'Hume AI Queue Backlog', service: 'Hume AI', severity: 'Degraded', duration: 'Lasted 1h 15m', description: 'Emotion analysis queue reached 340 pending jobs after a burst of 200 concurrent interviews. Increased worker parallelism from 4 to 12.', timestamp: 'Feb 18, 2025 — 09:00', resolved: true },
    { id: 8, title: 'GitHub OAuth Certificate Renewal', service: 'GitHub OAuth', severity: 'Maintenance', duration: 'Lasted 2 minutes', description: 'Automated TLS certificate rotation for the GitHub OAuth callback endpoint. Brief interruption handled by retry logic.', timestamp: 'Feb 15, 2025 — 02:00', resolved: true },
    { id: 9, title: 'Full Platform Outage', service: 'Main REST API', severity: 'Critical', duration: 'Lasted 6 minutes', description: 'Kubernetes cluster auto-scaler removed too many pods during a low-traffic window. Set minimum replica count to 3. All services recovered automatically.', timestamp: 'Feb 11, 2025 — 04:17', resolved: true },
    { id: 10, title: 'Azure Video Indexer Quota Exceeded', service: 'Azure Video Indexer', severity: 'Degraded', duration: 'Lasted 3 hours', description: 'Daily processing quota reached at 14:00. Upgraded to the next billing tier and implemented queue prioritization for interview videos.', timestamp: 'Feb 08, 2025 — 14:00', resolved: true },
  ];

  /* ══════════ ERROR STREAM ══════════ */
  errorLogs: ErrorLog[] = [
    { id: 1, level: 'Critical', timestamp: '14:32:07.441', service: 'Anthropic Claude', message: 'Connection timeout after 30000ms — request aborted', stackTrace: 'Error: ETIMEDOUT\n    at ClientRequest.<anonymous> (/app/services/ai/anthropic-client.ts:142:15)\n    at Object.onceWrapper (events.js:421:28)\n    at ClientRequest.emit (events.js:315:20)\n    at TLSSocket.socketErrorListener (_http_client.js:469:9)\n    at TLSSocket.emit (events.js:315:20)\n    at emitErrorNT (internal/streams/destroy.js:106:8)' },
    { id: 2, level: 'Error', timestamp: '14:31:55.218', service: 'Auth Service', message: 'LinkedIn OAuth callback failed: invalid_grant — token exchange rejected', stackTrace: 'OAuthError: invalid_grant\n    at LinkedInProvider.exchangeCode (/app/auth/providers/linkedin.ts:89:11)\n    at async AuthController.handleCallback (/app/auth/auth.controller.ts:156:22)\n    at async Layer.handle (/node_modules/express/lib/router/layer.js:95:5)' },
    { id: 3, level: 'Warning', timestamp: '14:31:42.890', service: 'Redis Cache', message: 'Cache miss rate above threshold — current: 8.2% (threshold: 5%)', stackTrace: 'CacheMonitor: miss_rate_alert\n    service: redis-primary\n    window: 5m\n    current_rate: 0.082\n    threshold: 0.05\n    affected_keys: session:*, user:profile:*' },
    { id: 4, level: 'Info', timestamp: '14:31:30.102', service: 'Main API', message: 'Health check passed — all dependencies responsive', stackTrace: 'HealthCheck report:\n    postgres: ok (8ms)\n    redis: ok (1ms)\n    kafka: ok (12ms)\n    s3: ok (45ms)\n    openai: ok (1840ms)\n    claude: timeout (30000ms) ← DEGRADED' },
    { id: 5, level: 'Error', timestamp: '14:30:58.774', service: 'Hume AI', message: 'Job #4821 failed: audio format unsupported (video/webm; codecs=vp8,opus)', stackTrace: 'AudioProcessingError: Unsupported format\n    at HumeJobProcessor.validateInput (/app/services/ai/hume-processor.ts:67:13)\n    at HumeJobProcessor.process (/app/services/ai/hume-processor.ts:42:10)\n    job_id: 4821\n    interview_id: usr_9283\n    format: video/webm; codecs=vp8,opus' },
    { id: 6, level: 'Warning', timestamp: '14:30:22.115', service: 'PostgreSQL', message: 'Slow query detected: 2,340ms — SELECT with missing index on assessments.user_id', stackTrace: 'SlowQueryLog:\n    duration: 2340.12ms\n    query: SELECT a.*, u.name FROM assessments a JOIN users u ON a.user_id = u.id WHERE a.status = $1 AND a.created_at > $2 ORDER BY a.score DESC LIMIT 100\n    plan: Seq Scan on assessments (cost=0.00..4521.00 rows=98000)\n    suggestion: CREATE INDEX idx_assessments_user_id_status ON assessments(user_id, status)' },
    { id: 7, level: 'Info', timestamp: '14:29:44.003', service: 'Kafka', message: 'Consumer group "analytics-ingest" rebalanced — 3 partitions assigned', stackTrace: 'KafkaRebalance:\n    group: analytics-ingest\n    topic: platform.events\n    partitions: [0, 1, 2]\n    strategy: cooperative-sticky\n    generation: 47' },
    { id: 8, level: 'Error', timestamp: '14:29:10.667', service: 'Azure Video', message: 'Video indexing job #v-2201 exceeded 10-minute timeout — marked as stalled', stackTrace: 'VideoIndexerTimeout:\n    job_id: v-2201\n    video_duration: 32m14s\n    upload_time: 14:18:55\n    status: processing → stalled\n    retry_count: 0\n    action: queued_for_retry' },
    { id: 9, level: 'Critical', timestamp: '14:28:33.991', service: 'Stripe', message: 'Webhook signature verification failed — potential replay attack', stackTrace: 'StripeWebhookError: Signature verification failed\n    at verifyWebhookSignature (/app/payments/stripe-webhook.ts:23:11)\n    endpoint: /api/webhooks/stripe\n    event_type: invoice.payment_succeeded\n    ip: 54.187.205.18\n    tolerance: 300s\n    Note: IP does not match Stripe webhook IPs — blocked' },
    { id: 10, level: 'Warning', timestamp: '14:27:55.442', service: 'Main API', message: 'Rate limit approaching for IP 192.168.1.42 — 280/300 requests in window', stackTrace: 'RateLimiter:\n    ip: 192.168.1.42\n    window: 60s\n    current: 280\n    limit: 300\n    endpoint: /api/jobs/search\n    user_agent: Mozilla/5.0 (bot pattern detected)' },
  ];

  /* ══════════ RESPONSE TIME CHART ══════════ */
  rtDataPoints = 24;
  rtMainApi     = [140, 138, 145, 142, 155, 148, 136, 140, 142, 145, 150, 160, 148, 145, 140, 138, 135, 142, 148, 155, 142, 138, 140, 142];
  rtAuthService = [95, 92, 98, 96, 102, 98, 90, 95, 96, 98, 100, 105, 98, 96, 92, 90, 88, 95, 100, 102, 95, 90, 92, 96];
  rtAiServices  = [1800, 1750, 1900, 1840, 2100, 2400, 2350, 2200, 2050, 1950, 1880, 1840, 1900, 1950, 2000, 2100, 2200, 2150, 2050, 1950, 1900, 1870, 1850, 1840];
  rtSlaThreshold = 300;

  get rtMax(): number {
    return Math.max(...this.rtAiServices, this.rtSlaThreshold + 100);
  }

  getLinePoints(data: number[], height: number, width: number): string {
    const stepX = width / (data.length - 1);
    return data.map((v, i) => {
      const y = height - (v / this.rtMax) * height;
      return `${i * stepX},${y}`;
    }).join(' ');
  }

  /* ══════════ UPTIME DATA ══════════ */
  uptimeData: UptimeRow[] = [
    { service: 'Main REST API', pct: 99.98, days: this.genUptime(99.98) },
    { service: 'WebSocket Server', pct: 99.95, days: this.genUptime(99.95) },
    { service: 'Auth Service', pct: 99.99, days: this.genUptime(99.99) },
    { service: 'OpenAI GPT-4o', pct: 99.91, days: this.genUptime(99.91, 2) },
    { service: 'Anthropic Claude', pct: 98.72, days: this.genUptime(98.72, 3) },
    { service: 'Hume AI', pct: 99.85, days: this.genUptime(99.85, 1) },
    { service: 'Azure Video Indexer', pct: 99.78, days: this.genUptime(99.78, 1) },
    { service: 'GitHub OAuth', pct: 99.97, days: this.genUptime(99.97) },
    { service: 'Google OAuth', pct: 99.99, days: this.genUptime(99.99) },
    { service: 'LinkedIn OAuth', pct: 97.80, days: this.genUptime(97.80, 4) },
    { service: 'Stripe Payments', pct: 99.95, days: this.genUptime(99.95, 1) },
    { service: 'PostgreSQL', pct: 99.99, days: this.genUptime(99.99) },
  ];

  private genUptime(pct: number, degradedDays = 0): UptimeDay[] {
    const days: UptimeDay[] = [];
    const outageDays = pct < 99 ? 1 : 0;
    for (let i = 1; i <= 30; i++) {
      if (outageDays > 0 && i === 15) { days.push({ day: i, status: 'outage' }); }
      else if (degradedDays > 0 && i > (30 - degradedDays - 2) && i <= (30 - 2)) { days.push({ day: i, status: 'degraded' }); }
      else { days.push({ day: i, status: 'ok' }); }
    }
    return days;
  }

  /* ══════════ INFRA METRICS ══════════ */
  cpuUsage = 34;
  memoryUsed = 6.2;
  memoryTotal = 16;
  get memoryPct(): number { return Math.round((this.memoryUsed / this.memoryTotal) * 100); }
  dbConnections = 42;
  dbMaxConnections = 100;
  get dbConnPct(): number { return Math.round((this.dbConnections / this.dbMaxConnections) * 100); }
  storageUsed = 234;
  storageTotal = 500;
  get storagePct(): number { return Math.round((this.storageUsed / this.storageTotal) * 100); }

  getDonutColor(pct: number): string {
    if (pct > 80) return '#ef4444';
    if (pct > 60) return '#f97316';
    return '#2ee8a5';
  }

  getDonutDash(pct: number): string {
    const circ = 2 * Math.PI * 36;
    const filled = (pct / 100) * circ;
    return `${filled} ${circ - filled}`;
  }

  /* ══════════ DEPLOYMENT ══════════ */
  deployInfo: DeployInfo[] = [
    { label: 'Version', value: 'v1.4.2' },
    { label: 'Environment', value: 'Production' },
    { label: 'Last Deploy', value: 'Feb 20, 2025 at 11:42 UTC' },
    { label: 'Deployed By', value: 'CI/CD — GitHub Actions' },
    { label: 'Backend Build', value: 'Spring Boot 3.2.1 — Java 21' },
    { label: 'Frontend Build', value: 'Angular 17.3.0' },
    { label: 'Database Version', value: 'PostgreSQL 16.1' },
    { label: 'Commit Hash', value: 'a3f9c12', mono: true, link: 'https://github.com' },
  ];

  /* ══════════ COMPUTED ══════════ */

  get totalServiceCount(): number {
    return this.coreApi.length + this.aiServices.length + this.integrations.length + this.infrastructure.length;
  }

  get filteredErrorLogs(): ErrorLog[] {
    if (this.logLevelFilter === 'All') return this.errorLogs;
    return this.errorLogs.filter(l => l.level === this.logLevelFilter);
  }

  getStatusClass(s: ServiceStatus): string {
    switch (s) {
      case 'Operational': return 'st--green';
      case 'Degraded': return 'st--orange';
      case 'Down': return 'st--red';
      case 'Maintenance': return 'st--blue';
    }
  }

  getSeverityClass(s: IncidentSeverity): string {
    switch (s) {
      case 'Critical': return 'sev--red';
      case 'Degraded': return 'sev--orange';
      case 'Maintenance': return 'sev--blue';
      case 'Resolved': return 'sev--gray';
    }
  }

  getLogClass(l: LogLevel): string {
    switch (l) {
      case 'Critical': return 'log--critical';
      case 'Error': return 'log--error';
      case 'Warning': return 'log--warning';
      case 'Info': return 'log--info';
    }
  }

  toggleLogExpand(id: number): void {
    this.expandedLogId = this.expandedLogId === id ? null : id;
  }

  copyStackTrace(trace: string): void {
    navigator.clipboard.writeText(trace);
  }

  /* ══════════ LIFECYCLE ══════════ */

  ngOnInit(): void {
    this.startCountdown();
  }

  ngOnDestroy(): void {
    if (this.countdownInterval) clearInterval(this.countdownInterval);
  }

  private startCountdown(): void {
    this.countdownInterval = setInterval(() => {
      this.refreshCountdown--;
      if (this.refreshCountdown <= 0) {
        this.simulateRefresh();
      }
    }, 1000);
  }

  private simulateRefresh(): void {
    this.isRefreshing = true;
    this.refreshCountdown = 30;
    setTimeout(() => { this.isRefreshing = false; }, 1200);
  }

  get canRollback(): boolean {
    return this.rollbackConfirmText === 'CONFIRM ROLLBACK';
  }
}
