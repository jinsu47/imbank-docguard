import { Link } from 'react-router'
import type { AnalysisSummary } from '@/api/types'
import { ListPagination } from '@/components/ListPagination'
import { usePagedList } from '@/lib/pagination'
import { GradeBadge, ReplayBadge, ReviewBadge } from './GradeBadge'
import { formatDateTime } from './labels'
import { FilterToggle, ListFilterBar, ListFilterEmpty } from './ListFilterBar'
import { useListFilter } from './listFilter'

/**
 * 분석 목록 표 (F07, F08). 파일 이름은 서버가 준 경우(내 목록)에만 보여 줍니다. 15건을 넘으면 쪽으로 나눕니다.
 * 검색과, 등급·검토 상태를 눌러 같은 항목을 위로 올리는 기능은 서버가 준 목록 안에서 화면이 처리합니다.
 */
export function AnalysisTable({
  items,
  emptyText,
}: {
  items: AnalysisSummary[]
  emptyText: string
}) {
  const filter = useListFilter(items)
  const { page, totalPages, pageItems, goTo, listRef, scrollToList } = usePagedList(filter.visible)
  if (items.length === 0) {
    return (
      <div className="border border-dashed bg-muted p-12 text-center text-sm text-muted-foreground">
        {emptyText}
      </div>
    )
  }
  return (
    <div ref={listRef} className="grid min-w-0 scroll-mt-24 gap-4">
      <ListFilterBar
        filter={filter.controls}
        shown={filter.visible.length}
        total={items.length}
        placeholder={
          items.some((a) => a.fileName) ? '파일 이름이나 문서 종류로 검색' : '문서 종류로 검색'
        }
      />
      {filter.visible.length === 0 ? (
        <ListFilterEmpty />
      ) : (
        <>
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
                {pageItems.map((a) => (
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
                      <FilterToggle {...filter.gradeToggle(a, scrollToList)}>
                        <GradeBadge analysis={a} />
                      </FilterToggle>
                    </td>
                    <td>
                      <FilterToggle {...filter.reviewToggle(a, scrollToList)}>
                        <ReviewBadge analysis={a} />
                      </FilterToggle>
                    </td>
                    <td className="text-muted-foreground">{a.findingCount ?? '-'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <ListPagination
            page={page}
            totalPages={totalPages}
            total={filter.visible.length}
            onChange={goTo}
          />
        </>
      )}
    </div>
  )
}
