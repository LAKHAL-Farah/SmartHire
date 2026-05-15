import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LUCIDE_ICONS } from '../../../../shared/lucide-icons';

interface Recruiter {
  id: number;
  companyName: string;
  companyLogo: string;
  contactName: string;
  email: string;
  industry: string;
  plan: 'Starter' | 'Business' | 'Enterprise';
  verification: 'Verified' | 'Pending' | 'Rejected';
  activeJobs: number;
  joined: string;
  website: string;
  description: string;
  employees: string;
  rejectionReason?: string;
}

@Component({
  selector: 'app-recruiter-management',
  standalone: true,
  imports: [CommonModule, FormsModule, LUCIDE_ICONS],
  templateUrl: './recruiter-management.component.html',
  styleUrl: './recruiter-management.component.scss'
})
export class RecruiterManagementComponent {
  /* ── Filters ── */
  searchQuery = '';
  verificationFilter = 'All';
  industryFilter = 'All';
  planFilter = 'All';
  verifications = ['All', 'Verified', 'Pending', 'Rejected'];
  industries = ['All', 'Technology', 'Finance', 'Healthcare', 'Consulting', 'E-commerce', 'Education', 'Manufacturing'];
  plans = ['All', 'Starter', 'Business', 'Enterprise'];

  /* ── Pagination ── */
  currentPage = 1;
  pageSize = 15;
  get totalPages(): number { return Math.ceil(this.filteredRecruiters.length / this.pageSize); }
  get paginatedRecruiters(): Recruiter[] {
    const start = (this.currentPage - 1) * this.pageSize;
    return this.filteredRecruiters.slice(start, start + this.pageSize);
  }

  /* ── Selection / Bulk ── */
  selectedIds = new Set<number>();
  allChecked = false;

  /* ── Action dropdown ── */
  openMenuId: number | null = null;

  /* ── Verify modal ── */
  modalOpen = false;
  modalRecruiter: Recruiter | null = null;
  rejectionReason = '';

  /* ── Data ── */
  recruiters: Recruiter[] = [
    { id: 1, companyName: 'TechCorp Solutions', companyLogo: 'TC', contactName: 'Marcus Johnson', email: 'marcus@techcorp.io', industry: 'Technology', plan: 'Enterprise', verification: 'Verified', activeJobs: 12, joined: 'Oct 18, 2023', website: 'https://techcorp.io', description: 'Leading enterprise software company specializing in AI-driven HR solutions. 500+ employees across 3 continents.', employees: '500-1000' },
    { id: 2, companyName: 'FinanceHub Ltd', companyLogo: 'FH', contactName: 'Sarah Mitchell', email: 'sarah@financehub.com', industry: 'Finance', plan: 'Business', verification: 'Verified', activeJobs: 8, joined: 'Nov 05, 2023', website: 'https://financehub.com', description: 'Digital-first financial services provider offering investment banking and wealth management.', employees: '200-500' },
    { id: 3, companyName: 'MediCare Plus', companyLogo: 'MC', contactName: 'Dr. Aisha Patel', email: 'aisha@medicareplus.org', industry: 'Healthcare', plan: 'Starter', verification: 'Pending', activeJobs: 0, joined: 'Apr 08, 2024', website: 'https://medicareplus.org', description: 'Healthcare network providing telemedicine and digital health solutions across South Asia.', employees: '100-200' },
    { id: 4, companyName: 'NovaTech Industries', companyLogo: 'NT', contactName: 'James Wilson', email: 'j.wilson@novatech.com', industry: 'Manufacturing', plan: 'Business', verification: 'Pending', activeJobs: 0, joined: 'Apr 10, 2024', website: 'https://novatech.com', description: 'Advanced manufacturing company specializing in semiconductor production and cleanroom equipment.', employees: '1000-5000' },
    { id: 5, companyName: 'EduSpark Global', companyLogo: 'ES', contactName: 'Elena Rodriguez', email: 'elena@eduspark.edu', industry: 'Education', plan: 'Starter', verification: 'Pending', activeJobs: 0, joined: 'Apr 12, 2024', website: 'https://eduspark.edu', description: 'EdTech platform offering online certifications, bootcamps, and corporate training programs.', employees: '50-100' },
    { id: 6, companyName: 'CloudNine SaaS', companyLogo: 'C9', contactName: 'Ryan O\'Brien', email: 'ryan@cloudnine.io', industry: 'Technology', plan: 'Enterprise', verification: 'Verified', activeJobs: 23, joined: 'Sep 14, 2023', website: 'https://cloudnine.io', description: 'Cloud infrastructure and DevOps platform serving 10,000+ businesses worldwide.', employees: '200-500' },
    { id: 7, companyName: 'GreenLeaf Ventures', companyLogo: 'GL', contactName: 'Kenji Tanaka', email: 'kenji@greenleaf.vc', industry: 'Finance', plan: 'Starter', verification: 'Rejected', activeJobs: 0, joined: 'Mar 20, 2024', website: 'https://greenleaf.vc', description: 'Venture capital fund focused on sustainability and green tech investments.', employees: '10-50', rejectionReason: 'Unable to verify company registration. Business license expired.' },
    { id: 8, companyName: 'ShopWave Commerce', companyLogo: 'SW', contactName: 'Mei Lin Zhang', email: 'meilin@shopwave.cn', industry: 'E-commerce', plan: 'Business', verification: 'Verified', activeJobs: 15, joined: 'Nov 25, 2023', website: 'https://shopwave.cn', description: 'Asia-Pacific e-commerce platform with integrated logistics and payment solutions.', employees: '500-1000' },
    { id: 9, companyName: 'ConsultPro Group', companyLogo: 'CP', contactName: 'Thomas Laurent', email: 'thomas@consultpro.fr', industry: 'Consulting', plan: 'Enterprise', verification: 'Verified', activeJobs: 6, joined: 'Dec 01, 2023', website: 'https://consultpro.fr', description: 'Management consulting firm specializing in digital transformation and strategy.', employees: '100-200' },
    { id: 10, companyName: 'HealthBridge AI', companyLogo: 'HB', contactName: 'Fatima Al-Hassan', email: 'fatima@healthbridge.ai', industry: 'Healthcare', plan: 'Business', verification: 'Verified', activeJobs: 4, joined: 'Jan 15, 2024', website: 'https://healthbridge.ai', description: 'AI-powered diagnostic platform helping clinicians detect diseases earlier.', employees: '50-100' },
    { id: 11, companyName: 'DataStream Analytics', companyLogo: 'DA', contactName: 'Priya Sharma', email: 'priya@datastream.in', industry: 'Technology', plan: 'Starter', verification: 'Pending', activeJobs: 0, joined: 'Apr 14, 2024', website: 'https://datastream.in', description: 'Real-time data analytics and business intelligence platform for Indian enterprises.', employees: '50-100' },
    { id: 12, companyName: 'QuickServe Logistics', companyLogo: 'QS', contactName: 'Ahmed Bakr', email: 'ahmed@quickserve.eg', industry: 'E-commerce', plan: 'Starter', verification: 'Rejected', activeJobs: 0, joined: 'Feb 28, 2024', website: 'https://quickserve.eg', description: 'Last-mile delivery and logistics provider in North Africa.', employees: '100-200', rejectionReason: 'Company website is non-functional. Contact information could not be verified.' },
  ];

