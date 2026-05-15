import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface RoadmapResponse {
  id: number;
  title: string;
  difficulty?: string;
  estimatedWeeks?: number;
  status?: string;
  totalSteps?: number;
  completedSteps?: number;
  streakDays?: number;
  longestStreak?: number;
  createdAt?: string;
  steps: StepResponse[];
  careerPath?: {
    id: number;
    title: string;
    targetRoles?: string;
    averageSalary?: number;
  };
}

export interface StepResponse {
  id: number;
  stepOrder: number;
  title: string;
  objective?: string;
  estimatedDays?: number;
  status?: string;
}

export interface RoadmapNodeDto {
  id: number;
  nodeId: string;
  title: string;
  description: string;
  objective: string;
  type: 'REQUIRED' | 'OPTIONAL' | 'ALTERNATIVE';
  difficulty: 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED';
  status: 'LOCKED' | 'AVAILABLE' | 'IN_PROGRESS' | 'COMPLETED' | 'SKIPPED';
  stepOrder: number;
  estimatedDays: number;
  actualDays?: number;
  technologies?: string;
  positionX: number;
  positionY: number;
  unlockedAt?: string;
  completedAt?: string;
}

export interface RoadmapEdgeDto {
  id: number;
  fromNodeId: string;
  toNodeId: string;
  type: 'REQUIRED' | 'RECOMMENDED' | 'OPTIONAL';
}

export interface RoadmapVisualResponse {
  roadmapId: number;
  title: string;
  description: string;
  status: string;
  totalNodes: number;
  completedNodes: number;
  progressPercent: number;
  streakDays: number;
  longestStreak: number;
  nodes: RoadmapNodeDto[];
  edges: RoadmapEdgeDto[];
}

export interface NodeQuizQuestionDto {
  id: string;
  prompt: string;
  options: string[];
  correctIndex: number;
}

export interface NodeQuizResponseDto {
  nodeId: number;
  nodeTitle: string;
  passThreshold: number;
  aiGenerated: boolean;
  questions: NodeQuizQuestionDto[];
}

export interface NodeProjectLabDto {
  historyId?: number;
  nodeId: number;
  nodeTitle: string;
  projectTitle: string;
  scenario: string;
  language: string;
  estimatedHours: number;
  passThreshold: number;
  aiGenerated: boolean;
  userStories: string[];
  acceptanceCriteria: string[];
  stretchGoals: string[];
  starterCode: string;
  generatedAt?: string;
}

export interface NodeTutorPromptRequestDto {
  prompt: string;
}

export interface NodeTutorPromptResponseDto {
  nodeId: number;
  nodeTitle: string;
  prompt: string;
  answer: string;
  keyTakeaways: string[];
  nextActions: string[];
  aiGenerated: boolean;
  respondedAt?: string;
}

export interface NodeCourseLessonDto {
  sectionTitle: string;
  explanation: string;
  miniExample: string;
  codeSnippet: string;
  commonPitfalls: string[];
  practiceTasks: string[];
}

export interface NodeCourseCheckpointDto {
  question: string;
  answerHint: string;
}

export interface NodeCourseContentDto {
  historyId?: number;
  nodeId: number;
  nodeTitle: string;
  courseTitle: string;
  intro: string;
  difficulty: string;
  technologies?: string;
  aiGenerated: boolean;
  generatedAt?: string;
  lessons: NodeCourseLessonDto[];
  checkpoints: NodeCourseCheckpointDto[];
  cheatSheet: string[];
  nextNodeFocus: string;
}

export interface NodeProjectValidationRequestDto {
  projectTitle?: string;
  language?: string;
  code: string;
  acceptanceCriteria?: string[];
}

export interface NodeProjectValidationResponseDto {
  nodeId: number;
  projectTitle: string;
  passThreshold: number;
  scorePercent: number;
  passed: boolean;
  aiGenerated: boolean;
  summary: string;
  strengths: string[];
  improvements: string[];
  nextSteps: string[];
}

export interface ProjectSuggestionDto {
  id: number;
  stepId: number;
  title: string;
  description: string;
  difficulty: 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED';
  githubTopics: string[];
  techStack: string[];
  estimatedDays: number;
  alignedCareerPath?: string;
  createdAt?: string;
}

