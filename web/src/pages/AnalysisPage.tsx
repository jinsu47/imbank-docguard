import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { ApiError } from '@/api/client'
import { analysisApi } from '@/api/endpoints'
import type { AnalysisDetail, RevealedFinding } from '@/api/types'
import { FullPageMessage } from '@/components/FullPageMessage'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { AnalysisProgress } from '@/features/analysis/AnalysisProgress'
import { FindingList } from '@/features/analysis/FindingList'
import { errorMessage, isInProgress } from '@/features/analysis/labels'
import { ResultSummary } from '@/features/analysis/ResultSummary'
import { RevealDialog } from '@/features/analysis/RevealDialog'
import { ReviewPanel } from '@/features/analysis/ReviewPanel'
import { ReportDownload } from '@/features/report/ReportDownload'
import { ReportView } from '@/features/report/ReportView'
import { DocumentViewer, type Focus } from '@/features/viewer/DocumentViewer'
import { boxesOf } from '@/features/viewer/boxes'

// 분석 화면 (F02~F06, F08). 진행 중이면 진행 표시, 완료면 문서 뷰어·발견 항목·보고서 세 영역을 보여 줍니다.
export default function AnalysisPage() {
  const { id = '' } = useParams()
  const detail = useQuery({
    queryKey: ['analysis', id],
    queryFn: () => analysisApi.detail(id),
    refetchInterval: (query) =>
      query.state.data && isInProgress(query.state.data.summary.status) ? 2000 : false,
  })

  if (detail.isPending) {
    return (
      <main className="mx-auto max-w-6xl px-4 py-8">
        <Skeleton className="h-32 w-full" />
      </main>
    )
  }
  if (detail.isError) {
    const notFound = detail.error instanceof ApiError && detail.error.status === 404
    return (
      <FullPageMessage
        title={notFound ? '분석을 찾을 수 없습니다.' : '분석을 불러오지 못했습니다.'}
        description={
          notFound ? '지워졌거나 볼 수 있는 권한이 없는 분석입니다.' : '잠시 후 다시 시도해 주세요.'
        }
      >
        <Button variant="outline" asChild>
          <Link to="/analyses">내 분석 목록으로</Link>
        </Button>
      </FullPageMessage>
    )
  }

  const d = detail.data
  return (
    <main className="mx-auto grid max-w-[1600px] gap-5 px-4 py-6 lg:px-8">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <Link
            to={d.owner || !d.canManage ? '/analyses' : '/admin/analyses'}
            className="text-sm font-medium text-muted-foreground hover:underline"
          >
            ← {d.owner || !d.canManage ? '내 분석' : '분석 현황'}
          </Link>
          <h1 className="mt-1 text-2xl font-bold tracking-tight break-all">
            {d.summary.fileName ?? `분석 ${d.summary.id.slice(0, 8)}`}
          </h1>
        </div>
        <OwnerActions detail={d} />
      </header>
      {isInProgress(d.summary.status) && <AnalysisProgress detail={d} />}
      {d.summary.status === 'FAILED' && (
        <Alert variant="destructive">
          <AlertTitle>분석에 실패했습니다</AlertTitle>
          <AlertDescription>
            {errorMessage(d.summary.errorCode)} (오류 코드 {d.summary.errorCode ?? '-'})
          </AlertDescription>
        </Alert>
      )}
      {d.summary.status === 'PURGED' && (
        <Alert>
          <AlertTitle>보관 기간이 지났습니다</AlertTitle>
          <AlertDescription>
            보관 기간이 지나 원본과 상세 결과를 삭제했습니다. 등급과 상태만 남아 있습니다.
          </AlertDescription>
        </Alert>
      )}
      {d.summary.status === 'COMPLETED' && <CompletedView detail={d} />}
    </main>
  )
}

