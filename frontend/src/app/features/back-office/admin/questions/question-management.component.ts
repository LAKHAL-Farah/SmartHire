import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LUCIDE_ICONS } from '../../../../shared/lucide-icons';

interface InterviewQuestion {
  id: number;
  text: string;
  category: 'Technical' | 'Behavioral' | 'System Design';
  difficulty: 'Beginner' | 'Intermediate' | 'Advanced' | 'Expert';
  careerPath: string;
  tags: string[];
  sampleAnswer: string;
  status: 'Active' | 'Archived';
  usageCount: number;
  createdAt: string;
}

@Component({
  selector: 'app-question-management',
  standalone: true,
  imports: [CommonModule, FormsModule, LUCIDE_ICONS],
  templateUrl: './question-management.component.html',
  styleUrl: './question-management.component.scss'
})
export class QuestionManagementComponent {

  /* ── Filters ── */
  searchQuery = '';
  categoryFilter = 'All';
  difficultyFilter = 'All';
  careerPathFilter = 'All';

  categories = ['All', 'Technical', 'Behavioral', 'System Design'];
  difficulties = ['All', 'Beginner', 'Intermediate', 'Advanced', 'Expert'];
  careerPaths = ['All', 'Frontend Developer', 'Backend Developer', 'Full Stack Developer', 'Data Scientist', 'DevOps Engineer', 'Mobile Developer', 'AI/ML Engineer', 'Cloud Architect', 'Cybersecurity Analyst', 'Product Manager'];

  allTags = ['JavaScript', 'TypeScript', 'React', 'Angular', 'Node.js', 'Python', 'SQL', 'AWS', 'Docker', 'Kubernetes', 'REST API', 'GraphQL', 'System Design', 'OOP', 'Data Structures', 'Algorithms', 'CI/CD', 'Testing', 'Agile', 'Leadership', 'Communication', 'Problem Solving', 'Microservices', 'Machine Learning', 'TensorFlow', 'Security'];

  /* ── Pagination ── */
  currentPage = 1;
  pageSize = 12;

  get totalPages(): number { return Math.ceil(this.filteredQuestions.length / this.pageSize); }
  get paginatedQuestions(): InterviewQuestion[] {
    const start = (this.currentPage - 1) * this.pageSize;
    return this.filteredQuestions.slice(start, start + this.pageSize);
  }

  /* ── Preview Modal ── */
  previewOpen = false;
  previewQuestion: InterviewQuestion | null = null;

  /* ── Form Panel (right column) ── */
  editingId: number | null = null;
  formText = '';
  formCategory: 'Technical' | 'Behavioral' | 'System Design' = 'Technical';
  formDifficulty: 'Beginner' | 'Intermediate' | 'Advanced' | 'Expert' = 'Beginner';
  formCareerPaths: string[] = [];
  formTags: string[] = [];
  formSampleAnswer = '';
  tagInput = '';
  careerPathDropdownOpen = false;
  tagDropdownOpen = false;
  aiGenerating = false;

  /* ── Action dropdown ── */
  openMenuId: number | null = null;

