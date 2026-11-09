// 서버 응답 형식. server 의 AuthDtos, AnalysisDtos, ResultDtos 와 맞춥니다.

export type Role = 'INSPECTOR' | 'REVIEWER' | 'ADMIN'

export interface Me {
  id: number
  email: string
  displayName: string
  role: Role
}

export type AnalysisStatus = 'UPLOADING' | 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'PURGED'
export type RiskGrade = 'HIGH' | 'MEDIUM' | 'LOW' | 'NONE' | 'UNKNOWN' | 'UNDECIDED'
export type ReviewStatus = 'NOT_REQUIRED' | 'PENDING' | 'CONFIRMED' | 'DISPUTED'
export type ReviewDecision = 'CONFIRMED' | 'DISPUTED'

export interface AnalysisSummary {
  id: string
  status: AnalysisStatus
  fileName?: string
  riskGrade?: RiskGrade
  gradeProvisional: boolean
  reviewRequired?: boolean
  reviewStatus: ReviewStatus
  documentType?: string
  findingCount?: number
  pageCount?: number
  errorCode?: string
  createdAt: string
  completedAt?: string
  /** 저장해 둔 응답을 돌려주는 재생 모드로 만든 분석(실제 판정이 아님) */
  replay: boolean
}

export interface ReviewInfo {
  decision: ReviewDecision
  comment?: string
  createdAt: string
}

export interface AnalysisDetail {
  summary: AnalysisSummary
  contentType: string
  studioConfigId: string
  parseCheckVerdict?: string
  extractCheckVerdict?: string
  steps: string[]
  completedSteps: string[]
  submittedAt?: string
  expiresAt: string
  purgedAt?: string
  owner: boolean
  /** 삭제·다시 실행을 할 수 있는지(소유자 또는 관리자) */
  canManage: boolean
  canReview: boolean
  review?: ReviewInfo
}

export interface Point {
  x: number
  y: number
}

export interface FindingView {
  rowIndex: number
  itemLabel: string
  infoType: string
  subjectRole: string
  maskState: string
  valueNature?: string
  policyLinked: boolean
  displayValue: string
  /** 일부 가린 항목에 남은 정보의 종류 (v4 부터) */
  residualInfo?: string
  page?: number
  wordBoxes?: Point[][]
  elementBox?: Point[]
  confidence?: string
}

export interface CitationView {
  index: number
  rowIndex?: number
  field?: string
  page?: number
  wordBoxes?: Point[][]
  elementBox?: Point[]
}

export interface ReportSection {
  title: string
  body: string
}

export type GradeRoute = 'RULE' | 'DECISION'

export interface ResultView {
  sections: ReportSection[]
  reviewReason?: string
  findings: FindingView[]
  citations: CitationView[]
  /** 위험 등급이 정해진 경로 (v4 부터) */
  gradeRoute?: GradeRoute
}

export interface RevealedFinding {
  rowIndex: number
  rawValue: string
  evidence?: string
  subjectBasis?: string
  reviewReason?: string
}

export interface ErrorBody {
  code: string
  message: string
  requestId?: string
}

// 관리자 (F09, F10)
export type UserStatus = 'ACTIVE' | 'LOCKED' | 'DISABLED'

export interface AdminUser {
  id: number
  email: string
  displayName: string
  role: Role
  status: UserStatus
  failedLoginCount: number
  lockedUntil?: string
  lastLoginAt?: string
  createdAt: string
}

export type AuditAction =
  | 'LOGIN_SUCCESS'
  | 'LOGIN_FAILURE'
  | 'LOGOUT'
  | 'ACCOUNT_LOCKED'
  | 'SIGNUP'
  | 'ANALYSIS_CREATED'
  | 'ANALYSIS_DELETED'
  | 'ANALYSIS_PURGED'
  | 'RAW_VALUE_REVEALED'
  | 'ORIGINAL_FILE_VIEWED'
  | 'RESULT_EXPORTED'
  | 'REVIEW_RECORDED'
  | 'USER_ROLE_CHANGED'
  | 'USER_STATUS_CHANGED'

export interface AuditRow {
  id: number
  createdAt: string
  actorId?: number
  actorEmail?: string
  action: AuditAction
  targetType?: string
  targetId?: string
  reason?: string
  ipAddress?: string
  requestId?: string
  detail?: Record<string, unknown>
}

export interface AuditPage {
  items: AuditRow[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface AdminAnalysis {
  id: string
  fileName: string
  ownerId: number
  ownerEmail?: string
  status: AnalysisStatus
  riskGrade?: RiskGrade
  reviewStatus: ReviewStatus
  errorCode?: string
  createdAt: string
  completedAt?: string
  expiresAt: string
  purgedAt?: string
  replay: boolean
}

// 시연 정확도 (F12)
export interface AccuracyRow {
  tag: string
  title: string
  expectedGrade: string
  expectedReview: string
  analysisId?: string
  actualGrade?: string
  actualReview?: string
  gradeMatch?: boolean
  reviewMatch?: boolean
  match?: boolean
  completedAt?: string
  elapsedSeconds?: number
}

export interface AccuracyReport {
  available: boolean
  message?: string
  total: number
  executed: number
  matched: number
  rows: AccuracyRow[]
}
