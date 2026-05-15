import { Component, computed, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { LUCIDE_ICONS } from '../../../../shared/lucide-icons';

function careerTitle(path: string | null): string {
  if (!path) return 'Software engineering';
  const m: Record<string, string> = {
    frontend: 'Frontend Engineer',
    backend: 'Backend Engineer',
    fullstack: 'Full-Stack Engineer',
    devops: 'DevOps Engineer',
    data: 'Data Engineer',
    mobile: 'Mobile Engineer',
  };
  return m[path] || 'Software Engineer';
}

@Component({
  selector: 'app-step-results',
  standalone: true,
  imports: [CommonModule, RouterLink, LUCIDE_ICONS],
  templateUrl: './step-results.component.html',
  styleUrl: './step-results.component.scss',
})
export class StepResultsComponent {
  skillScores = input<Record<string, number>>({});
  careerPath = input<string | null>(null);
  situation = input<string | null>(null);

  axisIndices = [0, 1, 2, 3, 4, 5];

  skills = computed(() => {
    const s = this.skillScores();
    const entries = Object.entries(s);
    if (entries.length === 0) {
      return [
        { name: 'Frontend', score: 60 },
        { name: 'Backend', score: 55 },
        { name: 'DevOps', score: 40 },
        { name: 'Algorithms', score: 50 },
        { name: 'Databases', score: 48 },
        { name: 'Soft Skills', score: 70 },
      ];
    }
    return entries.map(([name, score]) => ({ name, score }));
  });

  readinessPct = computed(() => {
    const list = this.skills();
    if (!list.length) return 0;
    return Math.round(list.reduce((a, s) => a + s.score, 0) / list.length);
  });

  matchTitle = computed(() => careerTitle(this.careerPath()));

  nextSteps = [
    { num: '1', text: 'Review your profile & linked accounts', link: '/dashboard/profile' },
    { num: '2', text: 'Explore your dashboard and roadmap', link: '/dashboard' },
    { num: '3', text: 'Explore your roadmap & CV tools', link: '/dashboard/roadmap' },
  ];

  dataPoints = computed(() => {
    const list = this.skills();
    return list
      .map((s, i) => {
        const p = this.getAxisPoint(i, s.score);
        return `${p.x},${p.y}`;
      })
      .join(' ');
  });

  getHexPoints(cx: number, cy: number, r: number): string {
    return Array.from({ length: 6 }, (_, i) => {
      const angle = (Math.PI / 3) * i - Math.PI / 2;
      return `${cx + r * Math.cos(angle)},${cy + r * Math.sin(angle)}`;
    }).join(' ');
  }

  getAxisPoint(index: number, radius: number): { x: number; y: number } {
    const angle = (Math.PI / 3) * index - Math.PI / 2;
    return {
      x: 150 + radius * Math.cos(angle),
      y: 125 + radius * Math.sin(angle),
    };
  }
}
