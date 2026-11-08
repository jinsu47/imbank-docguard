import { useQuery } from '@tanstack/react-query'
import {
  memo,
  type PointerEvent as ReactPointerEvent,
  Suspense,
  useCallback,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
} from 'react'
import { Document, Page, pdfjs } from 'react-pdf'
import { analysisApi } from '@/api/endpoints'
import type { FindingView, Point } from '@/api/types'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'
import { markKind, toPercentRect, zoomScaleFor } from './boxes'

pdfjs.GlobalWorkerOptions.workerSrc = new URL(
  'pdfjs-dist/build/pdf.worker.min.mjs',
  import.meta.url,
).toString()

export interface Focus {
  page: number
  boxes: Point[][]
  /** true 면 이 위치가 크게 보이도록 확대합니다(발견 항목·인용을 골랐을 때). */
  zoom: boolean
  /** 같은 위치를 다시 골라도 다시 스크롤하도록 바꾸는 값 */
  nonce: number
}

/** 돋보기 배율(현재 보이는 크기 기준)과 크기(px) */
const LOUPE_ZOOM = 1.5
const LOUPE_SIZE = 240

/** 문서 칸에서 이 거리(px) 안으로 들어온 쪽까지 미리 그립니다. */
const RENDER_MARGIN = 1200
/** 크기를 읽지 못한 쪽에 쓰는 A4 비율(가로/세로) */
const A4_RATIO = 1 / 1.414
const NO_FINDINGS: FindingView[] = []

interface Hover {
  page: number
  /** 페이지 안 상대 위치(0~1) */
  x: number
  y: number
  /** 지금 화면에 그려진 페이지 크기(px) */
  width: number
  height: number
  /** 돋보기에 복사할 화면의 페이지 그림(PDF 캔버스 또는 이미지) */
  source: HTMLCanvasElement | HTMLImageElement
}

/**
 * 문서 뷰어 (F04). 원본 위에 발견 위치를 겹쳐 그립니다.
 * - 기본은 불투명한 가림 상자이고, 원문 보기를 켜면 테두리만 남깁니다(BR10).
 * - 좌표는 0~1 상대값이라 백분율로 배치하므로 화면 크기·확대 배율이 바뀌어도 맞습니다.
 * - 돋보기: 마우스 위치 주변을 확대해 보여 줍니다. 돋보기 안에도 가림 상자를 그대로 그려 가린 값이 드러나지 않게 합니다.
 * - 확대: focus.zoom 이면 고른 위치가 칸에 크게 보이도록 페이지를 키우고 그 위치로 스크롤합니다.
 * - 긴 문서: 쪽 크기만 먼저 읽어 자리를 잡고, 화면 가까이 온 쪽만 PDF 를 그립니다(LazyPdfPage).
 */
