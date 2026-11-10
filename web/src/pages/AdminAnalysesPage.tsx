import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router'
import { ApiError } from '@/api/client'
import { adminApi, analysisApi } from '@/api/endpoints'
import type { AdminAnalysis } from '@/api/types'
import { ListPagination } from '@/components/ListPagination'
import { PageBand, PageBody } from '@/components/PageBand'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import {
  formatDateTime,
  GRADE_LABELS,
  isInProgress,
  REVIEW_LABELS,
  STATUS_LABELS,
} from '@/features/analysis/labels'
import { FilterToggle, ListFilterBar, ListFilterEmpty } from '@/features/analysis/ListFilterBar'
import { reviewOf, useListFilter } from '@/features/analysis/listFilter'
import { usePagedList } from '@/lib/pagination'

/** 올린 뒤 지난 날수 */
function daysSince(iso: string): number {
  return Math.floor((Date.now() - new Date(iso).getTime()) / 86_400_000)
}

// 분석 현황 (BR11, BR15). 관리자는 모든 사용자의 분석을 열어 보고 지울 수 있고, 보관 기간이 지난 문서는 삭제를 권고합니다.
export default function AdminAnalysesPage() {
  const queryClient = useQueryClient()
  const list = useQuery({ queryKey: ['admin', 'analyses'], queryFn: adminApi.analyses })
  const [params, setParams] = useSearchParams()
  const all = list.data ?? []
  const recommended = all.filter((a) => a.deletionRecommended)
  // 권고 문서를 모두 지우면 빈 화면에 갇히지 않도록 전체 목록으로 돌아갑니다.
  const onlyRecommended = params.get('recommend') === '1' && recommended.length > 0
  const scoped = onlyRecommended ? recommended : all
  const filter = useListFilter(scoped)
  const { page, totalPages, pageItems, goTo, listRef, scrollToList } = usePagedList(filter.visible)
  const remove = useMutation({
    mutationFn: (id: string) => analysisApi.remove(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin'] })
      queryClient.invalidateQueries({ queryKey: ['analyses'] })
      queryClient.invalidateQueries({ queryKey: ['reviews'] })
    },
  })

  const toggleRecommended = () => {
    setParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        if (onlyRecommended) {
          next.delete('recommend')
        } else {
          next.set('recommend', '1')
        }
        next.delete('page')
        return next
      },
      { replace: true },
    )
  }

  const onDelete = (a: AdminAnalysis) => {
    if (
      window.confirm(
        `${a.fileName}\n원본 문서와 분석 결과를 지웁니다. 되돌릴 수 없습니다. 지울까요?`,
      )
    ) {
      remove.mutate(a.id)
    }
  }

  return (
    <>
      <PageBand
        title="분석 현황"
        description="모든 사용자의 분석입니다. 다른 사람 문서의 원본 열람, 원문 보기, 삭제는 감사 기록에 남습니다."
      />
      <PageBody wide>
        {remove.isError && (
          <Alert variant="destructive">
            <AlertDescription>
              {remove.error instanceof ApiError ? remove.error.message : '지우지 못했습니다.'}
            </AlertDescription>
          </Alert>
        )}
        {recommended.length > 0 && (
          <Alert>
            <AlertTitle>삭제 권고 {recommended.length}건</AlertTitle>
            <AlertDescription>
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span>
                  보관 기간(기본 7일)이 지난 문서입니다. 자동으로 지워지지 않으니 내용을 확인한 뒤
                  삭제해 주세요.
                </span>
                <Button
                  size="sm"
                  variant="outline"
                  aria-pressed={onlyRecommended}
                  onClick={toggleRecommended}
                >
                  {onlyRecommended ? '전체 문서 보기' : '권고 문서만 보기'}
                </Button>
              </div>
            </AlertDescription>
          </Alert>
        )}
        {list.isPending ? (
          <Skeleton className="h-40 w-full" />
        ) : list.isError ? (
          <p className="text-sm text-destructive">목록을 불러오지 못했습니다.</p>
        ) : list.data.length === 0 ? (
          <div className="border border-dashed bg-muted p-12 text-center text-sm text-muted-foreground">
            아직 분석이 없습니다.
          </div>
        ) : (
          <div ref={listRef} className="grid min-w-0 scroll-mt-24 gap-4">
            <ListFilterBar
              filter={filter.controls}
              shown={filter.visible.length}
              total={scoped.length}
              placeholder="파일 이름이나 올린 사람으로 검색"
            />
            {filter.visible.length === 0 && <ListFilterEmpty />}
            <div className={filter.visible.length === 0 ? 'hidden' : 'overflow-x-auto bg-white'}>
              <table className="dg-table">
                <thead>
                  <tr>
                    <th>문서</th>
                    <th>올린 사람</th>
                    <th>상태</th>
                    <th>등급</th>
                    <th>검토</th>
                    <th>올린 시각</th>
                    <th>삭제 권고일</th>
                    <th>관리</th>
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
                          {a.fileName}
                        </Link>
                        {a.replay && (
                          <Badge variant="outline" className="ml-2">
                            재생 모드
                          </Badge>
                        )}
                      </td>
                      <td>{a.ownerEmail ?? a.ownerId}</td>
                      <td className="whitespace-nowrap">
                        {STATUS_LABELS[a.status]}
                        {a.errorCode && (
                          <span className="ml-1 text-xs text-destructive">{a.errorCode}</span>
                        )}
                      </td>
                      <td className="whitespace-nowrap">
                        <FilterToggle {...filter.gradeToggle(a, scrollToList)}>
                          {a.riskGrade ? GRADE_LABELS[a.riskGrade] : '-'}
                        </FilterToggle>
                      </td>
                      <td className="whitespace-nowrap">
                        <FilterToggle {...filter.reviewToggle(a, scrollToList)}>
                          {reviewOf(a) ? REVIEW_LABELS[a.reviewStatus] : '-'}
                        </FilterToggle>
                      </td>
                      <td className="whitespace-nowrap text-muted-foreground">
                        {formatDateTime(a.createdAt)}
                      </td>
                      <td className="whitespace-nowrap text-muted-foreground">
                        {a.purgedAt ? (
                          '정리됨'
                        ) : a.deletionRecommended ? (
                          <span className="flex items-center gap-2">
                            <Badge variant="destructive">삭제 권고</Badge>
                            올린 지 {daysSince(a.createdAt)}일
                          </span>
                        ) : (
                          formatDateTime(a.expiresAt)
                        )}
                      </td>
                      <td>
                        <Button
                          size="sm"
                          variant="outline"
                          aria-label={`${a.fileName} 삭제`}
                          title={
                            isInProgress(a.status)
                              ? '진행 중인 분석은 끝난 뒤 지울 수 있습니다.'
                              : undefined
                          }
                          disabled={isInProgress(a.status) || remove.isPending}
                          onClick={() => onDelete(a)}
                        >
                          삭제
                        </Button>
                      </td>
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
          </div>
        )}
      </PageBody>
    </>
  )
}