  /* ── Questions Data ── */
  questions: InterviewQuestion[] = [
    { id: 1, text: 'Explain the difference between var, let, and const in JavaScript. When would you use each one?', category: 'Technical', difficulty: 'Beginner', careerPath: 'Frontend Developer', tags: ['JavaScript', 'TypeScript'], sampleAnswer: 'var is function-scoped and hoisted, let is block-scoped and not hoisted, const is block-scoped and cannot be reassigned. Use const by default, let when you need to reassign, and avoid var in modern code.', status: 'Active', usageCount: 342, createdAt: 'Jan 12, 2024' },
    { id: 2, text: 'You notice a colleague consistently takes credit for your work in team meetings. How do you handle this situation while maintaining a positive working relationship?', category: 'Behavioral', difficulty: 'Intermediate', careerPath: 'Full Stack Developer', tags: ['Communication', 'Leadership'], sampleAnswer: 'I would first document my contributions clearly, then address the issue privately with the colleague. If the behavior continues, I would discuss it with my manager while focusing on the impact to the team dynamic rather than making personal accusations.', status: 'Active', usageCount: 215, createdAt: 'Jan 18, 2024' },
    { id: 3, text: 'Design a URL shortening service like bit.ly. Consider the hashing strategy, database schema, and how you would handle high traffic reads.', category: 'System Design', difficulty: 'Advanced', careerPath: 'Backend Developer', tags: ['System Design', 'Algorithms', 'SQL'], sampleAnswer: 'Use a base62 encoding scheme to generate short URLs. Store mappings in a relational database with the short code as a primary key. Use Redis as a caching layer for frequently accessed URLs. For high traffic, implement horizontal scaling with load balancers and database read replicas.', status: 'Active', usageCount: 178, createdAt: 'Jan 25, 2024' },
    { id: 4, text: 'What is the event loop in Node.js? Explain the phases and how asynchronous operations are handled.', category: 'Technical', difficulty: 'Intermediate', careerPath: 'Backend Developer', tags: ['Node.js', 'JavaScript'], sampleAnswer: 'The event loop is a mechanism that allows Node.js to perform non-blocking I/O operations. It has phases: timers, pending callbacks, idle/prepare, poll, check, close callbacks. Async operations are delegated to the system kernel or thread pool, and their callbacks are queued for execution.', status: 'Active', usageCount: 289, createdAt: 'Feb 03, 2024' },
    { id: 5, text: 'Design a real-time chat application that supports one-on-one messaging, group chats, read receipts, and message history. Explain your architecture decisions for handling millions of concurrent connections.', category: 'System Design', difficulty: 'Expert', careerPath: 'Full Stack Developer', tags: ['System Design', 'Microservices', 'REST API'], sampleAnswer: 'Use WebSockets for real-time communication with a pub/sub system like Redis for message distribution. Implement a microservices architecture with separate services for message storage, user presence, and notifications. Use Cassandra for message history and Redis for active session management.', status: 'Active', usageCount: 95, createdAt: 'Feb 10, 2024' },
    { id: 6, text: 'Explain the SOLID principles in object-oriented programming. Give a practical example of each principle in a real project.', category: 'Technical', difficulty: 'Intermediate', careerPath: 'Full Stack Developer', tags: ['OOP', 'TypeScript'], sampleAnswer: 'S - Single Responsibility: One class per concern. O - Open/Closed: Extend via interfaces. L - Liskov Substitution: Subclasses must be interchangeable. I - Interface Segregation: Many small interfaces. D - Dependency Inversion: Depend on abstractions not concretions.', status: 'Active', usageCount: 312, createdAt: 'Feb 14, 2024' },
    { id: 7, text: 'Tell me about a time when you had to meet an extremely tight deadline. What did you do to ensure the project was delivered on time?', category: 'Behavioral', difficulty: 'Beginner', careerPath: 'Frontend Developer', tags: ['Problem Solving', 'Agile'], sampleAnswer: 'I would describe prioritizing tasks using MoSCoW method, communicating clearly with stakeholders about scope trade-offs, breaking work into smaller deliverables, and ensuring daily standups to track progress and remove blockers.', status: 'Active', usageCount: 401, createdAt: 'Feb 20, 2024' },
    { id: 8, text: 'What are the differences between SQL and NoSQL databases? When would you choose one over the other? Explain with real-world use cases.', category: 'Technical', difficulty: 'Beginner', careerPath: 'Backend Developer', tags: ['SQL', 'Data Structures'], sampleAnswer: 'SQL databases are relational, ACID-compliant, and best for structured data with complex queries. NoSQL databases (document, key-value, graph, columnar) offer flexibility and horizontal scaling. Choose SQL for financial transactions, NoSQL for content management or real-time analytics.', status: 'Active', usageCount: 267, createdAt: 'Feb 28, 2024' },
    { id: 9, text: 'Describe your approach to implementing a CI/CD pipeline for a microservices architecture. What tools would you use and why?', category: 'Technical', difficulty: 'Advanced', careerPath: 'DevOps Engineer', tags: ['CI/CD', 'Docker', 'Kubernetes'], sampleAnswer: 'I would use GitHub Actions or GitLab CI for pipeline orchestration, Docker for containerization, Kubernetes for orchestration, Helm for deployment templating, SonarQube for code quality, and ArgoCD for GitOps-based deployments. Each microservice would have its own pipeline with build, test, scan, and deploy stages.', status: 'Active', usageCount: 156, createdAt: 'Mar 05, 2024' },
    { id: 10, text: 'Explain the concept of transfer learning in deep learning. How would you fine-tune a pre-trained model for a specific domain task?', category: 'Technical', difficulty: 'Advanced', careerPath: 'AI/ML Engineer', tags: ['Machine Learning', 'TensorFlow'], sampleAnswer: 'Transfer learning leverages knowledge from a pre-trained model on a large dataset to a new but related task. Fine-tuning involves freezing early layers, replacing the final classification layer, and training on domain-specific data with a lower learning rate to preserve learned features.', status: 'Active', usageCount: 89, createdAt: 'Mar 12, 2024' },
    { id: 11, text: 'Design a notification system that can handle push notifications, emails, SMS, and in-app notifications at scale with delivery guarantees.', category: 'System Design', difficulty: 'Expert', careerPath: 'Cloud Architect', tags: ['System Design', 'Microservices', 'AWS'], sampleAnswer: 'Use a message queue (SQS/Kafka) for decoupling. Implement a notification service with channel-specific workers. Use DynamoDB for user preferences, SNS for push, SES for email, Twilio for SMS. Add dead letter queues for failed deliveries and implement exponential backoff retry logic.', status: 'Active', usageCount: 72, createdAt: 'Mar 18, 2024' },
    { id: 12, text: 'Describe a situation where you had to work with someone who had a very different working style than yours. How did you collaborate effectively?', category: 'Behavioral', difficulty: 'Intermediate', careerPath: 'Product Manager', tags: ['Communication', 'Leadership', 'Agile'], sampleAnswer: 'I would describe actively listening to understand their perspective, finding common ground on goals, establishing clear communication protocols, and leveraging each person\'s strengths to deliver better outcomes than either could achieve alone.', status: 'Active', usageCount: 198, createdAt: 'Mar 22, 2024' },
    { id: 13, text: 'What is the virtual DOM and how does React use it for efficient rendering? How does this compare to Angular\'s change detection strategy?', category: 'Technical', difficulty: 'Intermediate', careerPath: 'Frontend Developer', tags: ['React', 'Angular', 'JavaScript'], sampleAnswer: 'React\'s virtual DOM is an in-memory representation of the real DOM. React diffs the virtual DOM against the previous version, computing the minimal set of changes needed. Angular uses Zone.js to detect changes and a hierarchical change detection tree. React\'s approach is declarative while Angular\'s is more framework-managed.', status: 'Active', usageCount: 276, createdAt: 'Mar 28, 2024' },
    { id: 14, text: 'Implement a rate limiter for an API. Discuss the different algorithms (token bucket, sliding window, fixed window) and their trade-offs in terms of fairness and memory.', category: 'Technical', difficulty: 'Expert', careerPath: 'Backend Developer', tags: ['Algorithms', 'REST API', 'System Design'], sampleAnswer: 'Token bucket allows burst traffic with refill rate control. Fixed window is simple but has boundary spikes. Sliding window log is accurate but memory-intensive. Sliding window counter balances accuracy and memory. For distributed systems, use Redis with atomic operations for shared state.', status: 'Archived', usageCount: 134, createdAt: 'Apr 02, 2024' },
    { id: 15, text: 'How would you implement a zero-trust security model for a cloud-native application? What components and tools would you use?', category: 'Technical', difficulty: 'Expert', careerPath: 'Cybersecurity Analyst', tags: ['Security', 'AWS', 'Kubernetes'], sampleAnswer: 'Implement identity verification at every access point using mutual TLS and OAuth2/OIDC. Use service mesh (Istio) for mTLS between services. Employ network micro-segmentation, just-in-time access, continuous verification, and encrypt data at rest and in transit. Use SIEM for monitoring.', status: 'Active', usageCount: 67, createdAt: 'Apr 08, 2024' },
    { id: 16, text: 'Explain the key considerations when designing a mobile app for offline-first functionality. How do you handle data synchronization and conflict resolution?', category: 'Technical', difficulty: 'Advanced', careerPath: 'Mobile Developer', tags: ['Data Structures', 'REST API'], sampleAnswer: 'Use local storage (SQLite/Realm) as the source of truth. Implement a sync queue for pending changes. Use CRDTs or last-write-wins for conflict resolution. Queue network requests and replay when online. Provide clear UI indicators for sync status and handle merge conflicts gracefully.', status: 'Active', usageCount: 143, createdAt: 'Apr 14, 2024' },
  ];

