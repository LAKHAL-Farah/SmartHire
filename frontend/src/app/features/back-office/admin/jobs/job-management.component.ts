import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LUCIDE_ICONS } from '../../../../shared/lucide-icons';

interface JobOffer {
  id: number;
  title: string;
  company: string;
  companyLogo: string;
  category: string;
  locationType: 'Remote' | 'On-site' | 'Hybrid';
  status: 'Open' | 'Closed' | 'Flagged';
  applications: number;
  posted: string;
  salary: string;
  description: string;
  requirements: string[];
  flagHistory?: FlagEntry[];
}

interface FlagEntry {
  reason: string;
  source: 'AI System' | 'User Report';
  date: string;
  detail: string;
}

@Component({
  selector: 'app-job-management',
  standalone: true,
  imports: [CommonModule, FormsModule, LUCIDE_ICONS],
  templateUrl: './job-management.component.html',
  styleUrl: './job-management.component.scss'
})
export class JobManagementComponent {
  /* ── Filters ── */
  searchQuery = '';
  statusFilter = 'All';
  categoryFilter = 'All';
  dateFilter = 'All';
  statuses = ['All', 'Open', 'Closed', 'Flagged'];
  categories = ['All', 'Engineering', 'Design', 'Marketing', 'Sales', 'Finance', 'HR', 'Data Science', 'DevOps', 'Product'];
  dateRanges = ['All', 'Today', 'This Week', 'This Month', 'Last 3 Months'];

  /* ── Pagination ── */
  currentPage = 1;
  pageSize = 15;
  get totalPages(): number { return Math.ceil(this.filteredJobs.length / this.pageSize); }
  get paginatedJobs(): JobOffer[] {
    const start = (this.currentPage - 1) * this.pageSize;
    return this.filteredJobs.slice(start, start + this.pageSize);
  }

  /* ── Selection / Bulk ── */
  selectedIds = new Set<number>();
  allChecked = false;

  /* ── Action dropdown ── */
  openMenuId: number | null = null;

  /* ── Drawer ── */
  drawerOpen = false;
  drawerJob: JobOffer | null = null;

