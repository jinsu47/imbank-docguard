import { useMutation } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { ApiError } from '@/api/client'
import { analysisApi } from '@/api/endpoints'
import type { RevealedFinding } from '@/api/types'
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
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'

/** 원문 보기 (BR08, BR10). 사유를 적으면 서버가 감사 기록을 남긴 뒤 원문값을 돌려줍니다. */
export function RevealDialog({
  analysisId,
  open,
  onOpenChange,
  onRevealed,
}: {
  analysisId: string
  open: boolean
  onOpenChange: (open: boolean) => void
  onRevealed: (values: RevealedFinding[]) => void
}) {
  const [reason, setReason] = useState('')
  const reveal = useMutation({
    mutationFn: () => analysisApi.reveal(analysisId, reason.trim()),
    onSuccess: (values) => {
      onRevealed(values)
      setReason('')
      onOpenChange(false)
    },
  })

  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    if (reason.trim().length >= 2) reveal.mutate()
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <form onSubmit={onSubmit} className="grid gap-4">
          <DialogHeader>
            <DialogTitle>원문 보기</DialogTitle>
            <DialogDescription>
              가린 값의 원문과 문서 위치를 보여 줍니다. 누가 언제 어떤 사유로 열람했는지 감사 기록에
              남습니다.
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-2">
            <Label htmlFor="reveal-reason">열람 사유</Label>
            <Textarea
              id="reveal-reason"
              maxLength={300}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="예: 외부 제공 전 가림 범위 확인"
            />
          </div>
          {reveal.isError && (
            <Alert variant="destructive">
              <AlertDescription>
                {reveal.error instanceof ApiError
                  ? reveal.error.message
                  : '원문을 불러오지 못했습니다.'}
              </AlertDescription>
            </Alert>
          )}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              취소
            </Button>
            <Button type="submit" disabled={reason.trim().length < 2 || reveal.isPending}>
              원문 보기
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