  /* ── Filtered list ── */
  get filteredQuestions(): InterviewQuestion[] {
    return this.questions.filter(q => {
      if (q.status === 'Archived') return false;
      const matchSearch = !this.searchQuery ||
        q.text.toLowerCase().includes(this.searchQuery.toLowerCase()) ||
        q.tags.some(t => t.toLowerCase().includes(this.searchQuery.toLowerCase()));
      const matchCategory = this.categoryFilter === 'All' || q.category === this.categoryFilter;
      const matchDifficulty = this.difficultyFilter === 'All' || q.difficulty === this.difficultyFilter;
      const matchCareerPath = this.careerPathFilter === 'All' || q.careerPath === this.careerPathFilter;
      return matchSearch && matchCategory && matchDifficulty && matchCareerPath;
    });
  }

  /* ── Stats ── */
  get totalActive(): number { return this.questions.filter(q => q.status === 'Active').length; }
  get totalArchived(): number { return this.questions.filter(q => q.status === 'Archived').length; }
  get categoryBreakdown(): { label: string; count: number }[] {
    return ['Technical', 'Behavioral', 'System Design'].map(c => ({
      label: c,
      count: this.questions.filter(q => q.category === c && q.status === 'Active').length
    }));
  }

  /* ── Helpers ── */
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