  /* ── Data ── */
  jobs: JobOffer[] = [
    { id: 1, title: 'Senior Backend Engineer', company: 'TechCorp Solutions', companyLogo: 'TC', category: 'Engineering', locationType: 'Remote', status: 'Open', applications: 47, posted: 'Mar 15, 2024', salary: '$120,000 – $160,000', description: 'We are looking for a Senior Backend Engineer to join our platform team. You will design and build scalable microservices, optimize database performance, and mentor junior developers. Our stack includes Node.js, PostgreSQL, Redis, and Kubernetes.', requirements: ['5+ years backend experience', 'Node.js / Python proficiency', 'PostgreSQL / MongoDB', 'Kubernetes & Docker', 'System design skills'] },
    { id: 2, title: 'Product Designer', company: 'CloudNine SaaS', companyLogo: 'C9', category: 'Design', locationType: 'Hybrid', status: 'Open', applications: 32, posted: 'Mar 18, 2024', salary: '$95,000 – $125,000', description: 'Join our design team to shape the future of cloud infrastructure UX. You will lead end-to-end design for our dashboard products, conduct user research, and collaborate closely with engineering.', requirements: ['3+ years product design', 'Figma proficiency', 'Design systems experience', 'User research skills', 'B2B SaaS background preferred'] },
    { id: 3, title: 'Full Stack Developer', company: 'ShopWave Commerce', companyLogo: 'SW', category: 'Engineering', locationType: 'On-site', status: 'Open', applications: 28, posted: 'Mar 20, 2024', salary: '$90,000 – $130,000', description: 'Build and maintain our e-commerce platform serving millions of users across Asia-Pacific. Work on both frontend (React) and backend (Java Spring Boot) components.', requirements: ['3+ years full-stack', 'React + TypeScript', 'Java Spring Boot', 'SQL databases', 'E-commerce experience a plus'] },
    { id: 4, title: 'Marketing Manager - Growth', company: 'EduSpark Global', companyLogo: 'ES', category: 'Marketing', locationType: 'Remote', status: 'Flagged', applications: 15, posted: 'Mar 22, 2024', salary: '$70,000 – $95,000', description: 'Lead our growth marketing initiatives across paid channels, content marketing, and partnerships. Drive student acquisition and retention for our online learning platform.', requirements: ['4+ years growth marketing', 'Paid ads (Google, Meta)', 'Analytics tools', 'Content strategy', 'EdTech experience preferred'], flagHistory: [{ reason: 'Misleading salary range', source: 'AI System', date: 'Mar 24, 2024', detail: 'AI detected salary range significantly below market average for similar roles with listed requirements. The role requires 4+ years experience but offers entry-level compensation.' }] },
    { id: 5, title: 'DevOps Engineer', company: 'HealthBridge AI', companyLogo: 'HB', category: 'DevOps', locationType: 'Remote', status: 'Open', applications: 19, posted: 'Mar 25, 2024', salary: '$110,000 – $145,000', description: 'Own our cloud infrastructure and CI/CD pipelines. Ensure 99.99% uptime for our healthcare AI platform that serves clinical diagnostics in real-time.', requirements: ['4+ years DevOps / SRE', 'AWS / GCP', 'Terraform / Pulumi', 'Kubernetes', 'Healthcare compliance (HIPAA) a plus'] },
    { id: 6, title: 'Data Scientist - NLP', company: 'ConsultPro Group', companyLogo: 'CP', category: 'Data Science', locationType: 'Hybrid', status: 'Open', applications: 22, posted: 'Mar 26, 2024', salary: '$130,000 – $170,000', description: 'Apply NLP and machine learning to automate consulting deliverables. Build models for document analysis, sentiment extraction, and automated report generation.', requirements: ['PhD or MS in CS/ML', 'NLP expertise (transformers, LLMs)', 'Python, PyTorch/TensorFlow', 'Production ML experience', 'Consulting domain knowledge a plus'] },
    { id: 7, title: 'Sales Development Rep', company: 'CloudNine SaaS', companyLogo: 'C9', category: 'Sales', locationType: 'On-site', status: 'Closed', applications: 64, posted: 'Feb 10, 2024', salary: '$55,000 – $75,000 + commission', description: 'Drive outbound prospecting for our enterprise cloud platform. Qualify leads, book demos, and work closely with Account Executives to close deals.', requirements: ['1+ year SDR experience', 'B2B SaaS background', 'Salesforce / HubSpot', 'Strong communication', 'Self-motivated'] },
    { id: 8, title: 'Blockchain Developer', company: 'FinanceHub Ltd', companyLogo: 'FH', category: 'Engineering', locationType: 'Remote', status: 'Flagged', applications: 8, posted: 'Mar 28, 2024', salary: '$200,000 – $500,000', description: 'URGENT HIRING! Make $500K working from home! Join our revolutionary DeFi protocol and earn massive bonuses. No experience needed - we train everyone!', requirements: ['Any experience level', 'Passion for crypto'], flagHistory: [{ reason: 'Spam / Misleading content', source: 'AI System', date: 'Mar 28, 2024', detail: 'AI flagged this listing for spam indicators: unrealistic salary range, urgency language ("URGENT HIRING"), misleading claims ("no experience needed" for a blockchain developer role).' }, { reason: 'Inappropriate content', source: 'User Report', date: 'Mar 29, 2024', detail: 'User #8421 reported: "This looks like a scam posting. The salary and description do not match any legitimate blockchain role."' }] },
    { id: 9, title: 'HR Business Partner', company: 'NovaTech Industries', companyLogo: 'NT', category: 'HR', locationType: 'Hybrid', status: 'Open', applications: 11, posted: 'Mar 30, 2024', salary: '$85,000 – $110,000', description: 'Partner with business leaders to drive people strategy across our manufacturing operations. Lead talent acquisition, performance management, and employee engagement initiatives.', requirements: ['5+ years HR experience', 'HRBP experience', 'Manufacturing sector preferred', 'SHRM/PHR certification', 'Change management skills'] },
    { id: 10, title: 'Finance Analyst', company: 'ConsultPro Group', companyLogo: 'CP', category: 'Finance', locationType: 'On-site', status: 'Closed', applications: 38, posted: 'Jan 15, 2024', salary: '$75,000 – $95,000', description: 'Support financial planning and analysis for consulting engagements. Build financial models, prepare client deliverables, and provide data-driven recommendations.', requirements: ['2+ years FP&A', 'Advanced Excel / modeling', 'SQL knowledge', 'CFA Level 1+ preferred', 'Consulting experience a plus'] },
    { id: 11, title: 'Product Manager - AI', company: 'TechCorp Solutions', companyLogo: 'TC', category: 'Product', locationType: 'Hybrid', status: 'Open', applications: 35, posted: 'Apr 01, 2024', salary: '$140,000 – $180,000', description: 'Define product strategy for our AI-powered HR tools. Own the roadmap, work with ML engineers, and drive adoption across enterprise customers.', requirements: ['5+ years PM experience', 'AI / ML product background', 'Enterprise SaaS', 'Technical background preferred', 'Strong stakeholder management'] },
    { id: 12, title: 'Frontend Engineer', company: 'ShopWave Commerce', companyLogo: 'SW', category: 'Engineering', locationType: 'Remote', status: 'Open', applications: 41, posted: 'Apr 02, 2024', salary: '$100,000 – $140,000', description: 'Build performant, accessible UI components for our e-commerce platform. Collaborate with designers and backend engineers to deliver exceptional user experiences.', requirements: ['3+ years frontend', 'React + TypeScript', 'CSS/Tailwind expertise', 'Performance optimization', 'Accessibility (WCAG)'] },
    { id: 13, title: 'Customer Success Manager', company: 'EduSpark Global', companyLogo: 'ES', category: 'Sales', locationType: 'Remote', status: 'Flagged', applications: 6, posted: 'Apr 03, 2024', salary: '$60,000 – $80,000', description: 'Manage relationships with our enterprise education clients. Ensure successful onboarding, drive product adoption, and identify expansion opportunities.', requirements: ['3+ years CSM experience', 'EdTech / SaaS background', 'Excellent communication', 'Analytical mindset', 'CRM tools proficiency'], flagHistory: [{ reason: 'Duplicate listing', source: 'AI System', date: 'Apr 04, 2024', detail: 'AI detected this listing is substantially similar to job #4 (Marketing Manager - Growth) posted by the same company, with overlapping requirements and similar description patterns.' }] },
  ];