export function DocumentViewer({
  analysisId,
  contentType,
  findings,
  revealed,
  selectedRow,
  focus,
  magnifier,
  onSelectRow,
}: {
  analysisId: string
  contentType: string
  findings: FindingView[]
  revealed: boolean
  selectedRow?: number
  focus?: Focus
  magnifier: boolean
  onSelectRow: (rowIndex: number) => void
}) {
  const file = useQuery({
    queryKey: ['analysis-file', analysisId],
    queryFn: () => analysisApi.file(analysisId),
    staleTime: Infinity,
    gcTime: 0,
  })
  const scrollRef = useRef<HTMLDivElement>(null)
  const renderWidth = useRenderWidth(scrollRef)
  const [pageRatios, setPageRatios] = useState<number[]>([])
  const pageCount = pageRatios.length
  const [hover, setHover] = useState<Hover>()

  const scale = focus?.zoom ? zoomScaleFor(focus.boxes) : 1

  // 마우스가 움직일 때마다 화면을 다시 계산하지 않도록, 쪽별 표시(Marks)에 바뀌지 않는 값을 넘깁니다.
  const findingsByPage = useMemo(() => {
    const map = new Map<number, FindingView[]>()
    for (const finding of findings) {
      if (finding.page == null) continue
      const onPage = map.get(finding.page)
      if (onPage) onPage.push(finding)
      else map.set(finding.page, [finding])
    }
    return map
  }, [findings])
  const selectRowRef = useRef(onSelectRow)
  useLayoutEffect(() => {
    selectRowRef.current = onSelectRow
  })
  const selectRow = useCallback((rowIndex: number) => selectRowRef.current(rowIndex), [])

  const imageUrl = useMemo(
    () => (file.data && contentType.startsWith('image/') ? URL.createObjectURL(file.data) : null),
    [file.data, contentType],
  )
  useEffect(
    () => () => {
      if (imageUrl) URL.revokeObjectURL(imageUrl)
    },
    [imageUrl],
  )

  // 고른 위치를 칸 가운데로 옮깁니다. 레이아웃 효과 안이라 확대 배율이 바뀐 뒤의 배치로 바로 잴 수 있습니다.
  // 배율이 바뀔 때는 화면이 한 번에 바뀌므로 바로 옮기고, 같은 배율에서 다른 항목으로 갈 때만 부드럽게 옮깁니다.
  const lastScale = useRef(scale)
  const lastFocusPage = useRef<number>(undefined)
  useLayoutEffect(() => {
    const box = scrollRef.current
    if (!box) return
    const behavior: ScrollBehavior = lastScale.current === scale ? 'smooth' : 'auto'
    lastScale.current = scale
    if (focus) lastFocusPage.current = focus.page
    const target = box.querySelector<HTMLElement>('[data-focus="true"], [data-selected="true"]')
    if (!target) {
      if (!focus) {
        // 확대를 풀면 방금 보던 항목이 있는 쪽에 머뭅니다. 고른 적이 없을 때만 맨 위입니다.
        const page = lastFocusPage.current
          ? box.querySelectorAll('.pdf-page')[lastFocusPage.current - 1]
          : undefined
        const top = page
          ? box.scrollTop + page.getBoundingClientRect().top - box.getBoundingClientRect().top
          : 0
        box.scrollTo({ left: 0, top, behavior })
      }
      return
    }
    const t = target.getBoundingClientRect()
    const b = box.getBoundingClientRect()
    box.scrollTo({
      left: box.scrollLeft + (t.left + t.width / 2) - (b.left + b.width / 2),
      top: box.scrollTop + (t.top + t.height / 2) - (b.top + b.height / 2),
      behavior,
    })
    // 문서 칸 아래쪽이 창 밖에 있으면 고른 위치가 보이도록 페이지도 함께 내립니다.
    const overflow = b.bottom - window.innerHeight + 16
    if (overflow > 0) window.scrollBy({ top: overflow, behavior })
  }, [selectedRow, focus?.nonce, scale, focus])

  const onPointerMove = (page: number) => (event: ReactPointerEvent<HTMLDivElement>) => {
    if (!magnifier || event.pointerType !== 'mouse') return
    const source = event.currentTarget.querySelector<HTMLCanvasElement | HTMLImageElement>(
      'canvas, img',
    )
    if (!source) return
    const rect = event.currentTarget.getBoundingClientRect()
    setHover({
      source,
      page,
      x: (event.clientX - rect.left) / rect.width,
      y: (event.clientY - rect.top) / rect.height,
      width: rect.width,
      height: rect.height,
    })
  }

  const renderPage = (pageNumber: number, content: React.ReactNode, ratio?: number) => {
    const marks = {
      findings: findingsByPage.get(pageNumber) ?? NO_FINDINGS,
      revealed,
      selectedRow,
      focus: focus?.page === pageNumber ? focus : undefined,
    }
    return (
      <div
        className={cn(
          'pdf-page relative mb-4 border bg-white shadow-sm',
          magnifier && 'cursor-zoom-in',
        )}
        style={ratio ? { aspectRatio: ratio } : undefined}
        onPointerMove={onPointerMove(pageNumber)}
        onPointerLeave={() => setHover(undefined)}
      >
        {content}
        <Marks {...marks} onSelectRow={selectRow} />
        {magnifier && hover?.page === pageNumber && <Loupe hover={hover} {...marks} />}
        {pageCount > 0 && (
          <span className="absolute right-2 bottom-2 rounded bg-black/60 px-1.5 text-[11px] text-white">
            {pageNumber} / {pageCount}
          </span>
        )}
      </div>
    )
  }

  return (
    // 문서 칸은 자기 안에서 위아래·좌우로 스크롤합니다(확대했을 때 좌우 이동).
    <div
      ref={scrollRef}
      className="max-h-[calc(100svh-var(--header-h)-7rem)] min-w-0 overflow-auto overscroll-contain bg-muted [scrollbar-gutter:stable]"
    >
      <div style={{ width: `${scale * 100}%` }} className="transition-[width] duration-0">
        {file.isPending ? (
          <Skeleton className="aspect-[1/1.4] w-full" />
        ) : file.isError ? (
          <p className="text-sm text-destructive">원본 문서를 불러오지 못했습니다.</p>
        ) : imageUrl ? (
          renderPage(1, <img src={imageUrl} alt="올린 문서" className="block w-full" />)
        ) : (
          <Document
            file={file.data}
            onLoadSuccess={(doc) => void readPageRatios(doc).then(setPageRatios)}
            loading={<Skeleton className="aspect-[1/1.4] w-full" />}
            error={<p className="text-sm text-destructive">PDF 를 표시하지 못했습니다.</p>}
          >
            {pageCount === 0 && <Skeleton className="aspect-[1/1.4] w-full" />}
            {pageRatios.map((ratio, i) => (
              <div key={i}>
                {renderPage(
                  i + 1,
                  <LazyPdfPage pageNumber={i + 1} width={renderWidth} scrollRef={scrollRef} />,
                  ratio,
                )}
              </div>
            ))}
          </Document>
        )}
      </div>
    </div>
  )
}

