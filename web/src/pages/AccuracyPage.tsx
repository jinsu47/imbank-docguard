import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { demoApi } from '@/api/endpoints'
import type { AccuracyRow } from '@/api/types'
import { PageBand, PageBody } from '@/components/PageBand'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDateTime } from '@/features/analysis/labels'
import { cn } from '@/lib/utils'

// 시연 정확도 (F12, US08). 내가 웹에서 실행한 평가 문서 결과를 정답표와 나란히 보여 줍니다.
// 판정을 다시 내리지 않고 저장된 Studio 판정을 비교만 합니다. 재생 모드 결과는 빠집니다.
export default function AccuracyPage() {
  const report = useQuery({ queryKey: ['demo', 'accuracy'], queryFn: demoApi.accuracy })

  return (
    <>
      <PageBand
        title="시연 정확도"
        description="평가 문서(파일 이름에 set01~set10 포함)를 올려 실행하면, 문서마다 가장 최근 결과를 정답표와 비교합니다."
      />
      <PageBody>
        {report.isPending ? (
          <Skeleton className="h-60 w-full" />
        ) : report.isError ? (
          <p className="text-sm text-destructive">결과를 불러오지 못했습니다.</p>
        ) : !report.data.available ? (
          <Alert>
            <AlertDescription>{report.data.message}</AlertDescription>
          </Alert>
        ) : (
          <>
            <section className="grid grid-cols-3">
              <Stat
                tone="bg-tile-blue text-white"
                label="평가 문서"
                value={`${report.data.total}건`}
              />
              <Stat
                tone="bg-tile-sky text-[#17324a]"
                label="웹에서 실행"
                value={`${report.data.executed}건`}
              />
              <Stat
                label="정답과 일치"
                value={`${report.data.matched} / ${report.data.executed}`}
                highlight={report.data.executed > 0 && report.data.matched === report.data.executed}
              />
            </section>
            <div className="overflow-x-auto bg-white">
              <table className="dg-table">
                <thead>
                  <tr>
                    <th>문서</th>
                    <th>정답 (등급 · 사람 검토)</th>
                    <th>웹 실행 결과</th>
                    <th>일치</th>
                    <th>실행 시각 · 걸린 시간</th>
                  </tr>
                </thead>
                <tbody>
                  {report.data.rows.map((row) => (
                    <AccuracyTableRow key={row.tag} row={row} />
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}
      </PageBody>
    </>
  )
}

function AccuracyTableRow({ row }: { row: AccuracyRow }) {
  return (
    <tr>
      <td>
        <div className="font-medium">{row.tag}</div>
        <div className="text-xs text-muted-foreground">{row.title}</div>
      </td>
      <td>
        {row.expectedGrade} · {row.expectedReview}
      </td>
      <td>
        {row.analysisId ? (
          <Link to={`/analyses/${row.analysisId}`} className="underline-offset-4 hover:underline">
            <span className={cn(row.gradeMatch === false && 'font-semibold text-destructive')}>
              {row.actualGrade}
            </span>{' '}
            ·{' '}
            <span className={cn(row.reviewMatch === false && 'font-semibold text-destructive')}>
              {row.actualReview}
            </span>
          </Link>
        ) : (
          <span className="text-muted-foreground">아직 실행하지 않음</span>
        )}
      </td>
      <td>
        {row.match === undefined ? (
          '-'
        ) : row.match ? (
          <span className="font-bold text-tile-teal">일치</span>
        ) : (
          <span className="font-bold text-destructive">불일치</span>
        )}
      </td>
      <td className="text-muted-foreground">
        {row.completedAt ? formatDateTime(row.completedAt) : '-'}
        {row.elapsedSeconds != null && ` · ${row.elapsedSeconds}초`}
      </td>
    </tr>
  )
}

function Stat({
  label,
  value,
  highlight,
  tone,
}: {
  label: string
  value: string
  highlight?: boolean
  tone?: string
}) {
  return (
    <div
      className={cn(
        'flex min-h-28 flex-col justify-center px-5 py-4',
        highlight ? 'bg-tile-teal text-white' : (tone ?? 'bg-tile-gray text-foreground'),
      )}
    >
      <div className="text-sm font-semibold opacity-90">{label}</div>
      <div className="mt-1 text-3xl font-bold tracking-tight tabular-nums">{value}</div>
    </div>
  )
}