  /* ── Flagged count ── */
  get flaggedCount(): number { return this.jobs.filter(j => j.status === 'Flagged').length; }

  /* ── Filtered list ── */
  get filteredJobs(): JobOffer[] {
    return this.jobs.filter(j => {
      const matchSearch = !this.searchQuery ||
        j.title.toLowerCase().includes(this.searchQuery.toLowerCase()) ||
        j.company.toLowerCase().includes(this.searchQuery.toLowerCase());
      const matchStatus = this.statusFilter === 'All' || j.status === this.statusFilter;
      const matchCategory = this.categoryFilter === 'All' || j.category === this.categoryFilter;
      // Simplified date filter — real app would parse dates
      const matchDate = this.dateFilter === 'All';
      return matchSearch && matchStatus && matchCategory && (matchDate || true);
    });
  }

  /* ── Helpers ── */
  getStatusClass(status: string): string {
    return status === 'Open' ? 'sbadge--teal' : status === 'Closed' ? 'sbadge--gray' : 'sbadge--red';
  }
  getLocationClass(loc: string): string {
    return loc === 'Remote' ? 'lbadge--blue' : loc === 'On-site' ? 'lbadge--amber' : 'lbadge--purple';
  }
  logoGradient(name: string): string {
    const map: Record<string, string> = {
      TC: 'linear-gradient(135deg,#6366f1,#8b5cf6)',
      C9: 'linear-gradient(135deg,#2ee8a5,#0ea5e9)',
      SW: 'linear-gradient(135deg,#f59e0b,#ef4444)',
      ES: 'linear-gradient(135deg,#ec4899,#8b5cf6)',
      HB: 'linear-gradient(135deg,#14b8a6,#06b6d4)',
      CP: 'linear-gradient(135deg,#f97316,#f59e0b)',
      FH: 'linear-gradient(135deg,#3b82f6,#6366f1)',
      NT: 'linear-gradient(135deg,#64748b,#475569)',
    };
    return map[name] || 'linear-gradient(135deg,#6366f1,#8b5cf6)';
  }

  /* ── Checkbox logic ── */
  toggleAll(): void {
    if (this.allChecked) { this.selectedIds.clear(); this.allChecked = false; }
    else { this.paginatedJobs.forEach(j => this.selectedIds.add(j.id)); this.allChecked = true; }
  }
  toggleRow(id: number): void {
    this.selectedIds.has(id) ? this.selectedIds.delete(id) : this.selectedIds.add(id);
    this.allChecked = this.paginatedJobs.every(j => this.selectedIds.has(j.id));
  }
  isSelected(id: number): boolean { return this.selectedIds.has(id); }
  clearSelection(): void { this.selectedIds.clear(); this.allChecked = false; }

  /* ── Pagination ── */
  goPage(page: number): void { if (page >= 1 && page <= this.totalPages) { this.currentPage = page; this.allChecked = false; } }
  get pageNumbers(): number[] { const p: number[] = []; for (let i = 1; i <= this.totalPages; i++) p.push(i); return p; }

  /* ── Filter to flagged ── */
  showFlagged(): void { this.statusFilter = 'Flagged'; this.currentPage = 1; }

  /* ── Drawer ── */
  openDrawer(job: JobOffer): void { this.drawerJob = job; this.drawerOpen = true; this.openMenuId = null; }
  closeDrawer(): void { this.drawerOpen = false; this.drawerJob = null; }

  /* ── Actions ── */
  unflagJob(job: JobOffer): void { job.status = 'Open'; job.flagHistory = undefined; }
  removeJob(job: JobOffer): void {
    this.jobs = this.jobs.filter(j => j.id !== job.id);
    this.closeDrawer();
  }

  /* ── Row actions menu ── */
  toggleMenu(id: number, event: Event): void {
    event.stopPropagation();
    this.openMenuId = this.openMenuId === id ? null : id;
  }
}
