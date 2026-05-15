import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface AiChatRequest {
  message: string;
}

export interface AiChatResponse {
  response: string;
}

@Injectable({
  providedIn: 'root'
})
export class AiChatService {
  private readonly baseUrl = 'http://localhost:8085/api/ai';

  constructor(private readonly http: HttpClient) {}

  sendMessage(message: string): Observable<AiChatResponse> {
    const payload: AiChatRequest = { message };
    return this.http.post<AiChatResponse>(`${this.baseUrl}/chat`, payload);
  }

  matchResume(resume: File): Observable<AiChatResponse> {
    const form = new FormData();
    form.append('resume', resume);

    return this.http.post<AiChatResponse>(`${this.baseUrl}/resume-match`, form);
  }
}