export interface ProjectSubmissionDto {
  id: number;
  userId: number;
  projectSuggestionId: number;
  repoUrl: string;
  status: string;
  score?: number;
  aiFeedback?: string;
  readmeScore?: number;
  structureScore?: number;
  testScore?: number;
  ciScore?: number;
  recommendations?: string[];
  retryCount: number;
  submittedAt?: string;
  reviewedAt?: string;
}

export interface ProjectSubmitRequestDto {
  userId: number;
  projectSuggestionId: number;
  repoUrl: string;
}

export interface RetrySubmissionRequestDto {
  repoUrl: string;
}

export interface ProjectReviewResponseDto {
  review: string;
  status?: string;
  score?: number;
  readmeScore?: number;
  structureScore?: number;
  testScore?: number;
  ciScore?: number;
  recommendations?: string[];
  reviewedAt?: string;
}

export interface ProgressSummaryDto {
  roadmapId: number;
  totalSteps: number;
  completedSteps: number;
  progressPercent: number;
  streakDays: number;
  currentStep?: StepResponse;
}

export interface MilestoneDto {
  id: number;
  roadmapId: number;
  title: string;
  description: string;
  stepThreshold: number;
  reachedAt?: string;
  certificateIssued: boolean;
}

export interface NotificationDto {
  id: number;
  userId: number;
  roadmapId: number;
  type: string;
  message: string;
  isRead: boolean;
  createdAt: string;
}

export interface StepResourceDto {
  id?: number;
  stepId?: number;
  type: string;
  provider: string;
  title: string;
  url: string;
  rating?: number;
  durationHours?: number;
  price?: number;
  isFree?: boolean;
  externalId?: string;
}

export interface CreateStepResourceRequest {
  type: string;
  provider: string;
  title: string;
  url: string;
  rating?: number;
  durationHours?: number;
  price?: number;
  isFree?: boolean;
  externalId?: string;
}

export interface PaceSnapshotDto {
  id: number;
  roadmapId: number;
  snapshotDate: string;
  plannedSteps: number;
  completedSteps: number;
  deltaDays: number;
  paceStatus: string;
  catchUpPlanNote?: string;
}

export interface CertificateDto {
  id: number;
  userId: number;
  milestoneId: number;
  certificateCode: string;
  pdfUrl?: string;
  badgeUrl?: string;
  status: string;
  issuedAt?: string;
  linkedInShared: boolean;
}

export interface ReplanRequestDto {
  roadmapId: number;
  newSkillGaps: string[];
  newStrongSkills: string[];
  experienceLevel: string;
}

export interface RoadmapGenerationRequestDto {
  userId: number;
  careerPathId: number;
  careerPathName: string;
  skillGaps: string[];
  strongSkills: string[];
  experienceLevel: string;
  weeklyHoursAvailable: number;
  preferredLanguage: string;
}

export interface AssessmentQuestionDto {
  id: number;
  text?: string;
  question?: string;
  order?: number;
  total?: number;
  category?: string;
  difficulty?: string;
  options: string[];
}

export interface AssessmentAnswerDto {
  questionId: number;
  selectedOption: string;
  confidenceLevel?: number;
  timeSpentSeconds?: number;
}

export interface AssessmentResultDto {
  id: number;
  userId: number;
  careerPathId: number;
  assessmentDate: string;
  skillGaps: string | string[];
  strongSkills: string | string[];
  experienceLevel: string;
  overallScore: number;
  answers: string;
}

export interface InterviewSessionDto {
  id: number;
  userId: number;
  careerPath: string;
  difficulty: string;
  status: string;
  startedAt?: string;
  completedAt?: string;
  finalScore?: number;
  totalQuestions?: number;
  answeredQuestions?: number;
}

export interface InterviewQuestionDto {
  id?: number;
  question?: string;
  text?: string;
  order?: number;
  total?: number;
}

export interface InterviewAnswerResultDto {
  evaluation: string;
  score?: number;
  completed: boolean;
  questionText?: string;
  userAnswer?: string;
}

