import { useQuery } from '@tanstack/react-query'
import { reviewApi } from '@/api/endpoints'
import { PageBand, PageBody } from '@/components/PageBand'
import { Skeleton } from '@/components/ui/skeleton'
import { AnalysisTable } from '@/features/analysis/AnalysisTable'

// 검토 대기열 (F08). 자기가 올린 문서는 서버가 빼고 줍니다(BR13).
export default function ReviewsPage() {
  const queue = useQuery({ queryKey: ['reviews', 'queue'], queryFn: reviewApi.queue })
  return (
    <>
      <PageBand
        title="검토 대기열"
        description="자동 판정이 사람 검토를 요청한 문서입니다. 검토는 Studio 판정을 바꾸지 않고 의견으로 남습니다."
      />
      <PageBody>
        {queue.isPending ? (
          <Skeleton className="h-40 w-full" />
        ) : queue.isError ? (
          <p className="text-sm text-destructive">대기열을 불러오지 못했습니다.</p>
        ) : (
          <AnalysisTable items={queue.data} emptyText="검토할 문서가 없습니다." />
        )}
      </PageBody>
    </>
  )
}
