import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { ApiError } from '@/api/client'
import { adminApi } from '@/api/endpoints'
import type { AdminUser, Role, UserStatus } from '@/api/types'
import { PageBand, PageBody } from '@/components/PageBand'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { STATUS_LABELS } from '@/features/admin/labels'
import { formatDateTime } from '@/features/analysis/labels'
import { ROLE_LABELS, useMe } from '@/features/auth/useMe'

interface PendingChange {
  user: AdminUser
  role?: Role
  status?: UserStatus
}

// 계정 관리 (F10). 바꾸기 전에 확인 대화상자를 띄우고, 바꾼 내용은 감사 기록에 남습니다(USER_FLOW.md 1-3).
export default function AdminUsersPage() {
  const { data: me } = useMe()
  const users = useQuery({ queryKey: ['admin', 'users'], queryFn: adminApi.users })
  const [pending, setPending] = useState<PendingChange>()

  return (
    <>
      <PageBand
        title="계정 관리"
        description="역할과 상태를 바꾸면 해당 사용자의 로그인 세션에도 바로 반영됩니다. 자기 계정은 바꿀 수 없습니다."
      />
      <PageBody>
        {users.isPending ? (
          <Skeleton className="h-40 w-full" />
        ) : users.isError ? (
          <p className="text-sm text-destructive">계정 목록을 불러오지 못했습니다.</p>
        ) : (
          <div className="overflow-x-auto bg-white">
            <table className="dg-table">
              <thead>
                <tr>
                  <th>이름 · 이메일</th>
                  <th>역할</th>
                  <th>상태</th>
                  <th>마지막 로그인</th>
                  <th>가입</th>
                </tr>
              </thead>
              <tbody>
                {users.data.map((u) => {
                  const self = u.id === me?.id
                  return (
                    <tr key={u.id}>
                      <td>
                        <div className="font-medium">
                          {u.displayName}
                          {self && <span className="ml-1 text-xs text-muted-foreground">(나)</span>}
                        </div>
                        <div className="text-xs text-muted-foreground">{u.email}</div>
                      </td>
                      <td>
                        <select
                          aria-label={`${u.email} 역할`}
                          className="dg-select"
                          value={u.role}
                          disabled={self}
                          onChange={(e) => setPending({ user: u, role: e.target.value as Role })}
                        >
                          {(Object.keys(ROLE_LABELS) as Role[]).map((r) => (
                            <option key={r} value={r}>
                              {ROLE_LABELS[r]}
                            </option>
                          ))}
                        </select>
                      </td>
                      <td>
                        <select
                          aria-label={`${u.email} 상태`}
                          className="dg-select"
                          value={u.status}
                          disabled={self}
                          onChange={(e) =>
                            setPending({ user: u, status: e.target.value as UserStatus })
                          }
                        >
                          {(Object.keys(STATUS_LABELS) as UserStatus[]).map((s) => (
                            <option key={s} value={s}>
                              {STATUS_LABELS[s]}
                            </option>
                          ))}
                        </select>
                        {u.status === 'LOCKED' && u.lockedUntil && (
                          <div className="mt-1 text-xs text-muted-foreground">
                            로그인 실패 잠금 · {formatDateTime(u.lockedUntil)}까지
                          </div>
                        )}
                      </td>
                      <td className="text-muted-foreground">{formatDateTime(u.lastLoginAt)}</td>
                      <td className="text-muted-foreground">{formatDateTime(u.createdAt)}</td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
        {pending && <ConfirmChange change={pending} onClose={() => setPending(undefined)} />}
      </PageBody>
    </>
  )
}

function ConfirmChange({ change, onClose }: { change: PendingChange; onClose: () => void }) {
  const queryClient = useQueryClient()
  const [reason, setReason] = useState('')
  const save = useMutation({
    mutationFn: () =>
      adminApi.updateUser(change.user.id, {
        role: change.role,
        status: change.status,
        reason: reason.trim() || undefined,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin'] })
      onClose()
    },
  })
  const description = change.role
    ? `역할: ${ROLE_LABELS[change.user.role]} → ${ROLE_LABELS[change.role]}`
    : `상태: ${STATUS_LABELS[change.user.status]} → ${STATUS_LABELS[change.status!]}`

  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    save.mutate()
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        <form onSubmit={onSubmit} className="grid gap-4">
          <DialogHeader>
            <DialogTitle>{change.user.email}</DialogTitle>
            <DialogDescription>{description}. 이 변경은 감사 기록에 남습니다.</DialogDescription>
          </DialogHeader>
          <div className="grid gap-2">
            <Label htmlFor="change-reason">사유 (선택)</Label>
            <Input
              id="change-reason"
              maxLength={300}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
          </div>
          {save.isError && (
            <Alert variant="destructive">
              <AlertDescription>
                {save.error instanceof ApiError ? save.error.message : '바꾸지 못했습니다.'}
              </AlertDescription>
            </Alert>
          )}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              취소
            </Button>
            <Button type="submit" disabled={save.isPending}>
              변경
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