export interface InterviewResultDto {
  sessionId: number;
  status: string;
  finalScore?: number;
  totalQuestions: number;
  answeredQuestions: number;
  answers: InterviewAnswerResultDto[];
}

export interface CareerPathOptionDto {
  id: number;
  title: string;
  description?: string;
  defaultTopics?: string;
  difficulty?: string;
  estimatedWeeks?: number;
  isPublished?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateCareerPathRequestDto {
  title: string;
  description?: string;
  defaultTopics?: string;
  difficulty?: string;
  estimatedWeeks?: number;
}

@Injectable({ providedIn: 'root' })
export class RoadmapApiService {
  private http = inject(HttpClient);
  private roadmapApiUrl = environment.apiUrls.roadmap.replace(/\/$/, '');
  private assessmentUrl = (environment.apiUrls.assessment || environment.assessmentApiUrl).replace(/\/$/, '');

  // Roadmap CRUD
  getUserRoadmap(userId: number): Observable<RoadmapResponse> {
    return this.http.get<RoadmapResponse>(`${this.roadmapApiUrl}/roadmaps/user/${userId}`);
  }

  getActiveRoadmap(userId: number): Observable<RoadmapResponse> {
    return this.http.get<RoadmapResponse>(`${this.roadmapApiUrl}/roadmaps/user/${userId}/active`);
  }

  getUserRoadmaps(userId: number): Observable<RoadmapResponse[]> {
    return this.http.get<RoadmapResponse[]>(`${this.roadmapApiUrl}/roadmaps/user/${userId}/all`);
  }

  getRoadmapById(id: number): Observable<RoadmapResponse> {
    return this.http.get<RoadmapResponse>(`${this.roadmapApiUrl}/roadmaps/${id}`);
  }

  // Visual Roadmap
  generateVisualRoadmap(request: RoadmapGenerationRequestDto): Observable<RoadmapVisualResponse> {
    return this.http.post<RoadmapVisualResponse>(`${this.roadmapApiUrl}/roadmaps/visual/generate`, request);
  }

  getRoadmapGraph(roadmapId: number): Observable<RoadmapVisualResponse> {
    return this.http.get<RoadmapVisualResponse>(`${this.roadmapApiUrl}/roadmaps/visual/${roadmapId}/graph`);
  }

  getNodeQuiz(nodeId: number, userId: number, questionCount = 5): Observable<NodeQuizResponseDto> {
    const params = new HttpParams()
      .set('userId', String(userId))
      .set('questionCount', String(questionCount));

    return this.http.get<NodeQuizResponseDto>(`${this.roadmapApiUrl}/roadmaps/visual/nodes/${nodeId}/quiz`, {
      params,
    });
  }

  getNodeProjectLab(nodeId: number, userId: number): Observable<NodeProjectLabDto> {
    const params = new HttpParams().set('userId', String(userId));
    return this.http.get<NodeProjectLabDto>(
      `${this.roadmapApiUrl}/roadmaps/visual/nodes/${nodeId}/project-lab`,
      { params }
    );
  }

  getNodeProjectLabHistory(nodeId: number, userId: number): Observable<NodeProjectLabDto[]> {
    const params = new HttpParams().set('userId', String(userId));
    return this.http.get<NodeProjectLabDto[]>(
      `${this.roadmapApiUrl}/roadmaps/visual/nodes/${nodeId}/project-lab/history`,
      { params }
    );
  }

  validateNodeProject(
    nodeId: number,
    userId: number,
    payload: NodeProjectValidationRequestDto
  ): Observable<NodeProjectValidationResponseDto> {
    const params = new HttpParams().set('userId', String(userId));
    return this.http.post<NodeProjectValidationResponseDto>(
      `${this.roadmapApiUrl}/roadmaps/visual/nodes/${nodeId}/project-lab/validate`,
      payload,
      { params }
    );
  }

  askNodeTutor(
    nodeId: number,
    userId: number,
    payload: NodeTutorPromptRequestDto
  ): Observable<NodeTutorPromptResponseDto> {
    const params = new HttpParams().set('userId', String(userId));
    return this.http.post<NodeTutorPromptResponseDto>(
      `${this.roadmapApiUrl}/roadmaps/visual/nodes/${nodeId}/tutor`,
      payload,
      { params }
    );
  }

