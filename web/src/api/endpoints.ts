import { api, apiBlob, apiFile } from './client'
import type {
  AccuracyReport,
  AdminAnalysis,
  AdminUser,
  AuditAction,
  AuditPage,
  AnalysisDetail,
  AnalysisSummary,
  Me,
  ResultView,
  RevealedFinding,
  ReviewDecision,
  Role,
  UserStatus,
} from './types'

// 서버 API 목록 (ARCHITECTURE.md 5장)

export const authApi = {
  me: () => api<Me>('/api/auth/me', { skipUnauthorizedRedirect: true }),
  login: (email: string, password: string) =>
    api<Me>('/api/auth/login', {
      method: 'POST',
      json: { email, password },
      skipUnauthorizedRedirect: true,
    }),
  signup: (input: { displayName: string; email: string; password: string; agreed: boolean }) =>
    api<Me>('/api/auth/signup', { method: 'POST', json: input }),
  logout: () => api<void>('/api/auth/logout', { method: 'POST' }),
}

export type ExportFormat = 'md' | 'json' | 'pdf'

export const analysisApi = {
  list: () => api<AnalysisSummary[]>('/api/analyses'),
  create: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return api<{ id: string }>('/api/analyses', { method: 'POST', form })
  },
  detail: (id: string) => api<AnalysisDetail>(`/api/analyses/${id}`),
  result: (id: string) => api<ResultView>(`/api/analyses/${id}/result`),
  file: (id: string) => apiBlob(`/api/analyses/${id}/file`),
  export: (id: string, format: ExportFormat) =>
    apiFile(`/api/analyses/${id}/export?format=${format}`),
  reveal: (id: string, reason: string) =>
    api<RevealedFinding[]>(`/api/analyses/${id}/reveal`, { method: 'POST', json: { reason } }),
  retry: (id: string) => api<void>(`/api/analyses/${id}/retry`, { method: 'POST' }),
  remove: (id: string) => api<void>(`/api/analyses/${id}`, { method: 'DELETE' }),
  review: (id: string, decision: ReviewDecision, comment: string) =>
    api<void>(`/api/analyses/${id}/review`, {
      method: 'POST',
      json: { decision, comment: comment.trim() || null },
    }),
}

export const reviewApi = {
  queue: () => api<AnalysisSummary[]>('/api/reviews/queue'),
}

export interface AuditQuery {
  action?: AuditAction
  actorId?: number
  from?: string
  to?: string
  page?: number
  size?: number
}

export const adminApi = {
  users: () => api<AdminUser[]>('/api/admin/users'),
  updateUser: (id: number, change: { role?: Role; status?: UserStatus; reason?: string }) =>
    api<AdminUser>(`/api/admin/users/${id}`, { method: 'PATCH', json: change }),
  auditLogs: (query: AuditQuery) => {
    const params = new URLSearchParams()
    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined && value !== '') params.set(key, String(value))
    }
    return api<AuditPage>(`/api/admin/audit-logs?${params.toString()}`)
  },
  analyses: () => api<AdminAnalysis[]>('/api/admin/analyses'),
}

export const demoApi = {
  accuracy: () => api<AccuracyReport>('/api/demo/accuracy'),
}