  /* ── Pagination ── */
  goPage(page: number): void {
    if (page >= 1 && page <= this.totalPages) { this.currentPage = page; }
  }
  get pageNumbers(): number[] {
    const pages: number[] = [];
    for (let i = 1; i <= this.totalPages; i++) pages.push(i);
    return pages;
  }

  /* ── Preview ── */
  openPreview(q: InterviewQuestion): void {
    this.previewQuestion = q;
    this.previewOpen = true;
    this.openMenuId = null;
  }
  closePreview(): void { this.previewOpen = false; this.previewQuestion = null; }

  /* ── Edit (loads into form) ── */
  editQuestion(q: InterviewQuestion): void {
    this.editingId = q.id;
    this.formText = q.text;
    this.formCategory = q.category;
    this.formDifficulty = q.difficulty;
    this.formCareerPaths = [q.careerPath];
    this.formTags = [...q.tags];
    this.formSampleAnswer = q.sampleAnswer;
    this.openMenuId = null;
  }

  /* ── Archive ── */
  archiveQuestion(q: InterviewQuestion): void {
    q.status = 'Archived';
    this.openMenuId = null;
  }

  /* ── Save (add or update) ── */
  saveForm(): void {
    if (!this.formText.trim()) return;
    if (this.editingId) {
      const q = this.questions.find(x => x.id === this.editingId);
      if (q) {
        q.text = this.formText;
        q.category = this.formCategory;
        q.difficulty = this.formDifficulty;
        q.careerPath = this.formCareerPaths[0] || 'Frontend Developer';
        q.tags = [...this.formTags];
        q.sampleAnswer = this.formSampleAnswer;
      }
    } else {
      const newId = Math.max(...this.questions.map(q => q.id)) + 1;
      this.questions.push({
        id: newId,
        text: this.formText,
        category: this.formCategory,
        difficulty: this.formDifficulty,
        careerPath: this.formCareerPaths[0] || 'Frontend Developer',
        tags: [...this.formTags],
        sampleAnswer: this.formSampleAnswer,
        status: 'Active',
        usageCount: 0,
        createdAt: 'Apr 20, 2024'
      });
    }
    this.resetForm();
  }

  /* ── Cancel / Reset ── */
  resetForm(): void {
    this.editingId = null;
    this.formText = '';
    this.formCategory = 'Technical';
    this.formDifficulty = 'Beginner';
    this.formCareerPaths = [];
    this.formTags = [];
    this.formSampleAnswer = '';
    this.tagInput = '';
    this.careerPathDropdownOpen = false;
    this.tagDropdownOpen = false;
  }

  /* ── Career Path multi-select ── */
  toggleCareerPathDropdown(): void { this.careerPathDropdownOpen = !this.careerPathDropdownOpen; this.tagDropdownOpen = false; }
  toggleCareerPath(cp: string): void {
    const idx = this.formCareerPaths.indexOf(cp);
    idx > -1 ? this.formCareerPaths.splice(idx, 1) : this.formCareerPaths.push(cp);
  }
  isCareerPathSelected(cp: string): boolean { return this.formCareerPaths.includes(cp); }

  /* ── Tag multi-select ── */
  toggleTagDropdown(): void { this.tagDropdownOpen = !this.tagDropdownOpen; this.careerPathDropdownOpen = false; }
  toggleTag(tag: string): void {
    const idx = this.formTags.indexOf(tag);
    idx > -1 ? this.formTags.splice(idx, 1) : this.formTags.push(tag);
  }
  isTagSelected(tag: string): boolean { return this.formTags.includes(tag); }
  removeTag(tag: string): void {
    const idx = this.formTags.indexOf(tag);
    if (idx > -1) this.formTags.splice(idx, 1);
  }
  removeCareerPath(cp: string): void {
    const idx = this.formCareerPaths.indexOf(cp);
    if (idx > -1) this.formCareerPaths.splice(idx, 1);
  }

  /* ── AI Generate Sample Answer ── */
  generateSampleAnswer(): void {
    if (!this.formText.trim()) return;
    this.aiGenerating = true;
    setTimeout(() => {
      this.formSampleAnswer = `AI-generated answer for: "${this.formText.substring(0, 60)}..."\n\nThis is a comprehensive sample answer that covers the key concepts expected in a ${this.formDifficulty.toLowerCase()}-level ${this.formCategory.toLowerCase()} question. The answer demonstrates structured thinking, relevant technical knowledge, and clear communication.`;
      this.aiGenerating = false;
    }, 2200);
  }

  /* ── Row actions menu ── */
  toggleMenu(id: number, event: Event): void {
    event.stopPropagation();
    this.openMenuId = this.openMenuId === id ? null : id;
  }
}
