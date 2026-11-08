import { Link } from 'react-router'
import type { AnalysisSummary } from '@/api/types'
import { GradeBadge, ReplayBadge, ReviewBadge } from './GradeBadge'
import { formatDateTime } from './labels'

/** 분석 목록 표 (F07, F08). 파일 이름은 서버가 준 경우(내 목록)에만 보여 줍니다. */
export function AnalysisTable({
  items,
  emptyText,
}: {
  items: AnalysisSummary[]
  emptyText: string
}) {
  if (items.length === 0) {
    return (
      <div className="border border-dashed bg-muted p-12 text-center text-sm text-muted-foreground">
        {emptyText}
      </div>
    )
  }
  return (
    <div className="overflow-x-auto bg-white">
      <table className="dg-table">
        <thead>
          <tr>
            <th>문서</th>
            <th>올린 시각</th>
            <th>결과</th>
            <th>검토</th>
            <th>발견 항목</th>
          </tr>
        </thead>
        <tbody>
          {items.map((a) => (
            <tr key={a.id}>
              <td>
                <Link
                  to={`/analyses/${a.id}`}
                  className="font-semibold underline-offset-4 hover:underline"
                >
                  {a.fileName ?? `분석 ${a.id.slice(0, 8)}`}
                </Link>
                <div className="mt-1 flex gap-1 text-xs text-muted-foreground">
                  {a.documentType && <span>{a.documentType}</span>}
                  {a.pageCount != null && <span>· {a.pageCount}쪽</span>}
                  <ReplayBadge analysis={a} />
                </div>
              </td>
              <td className="whitespace-nowrap text-muted-foreground">
                {formatDateTime(a.createdAt)}
              </td>
              <td>
                <GradeBadge analysis={a} />
              </td>
              <td>
                <ReviewBadge analysis={a} />
              </td>
              <td className="text-muted-foreground">{a.findingCount ?? '-'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
