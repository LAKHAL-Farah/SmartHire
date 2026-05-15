import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-auth-left-panel',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './auth-left-panel.component.html',
  styleUrl: './auth-left-panel.component.scss'
})
export class AuthLeftPanelComponent {
  activeQuote = 0;

  quotes = [
    {
      text: 'SmartHire mapped out exactly what I needed to learn and prepared me for interviews. I landed my first dev role in 8 weeks.',
      name: 'Sarah Chen',
      initials: 'SC',
      role: 'Junior Frontend Developer at Vercel'
    },
    {
      text: 'The AI mock interviews were a game changer. I went from failing every technical round to getting 3 offers in a month.',
      name: 'Marcus Rivera',
      initials: 'MR',
      role: 'Software Engineer at Stripe'
    },
    {
      text: 'I was stuck in tutorial hell for two years. SmartHire gave me a clear roadmap and accountability. Best investment ever.',
      name: 'Priya Nair',
      initials: 'PN',
      role: 'Full Stack Developer at Shopify'
    }
  ];
}