function OwnerActions({ detail }: { detail: AnalysisDetail }) {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const id = detail.summary.id
  const retry = useMutation({
    mutationFn: () => analysisApi.retry(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['analysis', id] }),
  })
  const remove = useMutation({
    mutationFn: () => analysisApi.remove(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['analyses'] })
      queryClient.invalidateQueries({ queryKey: ['admin'] })
      navigate(detail.owner ? '/analyses' : '/admin/analyses', { replace: true })
    },
  })
  if (!detail.canManage || isInProgress(detail.summary.status)) return null
  const error = (retry.error ?? remove.error) as Error | null
  return (
    <div className="flex items-center gap-2">
      {error && <span className="text-sm text-destructive">{error.message}</span>}
      {detail.summary.status === 'FAILED' && (
        <Button onClick={() => retry.mutate()} disabled={retry.isPending}>
          다시 실행
        </Button>
      )}
      <Button
        variant="outline"
        disabled={remove.isPending}
        onClick={() => {
          if (window.confirm('원본 문서와 분석 결과를 지웁니다. 되돌릴 수 없습니다. 지울까요?'))
            remove.mutate()
        }}
      >
        삭제
      </Button>
    </div>
  )
}

function CompletedView({ detail }: { detail: AnalysisDetail }) {
  const id = detail.summary.id
  const result = useQuery({
    queryKey: ['analysis-result', id],
    queryFn: () => analysisApi.result(id),
  })
  const [selectedRow, setSelectedRow] = useState<number>()
  const [focus, setFocus] = useState<Focus>()
  const [activeCitation, setActiveCitation] = useState<number>()
  const [revealOpen, setRevealOpen] = useState(false)
  const [revealed, setRevealed] = useState<Map<number, RevealedFinding>>()
  const wide = useMediaQuery('(min-width: 1280px)')
  const [magnifier, setMagnifier] = useStoredFlag('docguard.magnifier', true)

  if (result.isPending) {
    return <Skeleton className="h-96 w-full" />
  }
  if (result.isError) {
    return <p className="text-sm text-destructive">결과를 불러오지 못했습니다.</p>
  }
  const r = result.data

  /** 확대를 풀고 문서 전체 보기로 돌아갑니다. */
  const resetView = () => {
    setSelectedRow(undefined)
    setActiveCitation(undefined)
    setFocus(undefined)
  }
  // 항목을 고르면 그 위치를 확대하고, 같은 항목을 한 번 더 누르면 전체 보기로 돌아갑니다.
  const selectRow = (rowIndex: number) => {
    if (rowIndex === selectedRow && activeCitation === undefined && focus?.zoom) {
      resetView()
      return
    }
    setSelectedRow(rowIndex)
    setActiveCitation(undefined)
    const finding = r.findings.find((f) => f.rowIndex === rowIndex)
    if (finding?.page != null) {
      setFocus({ page: finding.page, boxes: boxesOf(finding), zoom: true, nonce: Date.now() })
    } else {
      setFocus(undefined)
    }
  }
  const selectCitation = (index: number) => {
    if (index === activeCitation && focus?.zoom) {
      resetView()
      return
    }
    setActiveCitation(index)
    const citation = r.citations.find((c) => c.index === index)
    if (!citation) return
    setSelectedRow(citation.rowIndex ?? undefined)
    if (citation.page != null) {
      setFocus({ page: citation.page, boxes: boxesOf(citation), zoom: true, nonce: Date.now() })
    }
  }

  const viewer = (
    <div className="grid gap-2">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-base font-bold">원본 문서</h2>
        <div className="flex items-center gap-2">
          {focus?.zoom && (
            <Button size="sm" variant="outline" onClick={resetView}>
              전체 보기
            </Button>
          )}
          <Button
            size="sm"
            variant={magnifier ? 'secondary' : 'outline'}
            aria-pressed={magnifier}
            title="마우스를 올린 자리를 크게 보여 줍니다."
            onClick={() => setMagnifier(!magnifier)}
          >
            돋보기 {magnifier ? '켜짐' : '꺼짐'}
          </Button>
          {revealed ? (
            <Button size="sm" variant="outline" onClick={() => setRevealed(undefined)}>
              다시 가리기
            </Button>
          ) : (
            <Button size="sm" variant="outline" onClick={() => setRevealOpen(true)}>
              원문 보기
            </Button>
          )}
        </div>
      </div>
      <DocumentViewer
        analysisId={id}
        contentType={detail.contentType}
        findings={r.findings}
        revealed={revealed != null}
        selectedRow={selectedRow}
        focus={focus}
        magnifier={magnifier}
        onSelectRow={selectRow}
      />
      <p className="text-xs text-muted-foreground">
        항목이나 근거 번호를 누르면 그 위치를 확대합니다. 같은 것을 한 번 더 누르면 전체 보기로
        돌아갑니다.
      </p>
    </div>
  )
  const findings = (
    <div className="grid content-start gap-2">
      <h2 className="text-base font-bold">발견 항목 {r.findings.length}건</h2>
      <FindingList
        findings={r.findings}
        revealed={revealed}
        selectedRow={selectedRow}
        onSelect={selectRow}
      />
    </div>
  )
  const report = (
    <div className="grid content-start gap-4">
      <ReviewPanel detail={detail} />
      <div className="border bg-white p-5">
        <div className="mb-4 flex flex-wrap items-start justify-between gap-2">
          <h2 className="text-base font-bold">분석 보고서</h2>
          <ReportDownload
            analysisId={id}
            fileName={detail.summary.fileName}
            contentType={detail.contentType}
          />
        </div>
        <ReportView
          sections={r.sections}
          onCitation={selectCitation}
          activeCitation={activeCitation}
        />
      </div>
    </div>
  )

  return (
    <>
      <ResultSummary detail={detail} result={r} />
      {wide ? (
        <div className="grid grid-cols-[minmax(0,5fr)_minmax(0,3fr)_minmax(0,4fr)] items-start gap-4">
          {/* 문서 칸은 DocumentViewer 가 자기 안에서 스크롤합니다(확대 시 좌우 포함). */}
          <div className="sticky top-[calc(var(--header-h)+1rem)] min-w-0">{viewer}</div>
          <div className="sticky top-[calc(var(--header-h)+1rem)] max-h-[calc(100svh-var(--header-h)-2rem)] min-w-0 overflow-x-hidden overflow-y-auto pr-1 [scrollbar-gutter:stable]">
            {findings}
          </div>
          <div className="min-w-0">{report}</div>
        </div>
      ) : (
        <div className="grid gap-4">
          <Tabs defaultValue="findings">
            <TabsList>
              <TabsTrigger value="findings">발견 항목</TabsTrigger>
              <TabsTrigger value="report">보고서</TabsTrigger>
            </TabsList>
            <TabsContent value="findings">{findings}</TabsContent>
            <TabsContent value="report">{report}</TabsContent>
          </Tabs>
          {viewer}
        </div>
      )}
      <RevealDialog
        analysisId={id}
        open={revealOpen}
        onOpenChange={setRevealOpen}
        onRevealed={(values) => setRevealed(new Map(values.map((v) => [v.rowIndex, v])))}
      />
    </>
  )
}

function useMediaQuery(query: string): boolean {
  const [matches, setMatches] = useState(() => window.matchMedia?.(query).matches ?? false)
  useEffect(() => {
    const media = window.matchMedia?.(query)
    if (!media) return
    const onChange = () => setMatches(media.matches)
    media.addEventListener('change', onChange)
    return () => media.removeEventListener('change', onChange)
  }, [query])
  return matches
}

/** 브라우저에 기억하는 켜기·끄기 설정(돋보기 등). 저장소를 못 쓰면 기본값으로 동작합니다. */
function useStoredFlag(key: string, initial: boolean): [boolean, (value: boolean) => void] {
  const [value, setValue] = useState(() => {
    try {
      const stored = window.localStorage.getItem(key)
      return stored === null ? initial : stored === 'true'
    } catch {
      return initial
    }
  })
  const update = (next: boolean) => {
    setValue(next)
    try {
      window.localStorage.setItem(key, String(next))
    } catch {
      // 저장하지 못해도 이번 화면에서는 동작합니다.
    }
  }
  return [value, update]
}
