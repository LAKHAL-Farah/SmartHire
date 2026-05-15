import { Component, Input, Output, EventEmitter, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';

interface Question {
  text: string;
  options: { letter: string; text: string }[];
  difficulty: number;          // 1-3
  difficultyLabel: string;
}

@Component({
  selector: 'app-step-skill-check',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './step-skill-check.component.html',
  styleUrl: './step-skill-check.component.scss'
})
export class StepSkillCheckComponent {
  @Input() currentQuestion = signal(0);
  @Input() selectedAnswer = signal<string | null>(null);
  @Output() answerSelected = new EventEmitter<string>();

  questions: Question[] = [
    {
      text: 'You need to center one element vertically and horizontally inside a container. Which CSS approach do you reach for first?',
      options: [
        { letter: 'A', text: 'margin: auto with position absolute' },
        { letter: 'B', text: 'display: flex with align-items and justify-content' },
        { letter: 'C', text: 'display: grid with place-items: center' },
        { letter: 'D', text: 'I usually trial-and-error until it works' },
      ],
      difficulty: 1,
      difficultyLabel: 'Beginner'
    },
    {
      text: 'Your REST API responds in 4 seconds. Where do you start investigating?',
      options: [
        { letter: 'A', text: 'Check the database query execution plan' },
        { letter: 'B', text: 'Add console.log timestamps in the route handler' },
        { letter: 'C', text: 'Profile the network waterfall in DevTools' },
        { letter: 'D', text: 'Increase the server timeout limit' },
      ],
      difficulty: 2,
      difficultyLabel: 'Intermediate'
    },
    {
      text: 'What is the time complexity of searching for an element in a balanced binary search tree?',
      options: [
        { letter: 'A', text: 'O(1)' },
        { letter: 'B', text: 'O(log n)' },
        { letter: 'C', text: 'O(n)' },
        { letter: 'D', text: 'O(n log n)' },
      ],
      difficulty: 2,
      difficultyLabel: 'Intermediate'
    },
    {
      text: 'A teammate pushes directly to main and it breaks CI. What is the best process improvement?',
      options: [
        { letter: 'A', text: 'Add branch protection rules requiring PR reviews' },
        { letter: 'B', text: 'Send the whole team an email about proper workflow' },
        { letter: 'C', text: 'Revert the commit and move on' },
        { letter: 'D', text: 'Set up a pre-push hook on their machine' },
      ],
      difficulty: 1,
      difficultyLabel: 'Beginner'
    },
    {
      text: 'You are designing a schema for a social feed. Posts have millions of likes. How do you store the like count efficiently?',
      options: [
        { letter: 'A', text: 'COUNT(*) query on the likes table each request' },
        { letter: 'B', text: 'Denormalized counter column with atomic increments' },
        { letter: 'C', text: 'Cache the count in Redis with no persistence' },
        { letter: 'D', text: 'Store likes as a JSON array on the post document' },
      ],
      difficulty: 3,
      difficultyLabel: 'Advanced'
    },
  ];

  selectAnswer(letter: string): void {
    this.answerSelected.emit(letter);
  }
}