/**
 * 화면 가까이 온 쪽만 PDF 를 그립니다. 쪽마다 고해상도 캔버스(최대 약 32MB)를 쓰므로,
 * 긴 문서를 한꺼번에 그리면 브라우저 메모리가 부족해집니다.
 * 정해진 해상도로 한 번 그리고, 화면 크기는 CSS(.pdf-page canvas)로 칸에 맞춥니다.
 */
const LazyPdfPage = memo(function LazyPdfPage({
  pageNumber,
  width,
  scrollRef,
}: {
  pageNumber: number
  width: number
  scrollRef: React.RefObject<HTMLElement | null>
}) {
  const ref = useRef<HTMLDivElement>(null)
  const [near, setNear] = useState(false)

  useEffect(() => {
    const element = ref.current
    if (!element) return
    const observer = new IntersectionObserver(
      (entries) => setNear(entries[entries.length - 1].isIntersecting),
      { root: scrollRef.current, rootMargin: `${RENDER_MARGIN}px` },
    )
    observer.observe(element)
    return () => observer.disconnect()
  }, [scrollRef])

  return (
    <div ref={ref} className="absolute inset-0">
      {near && (
        // Page 는 불러오는 동안 Suspense 로 멈춥니다. 여기서 받지 않으면 화면 전체가 대기 화면으로 바뀌고 스크롤이 초기화됩니다.
        <Suspense fallback={null}>
          <Page
            pageNumber={pageNumber}
            width={width}
            renderTextLayer={false}
            renderAnnotationLayer={false}
          />
        </Suspense>
      )}
    </div>
  )
})

/** 쪽별 가로/세로 비율을 읽습니다. 화면 밖 쪽도 실제 크기만큼 자리를 잡아야 스크롤 위치가 맞습니다. */
async function readPageRatios(doc: {
  numPages: number
  getPage: (
    n: number,
  ) => Promise<{ getViewport: (o: { scale: number }) => { width: number; height: number } }>
}): Promise<number[]> {
  return Promise.all(
    Array.from({ length: doc.numPages }, (_, i) =>
      doc.getPage(i + 1).then(
        (page) => {
          const { width, height } = page.getViewport({ scale: 1 })
          return width / height
        },
        () => A4_RATIO,
      ),
    ),
  )
}

interface MarksProps {
  findings: FindingView[]
  revealed: boolean
  selectedRow?: number
  focus?: Focus
  /** 없으면 누를 수 없는 그림(돋보기 안)으로 그립니다. */
  onSelectRow?: (rowIndex: number) => void
}

