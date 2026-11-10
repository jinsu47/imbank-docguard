import { useQuery } from '@tanstack/react-query'
import { analysisApi } from '@/api/endpoints'
import { PageBand, PageBody } from '@/components/PageBand'
import { Skeleton } from '@/components/ui/skeleton'
import { AnalysisTable } from '@/features/analysis/AnalysisTable'
import { isInProgress } from '@/features/analysis/labels'
import { UploadCard } from '@/features/analysis/UploadCard'

// 내 분석 목록 (F07).
export default function MyAnalysesPage() {
  const list = useQuery({
    queryKey: ['analyses'],
    queryFn: analysisApi.list,
    // 진행 중인 분석이 있으면 목록도 2초마다 갱신합니다.
    refetchInterval: (query) =>
      query.state.data?.some((a) => isInProgress(a.status)) ? 2000 : false,
  })

  return (
    <>
      <PageBand title="내 분석" description="올린 문서의 진행 상태와 위험 등급을 확인합니다." />
      <PageBody>
        <UploadCard />
        {list.isPending ? (
          <Skeleton className="h-40 w-full" />
        ) : list.isError ? (
          <p className="text-sm text-destructive">목록을 불러오지 못했습니다.</p>
        ) : (
          <AnalysisTable
            items={list.data}
            emptyText="아직 올린 문서가 없습니다. 위에서 문서를 올려 시험해 보세요."
          />
        )}
      </PageBody>
    </>
  )
}
