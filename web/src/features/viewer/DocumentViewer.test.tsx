import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, render, screen, waitFor } from '@testing-library/react'
import { useEffect } from 'react'
import type { FindingView } from '@/api/types'
import { DocumentViewer } from './DocumentViewer'

const PAGES = 30

const fakeDocument = {
  numPages: PAGES,
  getPage: () => Promise.resolve({ getViewport: () => ({ width: 595, height: 842 }) }),
}

vi.mock('react-pdf', () => ({
  pdfjs: { GlobalWorkerOptions: {} },
  Document: ({
    children,
    onLoadSuccess,
  }: {
    children: React.ReactNode
    onLoadSuccess: (doc: typeof fakeDocument) => void
  }) => {
    // 실제 Document 처럼 불러온 뒤 한 번만 부릅니다.
    // oxlint-disable-next-line react-hooks/exhaustive-deps
    useEffect(() => onLoadSuccess(fakeDocument), [])
    return <div>{children}</div>
  },
  Page: ({ pageNumber }: { pageNumber: number }) => <div data-testid="drawn">{pageNumber}</div>,
}))

vi.mock('@/api/endpoints', () => ({
  analysisApi: { file: () => Promise.resolve(new Blob(['%PDF-'])) },
}))

const observed: { element: Element; callback: IntersectionObserverCallback }[] = []

function setNear(index: number, isIntersecting: boolean) {
  const { element, callback } = observed[index]
  act(() =>
    callback(
      [{ isIntersecting, target: element } as IntersectionObserverEntry],
      {} as IntersectionObserver,
    ),
  )
}

beforeEach(() => {
  observed.length = 0
  Element.prototype.scrollTo = vi.fn()
  vi.stubGlobal(
    'IntersectionObserver',
    class {
      callback: IntersectionObserverCallback
      constructor(callback: IntersectionObserverCallback) {
        this.callback = callback
      }
      observe(element: Element) {
        observed.push({ element, callback: this.callback })
      }
      disconnect() {}
    },
  )
  vi.stubGlobal(
    'ResizeObserver',
    class {
      observe() {}
      disconnect() {}
    },
  )
})

afterEach(() => vi.unstubAllGlobals())

function finding(page: number): FindingView {
  return {
    rowIndex: 0,
    page,
    maskState: '미마스킹',
    itemLabel: '계좌번호',
    infoType: '금융정보',
    wordBoxes: [
      [
        { x: 0.1, y: 0.1 },
        { x: 0.3, y: 0.1 },
        { x: 0.3, y: 0.2 },
        { x: 0.1, y: 0.2 },
      ],
    ],
  } as FindingView
}

function renderViewer(findings: FindingView[]) {
  const client = new QueryClient()
  return render(
    <QueryClientProvider client={client}>
      <DocumentViewer
        analysisId="a"
        contentType="application/pdf"
        findings={findings}
        revealed={false}
        magnifier={false}
        onSelectRow={() => {}}
      />
    </QueryClientProvider>,
  )
}

describe('DocumentViewer 긴 문서', () => {
  it('화면 가까이 온 쪽만 그리고, 멀어지면 지운다', async () => {
    renderViewer([])
    await waitFor(() => expect(observed).toHaveLength(PAGES))
    expect(screen.queryAllByTestId('drawn')).toHaveLength(0)

    setNear(19, true)
    expect(screen.getAllByTestId('drawn').map((el) => el.textContent)).toEqual(['20'])

    setNear(19, false)
    expect(screen.queryAllByTestId('drawn')).toHaveLength(0)
  })

  it('그리지 않은 쪽도 쪽 수만큼 자리를 잡는다', async () => {
    const { container } = renderViewer([])
    await waitFor(() => expect(observed).toHaveLength(PAGES))
    expect(container.querySelectorAll('.pdf-page')).toHaveLength(PAGES)
  })

  it('그리지 않은 쪽의 가림 상자도 있어서 그 위치로 이동할 수 있다', async () => {
    renderViewer([finding(30)])
    await waitFor(() => expect(observed).toHaveLength(PAGES))
    expect(screen.queryAllByTestId('drawn')).toHaveLength(0)
    expect(screen.getAllByRole('button', { name: '계좌번호 위치' })).toHaveLength(1)
  })
})
