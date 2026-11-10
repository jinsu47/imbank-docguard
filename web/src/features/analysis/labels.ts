import type { AnalysisStatus, ReviewStatus, RiskGrade } from '@/api/types'

// 화면 문구. 등급은 보고서 표기(상·중·하·미확정)를 그대로 씁니다(DOMAIN.md 1장). 노출 없음은 v2.5 기록용입니다.

export const GRADE_LABELS: Record<RiskGrade, string> = {
  HIGH: '상',
  MEDIUM: '중',
  LOW: '하',
  NONE: '노출 없음',
  UNKNOWN: '판정 확인 필요',
  UNDECIDED: '미확정',
}

export const GRADE_STYLES: Record<RiskGrade, string> = {
  HIGH: 'bg-[#c8323f] text-white',
  MEDIUM: 'bg-[#b5650a] text-white',
  LOW: 'bg-tile-blue text-white',
  NONE: 'bg-tile-teal text-white',
  UNKNOWN: 'bg-brand-slate text-white',
  UNDECIDED: 'bg-brand-slate text-white',
}

export const STATUS_LABELS: Record<AnalysisStatus, string> = {
  UPLOADING: '업로드 중',
  QUEUED: '대기 중',
  RUNNING: '분석 중',
  COMPLETED: '완료',
  FAILED: '실패',
  PURGED: '보관 기간 만료',
}

export const REVIEW_LABELS: Record<ReviewStatus, string> = {
  NOT_REQUIRED: '검토 불필요',
  PENDING: '검토 대기 중',
  CONFIRMED: '검토 완료(판정 유지)',
  DISPUTED: '검토 완료(이의)',
}

/** 워크플로 단계 (StudioModels.Step) 이름. 분석 중 화면의 단계 칸에 씁니다. */
export const STEP_LABELS: Record<string, string> = {
  PARSE: '문서 인식',
  PARSE_CHECK: '인식 점검',
  CLASSIFY: '문서 분류',
  EXTRACT: '민감정보 추출',
  MATCH: '정책 대조',
  EXTRACT_CHECK: '추출 점검',
  REPORT: '보고서 작성',
}

/** 워크플로 단계 (StudioModels.Step)별로, 그 단계를 진행하는 동안 보여 줄 문구 */
export const STEP_MESSAGES: Record<string, string> = {
  PARSE: '문서를 읽는 중',
  PARSE_CHECK: '인식 결과를 확인하는 중',
  CLASSIFY: '문서 종류를 분류하는 중',
  EXTRACT: '민감정보를 찾는 중',
  MATCH: '공식 근거와 대조하는 중',
  EXTRACT_CHECK: '찾은 내용을 점검하는 중',
  REPORT: '분석 보고서를 작성하는 중',
}

/** 끝난 단계 수로 계산한 진행률(%). 분석이 끝나기 전에는 100 을 보여 주지 않습니다. */
export function progressPercent(completed: number, total: number): number {
  if (total <= 0) return 0
  return Math.min(99, Math.round((completed / total) * 100))
}

/** 지금 진행 중인 일을 나타내는 문구 */
export function progressMessage(
  status: AnalysisStatus,
  steps: string[],
  completedSteps: string[],
): string {
  if (status === 'UPLOADING') return '문서를 올리는 중'
  if (status === 'QUEUED') return '분석 순서를 기다리는 중'
  const done = new Set(completedSteps)
  const current = steps.find((step) => !done.has(step))
  return (current && STEP_MESSAGES[current]) || '결과를 정리하는 중'
}

export const ERROR_MESSAGES: Record<string, string> = {
  STUDIO_NOT_CONFIGURED: '서버에 Upstage 설정이 비어 있습니다. 관리자에게 문의해 주세요.',
  STUDIO_REPLAY_EMPTY: '재생 모드에 쓸 저장 응답이 없습니다.',
  STUDIO_UPLOAD_FAILED: 'Studio 에 문서를 올리지 못했습니다.',
  STUDIO_JOB_FAILED: 'Studio 분석 작업이 실패했습니다.',
  STUDIO_TIMEOUT: '분석 시간이 너무 오래 걸려 중단했습니다.',
  STUDIO_BAD_RESPONSE: 'Studio 응답을 읽지 못했습니다.',
}

export function errorMessage(code?: string): string {
  return (code && ERROR_MESSAGES[code]) || '분석을 마치지 못했습니다.'
}

export function isInProgress(status: AnalysisStatus): boolean {
  return status === 'UPLOADING' || status === 'QUEUED' || status === 'RUNNING'
}

export function formatDateTime(iso?: string): string {
  if (!iso) return '-'
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(iso))
}