  getNodeCourse(nodeId: number, userId: number, refresh = false): Observable<NodeCourseContentDto> {
    const params = new HttpParams().set('userId', String(userId)).set('refresh', String(refresh));
    return this.http.get<NodeCourseContentDto>(
      `${this.roadmapApiUrl}/roadmaps/visual/nodes/${nodeId}/course`,
      { params }
    );
  }

  getNodeCourseHistory(nodeId: number, userId: number): Observable<NodeCourseContentDto[]> {
    const params = new HttpParams().set('userId', String(userId));
    return this.http.get<NodeCourseContentDto[]>(
      `${this.roadmapApiUrl}/roadmaps/visual/nodes/${nodeId}/course/history`,
      { params }
    );
  }

  startNode(nodeId: number, userId: number): Observable<RoadmapVisualResponse> {
    return this.http.put<RoadmapVisualResponse>(`${this.roadmapApiUrl}/roadmaps/visual/nodes/${nodeId}/start?userId=${userId}`, {});
  }

  completeNode(nodeId: number, userId: number): Observable<RoadmapVisualResponse> {
    return this.http.put<RoadmapVisualResponse>(`${this.roadmapApiUrl}/roadmaps/visual/nodes/${nodeId}/complete?userId=${userId}`, {});
  }

  completeRoadmapStep(stepId: number, userId: number): Observable<void> {
    return this.http.put<void>(`${this.roadmapApiUrl}/roadmap-steps/${stepId}/complete?userId=${userId}`, {});
  }

  replanRoadmap(roadmapId: number, request: ReplanRequestDto): Observable<RoadmapVisualResponse> {
    return this.replanVisualRoadmap(roadmapId, request);
  }

  replanVisualRoadmap(roadmapId: number, request: ReplanRequestDto): Observable<RoadmapVisualResponse> {
    return this.http.post<RoadmapVisualResponse>(`${this.roadmapApiUrl}/roadmaps/visual/${roadmapId}/replan`, request);
  }

  getRoadmapStepsByRoadmapId(roadmapId: number): Observable<StepResponse[]> {
    return this.http.get<StepResponse[]>(`${this.roadmapApiUrl}/roadmap-steps/roadmap/${roadmapId}`);
  }

  generateProjectSuggestionsByRoadmapStep(
    roadmapId: number,
    stepOrder: number,
    domain: string,
    level = 'INTERMEDIATE'
  ): Observable<ProjectSuggestionDto[]> {
    const params = new HttpParams().set('domain', domain).set('level', level);
    return this.http.post<ProjectSuggestionDto[]>(
      `${this.roadmapApiUrl}/project-suggestions/generate/roadmap/${roadmapId}/step/${stepOrder}`,
      null,
      { params }
    );
  }

  getProjectSuggestionsByRoadmapStep(
    roadmapId: number,
    stepOrder: number
  ): Observable<ProjectSuggestionDto[]> {
    return this.http.get<ProjectSuggestionDto[]>(
      `${this.roadmapApiUrl}/project-suggestions/roadmap/${roadmapId}/step/${stepOrder}`
    );
  }

  submitProject(payload: ProjectSubmitRequestDto): Observable<ProjectSubmissionDto> {
    return this.http.post<ProjectSubmissionDto>(`${this.roadmapApiUrl}/projects/submit`, payload);
  }

  retryProjectSubmission(submissionId: number, payload: RetrySubmissionRequestDto): Observable<ProjectSubmissionDto> {
    return this.http.post<ProjectSubmissionDto>(
      `${this.roadmapApiUrl}/projects/submissions/${submissionId}/retry`,
      payload
    );
  }

  getProjectSubmissionReview(submissionId: number): Observable<ProjectReviewResponseDto> {
    return this.http.get<ProjectReviewResponseDto>(
      `${this.roadmapApiUrl}/projects/submissions/${submissionId}/review`
    );
  }

