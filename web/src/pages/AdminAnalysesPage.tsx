import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router'
import { ApiError } from '@/api/client'
import { adminApi, analysisApi } from '@/api/endpoints'
import type { AdminAnalysis } from '@/api/types'
import { PageBand, PageBody } from '@/components/PageBand'
import { Alert, AlertDescription } from '@/components/ui/alert'
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

// 분석 현황 (BR11). 관리자는 모든 사용자의 분석을 열어 보고 지울 수 있습니다.
export default function AdminAnalysesPage() {
  const queryClient = useQueryClient()
  const list = useQuery({ queryKey: ['admin', 'analyses'], queryFn: adminApi.analyses })
  const remove = useMutation({
    mutationFn: (id: string) => analysisApi.remove(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin'] })
      queryClient.invalidateQueries({ queryKey: ['analyses'] })
      queryClient.invalidateQueries({ queryKey: ['reviews'] })
    },
  })

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
        {list.isPending ? (
          <Skeleton className="h-40 w-full" />
        ) : list.isError ? (
          <p className="text-sm text-destructive">목록을 불러오지 못했습니다.</p>
        ) : list.data.length === 0 ? (
          <div className="border border-dashed bg-muted p-12 text-center text-sm text-muted-foreground">
            아직 분석이 없습니다.
          </div>
        ) : (
          <div className="overflow-x-auto bg-white">
            <table className="dg-table">
              <thead>
                <tr>
                  <th>문서</th>
                  <th>올린 사람</th>
                  <th>상태</th>
                  <th>등급</th>
                  <th>검토</th>
                  <th>올린 시각</th>
                  <th>보관 기한</th>
                  <th>관리</th>
                </tr>
              </thead>
              <tbody>
                {list.data.map((a) => (
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
                      {a.riskGrade ? GRADE_LABELS[a.riskGrade] : '-'}
                    </td>
                    <td className="whitespace-nowrap">
                      {a.status === 'COMPLETED' ? REVIEW_LABELS[a.reviewStatus] : '-'}
                    </td>
                    <td className="whitespace-nowrap text-muted-foreground">
                      {formatDateTime(a.createdAt)}
                    </td>
                    <td className="whitespace-nowrap text-muted-foreground">
                      {a.purgedAt ? '정리됨' : formatDateTime(a.expiresAt)}
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
        )}
      </PageBody>
    </>
  )
}