  /* ── Pending count ── */
  get pendingCount(): number {
    return this.recruiters.filter(r => r.verification === 'Pending').length;
  }

  /* ── Filtered list ── */
  get filteredRecruiters(): Recruiter[] {
    return this.recruiters.filter(r => {
      const matchSearch = !this.searchQuery ||
        r.companyName.toLowerCase().includes(this.searchQuery.toLowerCase()) ||
        r.contactName.toLowerCase().includes(this.searchQuery.toLowerCase()) ||
        r.email.toLowerCase().includes(this.searchQuery.toLowerCase());
      const matchVerification = this.verificationFilter === 'All' || r.verification === this.verificationFilter;
      const matchIndustry = this.industryFilter === 'All' || r.industry === this.industryFilter;
      const matchPlan = this.planFilter === 'All' || r.plan === this.planFilter;
      return matchSearch && matchVerification && matchIndustry && matchPlan;
    });
  }

  /* ── Helpers ── */
  getPlanClass(plan: string): string {
    return plan === 'Starter' ? 'pill--gray' : plan === 'Business' ? 'pill--amber' : 'pill--purple';
  }
  getVerificationClass(v: string): string {
    return v === 'Verified' ? 'vbadge--green' : v === 'Pending' ? 'vbadge--yellow' : 'vbadge--red';
  }
  logoGradient(index: number): string {
    const gradients = [
      'linear-gradient(135deg,#6366f1,#8b5cf6)',
      'linear-gradient(135deg,#2ee8a5,#0ea5e9)',
      'linear-gradient(135deg,#f59e0b,#ef4444)',
      'linear-gradient(135deg,#ec4899,#8b5cf6)',
      'linear-gradient(135deg,#14b8a6,#06b6d4)',
      'linear-gradient(135deg,#f97316,#f59e0b)',
    ];
    return gradients[index % gradients.length];
  }

  /* ── Checkbox logic ── */
  toggleAll(): void {
    if (this.allChecked) { this.selectedIds.clear(); this.allChecked = false; }
    else { this.paginatedRecruiters.forEach(r => this.selectedIds.add(r.id)); this.allChecked = true; }
  }
  toggleRow(id: number): void {
    this.selectedIds.has(id) ? this.selectedIds.delete(id) : this.selectedIds.add(id);
    this.allChecked = this.paginatedRecruiters.every(r => this.selectedIds.has(r.id));
  }
  isSelected(id: number): boolean { return this.selectedIds.has(id); }
  clearSelection(): void { this.selectedIds.clear(); this.allChecked = false; }

  /* ── Pagination ── */
  goPage(page: number): void { if (page >= 1 && page <= this.totalPages) { this.currentPage = page; this.allChecked = false; } }
  get pageNumbers(): number[] { const p: number[] = []; for (let i = 1; i <= this.totalPages; i++) p.push(i); return p; }

  /* ── Review Now — filter to pending only ── */
  reviewNow(): void { this.verificationFilter = 'Pending'; this.currentPage = 1; }

  /* ── Modal ── */
  openVerifyModal(recruiter: Recruiter, event: Event): void {
    event.stopPropagation();
    this.modalRecruiter = recruiter;
    this.rejectionReason = '';
    this.modalOpen = true;
    this.openMenuId = null;
  }
  closeModal(): void { this.modalOpen = false; this.modalRecruiter = null; this.rejectionReason = ''; }

  approveVerification(): void {
    if (this.modalRecruiter) { this.modalRecruiter.verification = 'Verified'; }
    this.closeModal();
  }
  rejectVerification(): void {
    if (this.modalRecruiter) {
      this.modalRecruiter.verification = 'Rejected';
      this.modalRecruiter.rejectionReason = this.rejectionReason;
    }
    this.closeModal();
  }

  /* ── Row actions menu ── */
  toggleMenu(id: number, event: Event): void {
    event.stopPropagation();
    this.openMenuId = this.openMenuId === id ? null : id;
  }
}
