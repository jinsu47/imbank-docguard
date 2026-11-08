import type { AuditAction, UserStatus } from '@/api/types'

export const STATUS_LABELS: Record<UserStatus, string> = {
  ACTIVE: '사용 중',
  LOCKED: '잠김',
  DISABLED: '비활성',
}

export const ACTION_LABELS: Record<AuditAction, string> = {
  LOGIN_SUCCESS: '로그인',
  LOGIN_FAILURE: '로그인 실패',
  LOGOUT: '로그아웃',
  ACCOUNT_LOCKED: '계정 잠김(로그인 실패)',
  SIGNUP: '회원가입',
  ANALYSIS_CREATED: '분석 생성',
  ANALYSIS_DELETED: '분석 삭제',
  ANALYSIS_PURGED: '보관 기간 정리',
  RAW_VALUE_REVEALED: '원문 보기',
  ORIGINAL_FILE_VIEWED: '원본 열람',
  REVIEW_RECORDED: '검토 기록',
  USER_ROLE_CHANGED: '역할 변경',
  USER_STATUS_CHANGED: '상태 변경',
}