/** 한 쪽 위의 표시: 가림 상자, 이미 가려진 값의 점선, 고른 영역, 인용 위치. findings 와 focus 는 그 쪽의 것만 받습니다. */
const Marks = memo(function Marks({
  findings,
  revealed,
  selectedRow,
  focus,
  onSelectRow,
}: MarksProps) {
  const interactive = onSelectRow != null
  return (
    <div className="pointer-events-none absolute inset-0">
      {findings.flatMap((f) => {
        const kind = markKind(f)
        const selected = f.rowIndex === selectedRow
        if (kind === 'none') return []
        // 요소 영역만 있는 항목은 넓은 영역을 칠하지 않고, 골랐을 때만 점선으로 보여 줍니다.
        if (kind === 'area-only') {
          if (!selected || !f.elementBox) return []
          const rect = toPercentRect(f.elementBox)
          return rect
            ? [
                <span
                  key={`${f.rowIndex}-area`}
                  data-selected={interactive ? 'true' : undefined}
                  title="값의 정확한 위치 없이 값이 들어 있는 영역만 있습니다."
                  style={rect}
                  className="absolute z-10 rounded-[2px] border-2 border-dashed border-blue-500"
                />,
              ]
            : []
        }
        return (f.wordBoxes ?? []).map((box, i) => {
          const rect = toPercentRect(box)
          if (!rect) return null
          const className = cn(
            'absolute rounded-[2px]',
            interactive && 'pointer-events-auto',
            kind === 'already-masked'
              ? 'border-2 border-dashed border-zinc-500'
              : revealed
                ? 'border-2 border-red-500 bg-red-500/10'
                : 'bg-zinc-900',
            selected && 'z-10 ring-3 ring-blue-500 ring-offset-1',
          )
          return interactive ? (
            <button
              key={`${f.rowIndex}-${i}`}
              type="button"
              title={`${f.itemLabel} · ${f.infoType}`}
              aria-label={`${f.itemLabel} 위치`}
              data-selected={selected && i === 0 ? 'true' : undefined}
              onClick={() => onSelectRow(f.rowIndex)}
              style={rect}
              className={className}
            />
          ) : (
            <span key={`${f.rowIndex}-${i}`} style={rect} className={className} />
          )
        })
      })}
      {focus?.boxes.map((box, i) => {
        const rect = toPercentRect(box)
        if (!rect) return null
        return (
          <span
            key={`focus-${focus.nonce}-${i}`}
            data-focus={interactive && i === 0 ? 'true' : undefined}
            style={rect}
            className={cn(
              'absolute z-20 rounded-[2px] border-[3px] border-amber-400',
              interactive && 'animate-pulse',
            )}
          />
        )
      })}
    </div>
  )
})

/**
 * 돋보기. 마우스 위치를 가운데에 두고 화면의 페이지 그림을 크게 복사해 보여 줍니다.
 * 같은 Marks 를 함께 그려서, 가린 값은 돋보기 안에서도 가려져 있습니다.
 */
function Loupe({ hover, ...marks }: { hover: Hover } & Omit<MarksProps, 'onSelectRow'>) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const width = Math.round(hover.width * LOUPE_ZOOM)
  const height = Math.round(hover.height * LOUPE_ZOOM)
  const half = LOUPE_SIZE / 2

  // 돋보기가 나타날 때(또는 크기가 바뀔 때) 한 번만 그리고, 마우스가 움직이면 위치만 옮깁니다.
  useLayoutEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return
    const ratio = window.devicePixelRatio || 1
    canvas.width = Math.round(width * ratio)
    canvas.height = Math.round(height * ratio)
    const context = canvas.getContext('2d')
    if (!context) return
    context.imageSmoothingQuality = 'high'
    context.drawImage(hover.source, 0, 0, canvas.width, canvas.height)
  }, [hover.source, width, height])

  return (
    <div
      aria-hidden
      data-loupe
      className="pointer-events-none absolute z-30 overflow-hidden rounded-md border-2 border-white bg-white shadow-[0_4px_24px_rgba(0,0,0,0.35)] ring-1 ring-black/20"
      style={{
        width: LOUPE_SIZE,
        height: LOUPE_SIZE,
        left: hover.x * hover.width - half,
        top: hover.y * hover.height - half,
      }}
    >
      <div
        className="relative"
        style={{
          width,
          height,
          transform: `translate(${half - hover.x * width}px, ${half - hover.y * height}px)`,
        }}
      >
        <canvas ref={canvasRef} className="loupe-canvas block h-full w-full" />
        <Marks {...marks} />
      </div>
    </div>
  )
}

/**
 * PDF 를 그릴 해상도(CSS px). 확대·돋보기에도 흐리지 않도록 칸 너비의 약 2.5배를 300px 단위로 올림하고,
 * 커질 때만 바꿉니다. 줄어들 때는 다시 그리지 않고 CSS 로 축소하므로, 스크롤바 때문에 너비가 바뀌어도 다시 그리지 않습니다.
 */
function useRenderWidth(ref: React.RefObject<HTMLElement | null>): number {
  const [width, setWidth] = useState(MIN_RENDER_WIDTH)
  useLayoutEffect(() => {
    const el = ref.current
    if (!el) return
    const update = (measured: number) => {
      const bucket = Math.min(
        MAX_RENDER_WIDTH,
        Math.max(MIN_RENDER_WIDTH, Math.ceil((measured * 2.5) / 300) * 300),
      )
      setWidth((current) => (bucket > current ? bucket : current))
    }
    // 처음에는 바로 재고, 이후에는 크기가 바뀔 때만 다시 잽니다.
    update(el.clientWidth)
    const observer = new ResizeObserver(([entry]) => update(entry.contentRect.width))
    observer.observe(el)
    return () => observer.disconnect()
  }, [ref])
  return width
}

const MIN_RENDER_WIDTH = 1200
const MAX_RENDER_WIDTH = 2400
