import type { AnalysisSummary } from '@/api/types'
import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'
import { GRADE_LABELS, GRADE_STYLES, REVIEW_LABELS, STATUS_LABELS } from './labels'

/** 위험 등급 표시. 완료 전에는 상태를 보여 줍니다. */
export function GradeBadge({
  analysis,
  size = 'sm',
}: {
  analysis: AnalysisSummary
  size?: 'sm' | 'lg'
}) {
  if (analysis.status !== 'COMPLETED' && analysis.status !== 'PURGED') {
    return (
      <Badge variant={analysis.status === 'FAILED' ? 'destructive' : 'secondary'}>
        {STATUS_LABELS[analysis.status]}
      </Badge>
    )
  }
  if (!analysis.riskGrade) {
    return <Badge variant="secondary">-</Badge>
  }
  const label = (analysis.gradeProvisional ? '잠정 ' : '') + GRADE_LABELS[analysis.riskGrade]
  return (
    <Badge className={cn(GRADE_STYLES[analysis.riskGrade], size === 'lg' && 'px-3 py-1 text-base')}>
      위험 등급 {label}
    </Badge>
  )
}

export function ReviewBadge({ analysis }: { analysis: AnalysisSummary }) {
  if (analysis.status !== 'COMPLETED' && analysis.status !== 'PURGED') return null
  const pending = analysis.reviewStatus === 'PENDING'
  return (
    <Badge
      variant={pending ? 'default' : 'outline'}
      className={pending ? 'bg-brand-navy text-white' : undefined}
    >
      {REVIEW_LABELS[analysis.reviewStatus]}
    </Badge>
  )
}

export function ReplayBadge({ analysis }: { analysis: AnalysisSummary }) {
  if (!analysis.replay) return null
  return (
    <Badge
      variant="outline"
      title="저장해 둔 Studio 응답을 돌려준 결과입니다. 올린 문서의 실제 판정이 아닙니다."
    >
      재생 모드
    </Badge>
  )
}
