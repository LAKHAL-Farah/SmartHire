export type InterviewMode = 'PRACTICE' | 'TEST' | 'LIVE';
export type InterviewType = 'TECHNICAL' | 'BEHAVIORAL' | 'MIXED';
export type RoleType = 'SE' | 'CLOUD' | 'AI' | 'ALL';
export type LiveSubMode = 'PRACTICE_LIVE' | 'TEST_LIVE';
export type SessionStatus = 'IN_PROGRESS' | 'PAUSED' | 'EVALUATING' | 'COMPLETED' | 'ABANDONED';
export type QuestionType = 'BEHAVIORAL' | 'TECHNICAL' | 'SITUATIONAL' | 'CODING';
export type DifficultyLevel = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED' | 'EXPERT';
export type CodeLanguage = 'PYTHON' | 'JAVA' | 'JAVASCRIPT' | 'CPP';

export interface StartLiveSessionRequest {
  userId: number;
  careerPathId: number;
  liveSubMode: LiveSubMode;
  questionCount: number;
  companyName?: string;
  targetRole?: string;
}

export interface StartLiveSessionResponse {
  sessionId: number;
  greetingAudioUrl: string;
  firstQuestionText: string;
  firstQuestionId: number;
  totalQuestions: number;
  liveSubMode: LiveSubMode;
  status: SessionStatus | string;
}

export interface LiveBootstrapResponse {
  sessionId: number;
  greetingAudioUrl: string;
  firstQuestionText: string;
  firstQuestionId: number;
  totalQuestions: number;
  currentQuestionIndex: number;
  liveSubMode: LiveSubMode;
  status: SessionStatus | string;
}

export interface InterviewStreakDto {
  id: number;
  userId: number;
  currentStreak: number;
  longestStreak: number;
  lastSessionDate: string | null;
  totalSessionsCompleted: number;
}

export interface InterviewReportDto {
  id: number;
  sessionId: number;
  userId: number;
  finalScore: number | null;
  percentileRank: number | null;
  contentAvg: number | null;
  voiceAvg: number | null;
  technicalAvg: number | null;
  presenceAvg: number | null;
  overallStressLevel: 'low' | 'medium' | 'high' | null;
  avgStressScore: number | null;
  questionStressScores: QuestionStressScoreDto[] | null;
  strengths: string | null;
  weaknesses: string | null;
  recommendations: string | null;
  recruiterVerdict: string | null;
  pdfUrl: string | null;
  generatedAt: string | null;
}

export interface QuestionStressScoreDto {
  questionIndex: number;
  score: number;
  level: 'low' | 'medium' | 'high';
}

export interface InterviewQuestionDto {
  id: number;
  careerPathId: number;
  roleType: RoleType;
  questionText: string;
  metadata: string | null;
  type: QuestionType;
  difficulty: DifficultyLevel;
  domain: string | null;
  category: string | null;
  expectedPoints: string | null;
  followUps: string | null;
  hints: string | null;
  idealAnswer: string | null;
  sampleCode: string | null;
  tags: string | null;
  ttsAudioUrl: string | null;
  active: boolean;
}

export interface SessionQuestionOrderDto {
  id: number;
  sessionId: number;
  questionId: number;
  questionOrder: number;
  nextQuestionId: number | null;
  timeAllottedSeconds: number | null;
  wasSkipped: boolean;
  question: InterviewQuestionDto | null;
}

export interface AnswerEvaluationDto {
  id: number;
  answerId: number;
  contentScore: number | null;
  clarityScore: number | null;
  technicalScore: number | null;
  codeCorrectnessScore: number | null;
  codeComplexityNote: string | null;
  confidenceScore: number | null;
  toneScore: number | null;
  emotionScore: number | null;
  speechRate: number | null;
  hesitationCount: number | null;
  postureScore: number | null;
  eyeContactScore: number | null;
  expressionScore: number | null;
  overallScore: number | null;
  aiFeedback: string | null;
  followUpGenerated: string | null;
}

export interface SessionAnswerDto {
  id: number;
  sessionId: number;
  questionId: number;
  answerText: string | null;
  codeAnswer: string | null;
  codeOutput: string | null;
  codeLanguage: string | null;
  videoUrl: string | null;
  audioUrl: string | null;
  retryCount: number | null;
  timeSpentSeconds: number | null;
  submittedAt: string | null;
  isFollowUp: boolean;
  parentAnswerId: number | null;
  answerEvaluation: AnswerEvaluationDto | null;
  codeExecutionResult: CodeExecutionResultDto | null;
  architectureDiagram: ArchitectureDiagramDto | null;
  mlScenarioAnswer: MlScenarioAnswerDto | null;
}

export interface CodeExecutionResultDto {
  id: number;
  answerId: number;
  language: CodeLanguage;
  sourceCode: string | null;
  stdout: string | null;
  stderr: string | null;
  testCasesPassed: number | null;
  testCasesTotal: number | null;
  executionTimeMs: number | null;
  memoryUsedKb: number | null;
  complexityNote: string | null;
}

export interface ArchitectureDiagramDto {
  id: number;
  answerId: number;
  diagramJson: string | null;
  nodeCount: number | null;
  edgeCount: number | null;
  componentTypes: string | null;
  aiDesignScore: number | null;
  aiFeedback: string | null;
}

export interface MlScenarioAnswerDto {
  id: number;
  answerId: number;
  modelChosen: string | null;
  featuresDescribed: string | null;
  metricsDescribed: string | null;
  deploymentStrategy: string | null;
  extractedConcepts: string | null;
  aiEvaluationScore: number | null;
}

export interface QuestionBookmarkDto {
  id: number;
  userId: number;
  questionId: number;
  note: string | null;
  tagLabel: string | null;
  savedAt: string | null;
  question: InterviewQuestionDto | null;
}

export interface AddBookmarkRequest {
  userId: number;
  questionId: number;
  note?: string;
  tagLabel?: string;
}

export interface UpsertQuestionRequest {
  careerPathId: number;
  roleType: RoleType;
  questionText: string;
  metadata?: string | null;
  type: QuestionType;
  difficulty: DifficultyLevel;
  domain?: string | null;
  category?: string | null;
  expectedPoints?: string | null;
  followUps?: string | null;
  hints?: string | null;
  idealAnswer?: string | null;
  sampleCode?: string | null;
  tags?: string | null;
  isActive?: boolean;
}

export interface InterviewSessionDto {
  id: number;
  userId: number;
  careerPathId: number;
  roleType: RoleType;
  mode: InterviewMode;
  liveSubMode?: LiveSubMode | null;
  type: InterviewType;
  status: SessionStatus;
  totalScore: number | null;
  currentQuestionIndex: number;
  durationSeconds: number;
  startedAt: string | null;
  endedAt: string | null;
  pressureMode: boolean;
  pressureEventsTriggered: number;
  questionOrders: SessionQuestionOrderDto[] | null;
  answers: SessionAnswerDto[] | null;
  report: InterviewReportDto | null;
}

export interface StartSessionRequest {
  userId: number;
  careerPathId: number;
  role: RoleType;
  roleType?: RoleType;
  mode: InterviewMode;
  type: InterviewType;
  questionCount: number;
}

export interface SubmitAnswerRequest {
  sessionId: number;
  questionId: number;
  answerText: string;
  codeAnswer?: string | null;
}

export interface RetryAnswerRequest {
  sessionId: number;
  questionId: number;
  answerText: string;
  codeAnswer?: string | null;
}

export interface SubmitFollowUpRequest {
  sessionId: number;
  questionId: number;
  parentAnswerId: number;
  answerText: string;
}