  getUserProjectSubmissions(userId: number): Observable<ProjectSubmissionDto[]> {
    return this.http.get<ProjectSubmissionDto[]>(
      `${this.roadmapApiUrl}/projects/submissions/user/${userId}`
    );
  }

  // Assessment
  // NOTE: This endpoint belongs to MS-Assessment service.
  // Routed through assessment service.
  getAssessmentQuestions(careerPathId: number): Observable<AssessmentQuestionDto[]> {
    return this.http.get<AssessmentQuestionDto[]>(`${this.assessmentUrl}/career-path/${careerPathId}/questions`);
  }

  // NOTE: This endpoint belongs to MS-Assessment service.
  // Routed through assessment service.
  submitAssessment(
    userId: number,
    careerPathId: number,
    answers: AssessmentAnswerDto[]
  ): Observable<AssessmentResultDto> {
    return this.http.post<AssessmentResultDto>(`${this.assessmentUrl}/submit`, {
      userId,
      careerPathId,
      answers,
    });
  }

  // NOTE: This endpoint belongs to MS-Assessment service.
  // Routed through assessment service.
  getLatestAssessment(userId: number): Observable<AssessmentResultDto> {
    return this.http.get<AssessmentResultDto>(`${this.assessmentUrl}/results/${userId}`);
  }

  // Interview
  createInterviewSession(
    userId: number,
    careerPath: string,
    difficulty: string
  ): Observable<InterviewSessionDto> {
    const params = new HttpParams()
      .set('userId', String(userId))
      .set('careerPath', careerPath)
      .set('difficulty', difficulty);
    return this.http.post<InterviewSessionDto>(`${this.roadmapApiUrl}/interview/session`, null, { params });
  }

  getInterviewSessions(userId: number): Observable<InterviewSessionDto[]> {
    return this.http.get<InterviewSessionDto[]>(`${this.roadmapApiUrl}/interview/user/${userId}/sessions`);
  }

  getInterviewQuestion(sessionId: number): Observable<InterviewQuestionDto> {
    return this.http.get<InterviewQuestionDto>(`${this.roadmapApiUrl}/interview/session/${sessionId}/question`);
  }

  submitInterviewAnswer(sessionId: number, answer: string): Observable<InterviewAnswerResultDto> {
    return this.http.post<InterviewAnswerResultDto>(`${this.roadmapApiUrl}/interview/session/${sessionId}/answer`, {
      answer,
    });
  }

  getInterviewScore(sessionId: number): Observable<InterviewResultDto> {
    return this.http.get<InterviewResultDto>(`${this.roadmapApiUrl}/interview/session/${sessionId}/score`);
  }

  getPublishedCareerPaths(): Observable<CareerPathOptionDto[]> {
    return this.http.get<CareerPathOptionDto[]>(`${this.roadmapApiUrl}/career-paths`);
  }

  getAdminCareerPaths(): Observable<CareerPathOptionDto[]> {
    return this.http.get<CareerPathOptionDto[]>(`${this.roadmapApiUrl}/admin/career-paths`);
  }

  createCareerPath(payload: CreateCareerPathRequestDto): Observable<CareerPathOptionDto> {
    return this.http.post<CareerPathOptionDto>(`${this.roadmapApiUrl}/admin/career-paths`, payload);
  }

  updateCareerPath(id: number, payload: CreateCareerPathRequestDto): Observable<CareerPathOptionDto> {
    return this.http.put<CareerPathOptionDto>(`${this.roadmapApiUrl}/admin/career-paths/${id}`, payload);
  }

  publishCareerPath(id: number): Observable<CareerPathOptionDto> {
    return this.http.post<CareerPathOptionDto>(`${this.roadmapApiUrl}/admin/career-paths/${id}/publish`, {});
  }

  deleteCareerPath(id: number): Observable<void> {
    return this.http.delete<void>(`${this.roadmapApiUrl}/admin/career-paths/${id}`);
  }

  // Progress
  getProgressSummary(roadmapId: number): Observable<ProgressSummaryDto> {
    return this.http.get<ProgressSummaryDto>(`${this.roadmapApiUrl}/roadmaps/${roadmapId}/progress-summary`);
  }

