import { Component, Input, Output, EventEmitter, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RoadmapApiService } from '../../../../services/roadmap-api.service';

interface CareerPathCard {
  id: string;
  emoji: string;
  title: string;
  tech: string;
  badge: string;
}

@Component({
  selector: 'app-step-career-goal',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './step-career-goal.component.html',
  styleUrl: './step-career-goal.component.scss'
})
export class StepCareerGoalComponent implements OnInit {
  @Input() selected: string | null = null;
  @Output() selectionChange = new EventEmitter<string>();

  private readonly roadmapApi = inject(RoadmapApiService);

  careerPaths: CareerPathCard[] = [];

  ngOnInit(): void {
    this.roadmapApi.getPublishedCareerPaths().subscribe({
      next: (items) => {
        this.careerPaths = (items || []).map((item) => {
          const topics = (item.defaultTopics || '')
            .split(',')
            .map((token) => token.trim())
            .filter((token) => token.length > 0)
            .slice(0, 4)
            .join(', ');

          const difficulty = (item.difficulty || 'Unknown').toUpperCase();
          return {
            id: String(item.id),
            emoji: this.pickEmoji(item.id),
            title: item.title,
            tech: topics || 'No topics configured yet',
            badge: difficulty,
          };
        });
      },
      error: () => {
        this.careerPaths = [];
      },
    });
  }

  select(id: string): void {
    this.selectionChange.emit(id);
  }

  private pickEmoji(id: number): string {
    const emojis = ['💻', '🔧', '📊', '🎨', '🛡️', '📱', '☁️', '🤖', '🧪', '🌐', '🚀', '⚙️'];
    return emojis[id % emojis.length] || emojis[0];
  }
}
