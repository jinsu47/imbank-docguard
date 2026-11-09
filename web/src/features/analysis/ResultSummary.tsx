import type { AnalysisDetail, GradeRoute, ResultView } from '@/api/types'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { splitCitations } from '@/features/report/citations'
import { cn } from '@/lib/utils'
import { ReplayBadge } from './GradeBadge'
import { formatDateTime, GRADE_LABELS, GRADE_STYLES, REVIEW_LABELS } from './labels'

const VERDICT_LABELS: Record<string, string> = { green: '정상', yellow: '주의', red: '확인 필요' }
const GRADE_ROUTE_LABELS: Record<GradeRoute, string> = {
  RULE: '고위험 규칙으로 판정',
  DECISION: 'AI가 기준표로 판정',
}

/** 결과 상단 (F03): 위험 등급·사람 검토·문서 종류·발견 항목을 색면 타일로 보여 주고, 아래에 요약을 둡니다. */
export function ResultSummary({ detail, result }: { detail: AnalysisDetail; result?: ResultView }) {
  const s = detail.summary
  const summaryText = result?.sections.find((x) => x.title === '요약')?.body
  const plain = (text: string) =>
    splitCitations(text)
      .filter((p) => p.type === 'text')
      .map((p) => (p.type === 'text' ? p.text : ''))
      .join('')
  const grade = s.riskGrade ?? 'UNKNOWN'
  const gradeLabel = (s.gradeProvisional ? '잠정 ' : '') + GRADE_LABELS[grade]

  return (
    <section className="grid gap-4">
      <div className="grid grid-cols-2 lg:grid-cols-4">
        <SummaryTile
          label="위험 등급"
          value={gradeLabel}
          note={result?.gradeRoute && GRADE_ROUTE_LABELS[result.gradeRoute]}
          className={GRADE_STYLES[grade]}
        />
        <SummaryTile
          label="사람 검토"
          value={s.reviewRequired ? '필요' : '불필요'}
          note={REVIEW_LABELS[s.reviewStatus]}
          className={s.reviewRequired ? 'bg-brand-navy text-white' : 'bg-tile-sky text-[#17324a]'}
        />
        <SummaryTile
          label="문서 종류"
          value={s.documentType ?? '미확인'}
          note={`${s.pageCount ?? '-'}쪽`}
          className="bg-tile-mint text-[#123c34]"
        />
        <SummaryTile
          label="발견 항목"
          value={`${s.findingCount ?? 0}건`}
          note={`완료 ${formatDateTime(s.completedAt)}`}
          className="bg-tile-gray text-foreground"
        />
      </div>
      {s.replay && (
        <Alert>
          <AlertTitle className="flex items-center gap-2">
            재생 모드 결과입니다 <ReplayBadge analysis={s} />
          </AlertTitle>
          <AlertDescription>
            Upstage 를 호출하지 않고 2026-11-06 에 저장해 둔 실제 응답을 돌려준 결과입니다. 올린
            문서의 실제 판정이 아니므로 화면 시험에만 쓰세요.
          </AlertDescription>
        </Alert>
      )}
      {s.riskGrade === 'UNKNOWN' && (
        <Alert variant="destructive">
          <AlertTitle>판정 확인 필요</AlertTitle>
          <AlertDescription>
            보고서의 등급 줄을 읽지 못해 등급을 정하지 않았습니다. 검토 대기열로 보냈습니다.
          </AlertDescription>
        </Alert>
      )}
      {s.riskGrade === 'UNDECIDED' && (
        <Alert>
          <AlertTitle>위험 등급 미확정</AlertTitle>
          <AlertDescription>
            자동 점검으로 위험 등급을 확정할 수 없는 문서입니다. 사람 검토 사유를 확인해 주세요.
          </AlertDescription>
        </Alert>
      )}
      {s.reviewRequired && result?.reviewReason && (
        <p className="border-l-[3px] border-brand-navy bg-secondary/60 px-4 py-3 text-sm">
          <span className="font-bold">사람 검토 사유: </span>
          {plain(result.reviewReason)}
        </p>
      )}
      <div className="border bg-white px-5 py-4">
        {summaryText && <p className="leading-relaxed">{plain(summaryText)}</p>}
        <p className={cn('text-xs text-muted-foreground', summaryText && 'mt-2')}>
          원문 인식 점검 {VERDICT_LABELS[detail.parseCheckVerdict ?? ''] ?? '-'} · 추출 결과 점검{' '}
          {VERDICT_LABELS[detail.extractCheckVerdict ?? ''] ?? '-'} · 워크플로 설정{' '}
          {detail.studioConfigId} · 이 결과는 자체 점검 기준에 따른 참고 정보이며 법적 판단이
          아닙니다.
        </p>
      </div>
    </section>
  )
}

function SummaryTile({
  label,
  value,
  note,
  className,
}: {
  label: string
  value: string
  note?: string
  className: string
}) {
  return (
    <div className={cn('flex min-h-28 flex-col justify-center gap-0.5 px-5 py-4', className)}>
      <span className="text-sm font-semibold opacity-90">{label}</span>
      <span className="text-3xl font-bold tracking-tight">{value}</span>
      {note && <span className="text-xs opacity-90">{note}</span>}
    </div>
  )
}
