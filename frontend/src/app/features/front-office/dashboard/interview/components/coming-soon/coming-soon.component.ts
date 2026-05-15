import { CommonModule } from '@angular/common';
import { Component, Input, OnInit } from '@angular/core';

@Component({
  selector: 'app-coming-soon',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './coming-soon.component.html',
  styleUrl: './coming-soon.component.scss'
})
export class ComingSoonComponent implements OnInit {
  @Input() role = '';
  @Input() feature = '';

  progress = 0;

  ngOnInit(): void {
    setTimeout(() => {
      this.progress = 65;
    }, 80);
  }

  get isCloud(): boolean {
    return this.role.toLowerCase().includes('cloud');
  }

  get roleBadgeClass(): string {
    return this.isCloud ? 'role-badge--cloud' : 'role-badge--ai';
  }

  get progressClass(): string {
    return this.isCloud ? 'progress-fill--cloud' : 'progress-fill--ai';
  }

  get icon(): string {
    return this.isCloud ? '☁️' : '🤖';
  }

  get iconClass(): string {
    return this.isCloud ? 'hero-icon--cloud' : 'hero-icon--ai';
  }

  get previewItems(): string[] {
    if (this.isCloud) {
      return [
        '🔧 Drag-and-drop infrastructure builder',
        '📊 System design evaluation with NVIDIA AI',
        '💬 Probing follow-ups on scalability and fault tolerance',
        '💰 Cost optimization scenario analysis',
      ];
    }

    return [
      '🧠 ML pipeline scenario designer',
      '📈 Concept extraction from your verbal answers',
      '🎯 Targeted follow-ups on model and metric choices',
      '🚀 MLOps and deployment evaluation',
    ];
  }
}
