import { useMutation, useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { ApiError } from '@/api/client'
import { analysisApi } from '@/api/endpoints'
import type { AnalysisDetail, ReviewDecision } from '@/api/types'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { formatDateTime } from './labels'

/**
 * 검토 기록 (F08). 검토는 Studio 판정을 바꾸지 않고 별도 의견으로 남습니다(BR01).
 * 이의는 사유가 필수입니다(BR14). 의견에 원문값을 적지 말라고 안내합니다.
 */
export function ReviewPanel({ detail }: { detail: AnalysisDetail }) {
  const queryClient = useQueryClient()
  const [decision, setDecision] = useState<ReviewDecision>('CONFIRMED')
  const [comment, setComment] = useState('')
  const submit = useMutation({
    mutationFn: () => analysisApi.review(detail.summary.id, decision, comment),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['analysis', detail.summary.id] })
      queryClient.invalidateQueries({ queryKey: ['reviews'] })
    },
  })

  if (detail.review) {
    return (
      <section className="border bg-white p-4 text-sm">
        <h3 className="font-semibold">검토 의견</h3>
        <p className="mt-1">
          {detail.review.decision === 'CONFIRMED' ? '판정 유지(확인 완료)' : '판정에 이의'} ·{' '}
          <span className="text-muted-foreground">{formatDateTime(detail.review.createdAt)}</span>
        </p>
        {detail.review.comment && (
          <p className="mt-1 whitespace-pre-line">{detail.review.comment}</p>
        )}
      </section>
    )
  }
  if (!detail.canReview) return null

  const needsComment = decision === 'DISPUTED' && comment.trim() === ''
  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    if (!needsComment) submit.mutate()
  }

  return (
    <form
      onSubmit={onSubmit}
      className="grid gap-3 border border-brand-navy/40 bg-secondary/50 p-4 text-sm"
    >
      <h3 className="font-semibold">검토 기록</h3>
      <div className="flex gap-4">
        <label className="flex items-center gap-2">
          <input
            type="radio"
            name="decision"
            checked={decision === 'CONFIRMED'}
            onChange={() => setDecision('CONFIRMED')}
          />
          판정 유지
        </label>
        <label className="flex items-center gap-2">
          <input
            type="radio"
            name="decision"
            checked={decision === 'DISPUTED'}
            onChange={() => setDecision('DISPUTED')}
          />
          판정에 이의
        </label>
      </div>
      <div className="grid gap-1.5">
        <Label htmlFor="review-comment">의견{decision === 'DISPUTED' ? ' (필수)' : ''}</Label>
        <Textarea
          id="review-comment"
          maxLength={1000}
          value={comment}
          onChange={(e) => setComment(e.target.value)}
          placeholder="의견에는 원문값(이름, 번호 등)을 적지 마세요."
        />
      </div>
      {submit.isError && (
        <Alert variant="destructive">
          <AlertDescription>
            {submit.error instanceof ApiError
              ? submit.error.message
              : '검토를 저장하지 못했습니다.'}
          </AlertDescription>
        </Alert>
      )}
      <Button type="submit" disabled={needsComment || submit.isPending}>
        검토 저장
      </Button>
    </form>
  )
}