  // Milestones
  getMilestones(roadmapId: number): Observable<MilestoneDto[]> {
    return this.http.get<MilestoneDto[]>(`${this.roadmapApiUrl}/milestones/roadmap/${roadmapId}`);
  }

  getNextMilestone(roadmapId: number): Observable<MilestoneDto> {
    return this.http.get<MilestoneDto>(`${this.roadmapApiUrl}/milestones/roadmap/${roadmapId}/next`);
  }

  // Notifications
  getUserNotifications(userId: number): Observable<NotificationDto[]> {
    return this.http.get<NotificationDto[]>(`${this.roadmapApiUrl}/notifications/user/${userId}`);
  }

  getRoadmapNotifications(roadmapId: number, userId: number): Observable<NotificationDto[]> {
    return this.http.get<NotificationDto[]>(`${this.roadmapApiUrl}/notifications/roadmap/${roadmapId}?userId=${userId}`);
  }

  getUnreadCount(userId: number): Observable<{ count: number }> {
    return this.http.get<{ count: number }>(`${this.roadmapApiUrl}/notifications/user/${userId}/unread-count`);
  }

  markNotificationAsRead(notificationId: number): Observable<NotificationDto> {
    return this.http.put<NotificationDto>(`${this.roadmapApiUrl}/notifications/${notificationId}/read`, {});
  }

  markAllAsRead(userId: number): Observable<void> {
    return this.http.put<void>(`${this.roadmapApiUrl}/notifications/user/${userId}/read-all`, {});
  }

  deleteNotification(notificationId: number): Observable<void> {
    return this.http.delete<void>(`${this.roadmapApiUrl}/notifications/${notificationId}`);
  }

  // Resources
  getStepResources(topic: string, provider?: string, type?: string): Observable<StepResourceDto[]> {
    let params = new HttpParams().set('topic', topic);
    if (provider) {
      params = params.set('provider', provider);
    }
    if (type) {
      params = params.set('type', type);
    }
    return this.http.get<StepResourceDto[]>(`${this.roadmapApiUrl}/step-resources/search`, { params });
  }

  getStepResourcesByStep(stepId: number): Observable<StepResourceDto[]> {
    return this.http.get<StepResourceDto[]>(`${this.roadmapApiUrl}/step-resources/step/${stepId}`);
  }

  addStepResource(stepId: number, payload: CreateStepResourceRequest): Observable<StepResourceDto> {
    return this.http.post<StepResourceDto>(`${this.roadmapApiUrl}/step-resources/step/${stepId}`, payload);
  }

  deleteStepResource(resourceId: number): Observable<void> {
    return this.http.delete<void>(`${this.roadmapApiUrl}/step-resources/${resourceId}`);
  }

  syncStepResources(stepId: number): Observable<void> {
    return this.http.post<void>(`${this.roadmapApiUrl}/step-resources/step/${stepId}/sync`, {});
  }

  // Certificates
  getUserCertificates(userId: number): Observable<CertificateDto[]> {
    return this.http.get<CertificateDto[]>(`${this.roadmapApiUrl}/certificates/user/${userId}`);
  }

  shareCertificateLinkedIn(certificateId: number): Observable<CertificateDto> {
    return this.http.post<CertificateDto>(`${this.roadmapApiUrl}/certificates/${certificateId}/share-linkedin`, {});
  }

  getCertificatePdfUrl(certificateCode: string): string {
    return `${this.roadmapApiUrl}/certificates/${certificateCode}/pdf`;
  }

  getCertificateBadgeUrl(certificateCode: string): string {
    return `${this.roadmapApiUrl}/certificates/${certificateCode}/badge`;
  }

  // Streak
  getStreakInfo(userId: number, roadmapId: number): Observable<{ currentStreak: number; longestStreak: number }> {
    return this.http.get<{ currentStreak: number; longestStreak: number }>(
      `${this.roadmapApiUrl}/streaks/user/${userId}/roadmap/${roadmapId}`
    );
  }

  // Pace
  getCurrentPace(roadmapId: number): Observable<PaceSnapshotDto> {
    return this.http.get<PaceSnapshotDto>(`${this.roadmapApiUrl}/roadmaps/${roadmapId}/pace`);
  }
}
