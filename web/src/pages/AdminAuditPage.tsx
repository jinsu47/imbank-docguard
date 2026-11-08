import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { adminApi, type AuditQuery } from '@/api/endpoints'
import type { AuditAction, AuditRow } from '@/api/types'
import { PageBand, PageBody } from '@/components/PageBand'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { ACTION_LABELS } from '@/features/admin/labels'
import { formatDateTime } from '@/features/analysis/labels'

const PAGE_SIZE = 50

/** 날짜 입력값(YYYY-MM-DD)을 그날 0시(한국 시간 기준 브라우저 시간대)의 ISO 시각으로 바꿉니다. */
function startOfDay(date: string): string | undefined {
  return date ? new Date(`${date}T00:00:00`).toISOString() : undefined
}

function nextDay(date: string): string | undefined {
  if (!date) return undefined
  const d = new Date(`${date}T00:00:00`)
  d.setDate(d.getDate() + 1)
  return d.toISOString()
}

// 감사 기록 (F09). 기간·사용자·행동으로 거릅니다(USER_FLOW.md 1-3). 조회만 하고 수정·삭제는 없습니다.
export default function AdminAuditPage() {
  const users = useQuery({ queryKey: ['admin', 'users'], queryFn: adminApi.users })
  const [action, setAction] = useState<AuditAction | ''>('')
  const [actorId, setActorId] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [page, setPage] = useState(0)

  const query: AuditQuery = {
    action: action || undefined,
    actorId: actorId ? Number(actorId) : undefined,
    from: startOfDay(from),
    to: nextDay(to),
    page,
    size: PAGE_SIZE,
  }
  const logs = useQuery({
    queryKey: ['admin', 'audit', query],
    queryFn: () => adminApi.auditLogs(query),
    placeholderData: keepPreviousData,
  })
  const resetPage =
    <T,>(setter: (v: T) => void) =>
    (v: T) => {
      setter(v)
      setPage(0)
    }

  return (
    <>
      <PageBand
        title="감사 기록"
        description="누가 언제 무엇을 했는지 기간·사용자·행동으로 찾아봅니다."
      />
      <PageBody wide>
        <section className="flex flex-wrap items-end gap-3 border bg-muted p-4">
          <div className="grid gap-1.5">
            <Label htmlFor="audit-action">행동</Label>
            <select
              id="audit-action"
              className="dg-select"
              value={action}
              onChange={(e) => resetPage(setAction)(e.target.value as AuditAction | '')}
            >
              <option value="">전체</option>
              {(Object.keys(ACTION_LABELS) as AuditAction[]).map((a) => (
                <option key={a} value={a}>
                  {ACTION_LABELS[a]}
                </option>
              ))}
            </select>
          </div>
          <div className="grid gap-1.5">
            <Label htmlFor="audit-actor">사용자</Label>
            <select
              id="audit-actor"
              className="dg-select"
              value={actorId}
              onChange={(e) => resetPage(setActorId)(e.target.value)}
            >
              <option value="">전체</option>
              {users.data?.map((u) => (
                <option key={u.id} value={u.id}>
                  {u.email}
                </option>
              ))}
            </select>
          </div>
          <div className="grid gap-1.5">
            <Label htmlFor="audit-from">시작일</Label>
            <Input
              id="audit-from"
              type="date"
              value={from}
              onChange={(e) => resetPage(setFrom)(e.target.value)}
            />
          </div>
          <div className="grid gap-1.5">
            <Label htmlFor="audit-to">종료일</Label>
            <Input
              id="audit-to"
              type="date"
              value={to}
              onChange={(e) => resetPage(setTo)(e.target.value)}
            />
          </div>
        </section>

        {logs.isPending ? (
          <Skeleton className="h-60 w-full" />
        ) : logs.isError ? (
          <p className="text-sm text-destructive">감사 기록을 불러오지 못했습니다.</p>
        ) : (
          <>
            <div className="overflow-x-auto bg-white">
              <table className="dg-table">
                <thead>
                  <tr>
                    <th>시각</th>
                    <th>사용자</th>
                    <th>행동</th>
                    <th>대상</th>
                    <th>사유 · 내용</th>
                    <th>IP · 요청 ID</th>
                  </tr>
                </thead>
                <tbody>
                  {logs.data.items.length === 0 && (
                    <tr>
                      <td colSpan={6} className="px-3 py-8 text-center text-muted-foreground">
                        조건에 맞는 기록이 없습니다.
                      </td>
                    </tr>
                  )}
                  {logs.data.items.map((row) => (
                    <tr key={row.id} className="align-top">
                      <td className="whitespace-nowrap">{formatDateTime(row.createdAt)}</td>
                      <td>{row.actorEmail ?? '-'}</td>
                      <td className="whitespace-nowrap">{ACTION_LABELS[row.action]}</td>
                      <td className="font-mono text-xs">
                        {row.targetType ? `${row.targetType} ${row.targetId?.slice(0, 8)}` : '-'}
                      </td>
                      <td>{describe(row)}</td>
                      <td className="font-mono text-xs text-muted-foreground">
                        {row.ipAddress ?? '-'}
                        <br />
                        {row.requestId?.slice(0, 8)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="flex items-center justify-between text-sm">
              <span className="text-muted-foreground">
                전체 {logs.data.totalElements}건 · {logs.data.page + 1} /{' '}
                {Math.max(1, logs.data.totalPages)}쪽
              </span>
              <div className="flex gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page === 0}
                  onClick={() => setPage(page - 1)}
                >
                  이전
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page + 1 >= logs.data.totalPages}
                  onClick={() => setPage(page + 1)}
                >
                  다음
                </Button>
              </div>
            </div>
          </>
        )}
      </PageBody>
    </>
  )
}

function describe(row: AuditRow): string {
  const parts: string[] = []
  if (row.reason) parts.push(row.reason)
  const before = row.detail?.before
  const after = row.detail?.after
  if (before !== undefined || after !== undefined)
    parts.push(`${String(before)} → ${String(after)}`)
  if (row.detail?.decision) parts.push(String(row.detail.decision))
  return parts.join(' · ') || '-'
}
