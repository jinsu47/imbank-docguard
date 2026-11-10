import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { routes } from '@/routes'

type Handler = (url: string) => { status: number; body?: unknown }

function mockFetch(handler: Handler) {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL) => {
      const { status, body } = handler(String(input))
      return new Response(body === undefined ? null : JSON.stringify(body), {
        status,
        headers: { 'Content-Type': 'application/json' },
      })
    }),
  )
}

function renderAt(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] })
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
  return router
}

const inspector = { id: 1, email: 'a@test.local', displayName: '시험', role: 'INSPECTOR' }

afterEach(() => vi.unstubAllGlobals())

describe('라우팅과 로그인 확인', () => {
  it('로그인하지 않았으면 원래 경로를 기억하고 로그인 화면으로 보낸다', async () => {
    mockFetch(() => ({
      status: 401,
      body: { code: 'UNAUTHORIZED', message: '로그인이 필요합니다.' },
    }))
    const router = renderAt('/analyses/abc')
    expect(await screen.findByRole('heading', { name: '로그인' })).toBeInTheDocument()
    expect(router.state.location.search).toBe('?next=%2Fanalyses%2Fabc')
  })

  it('로그인하면 메인화면을 보여 주고, 내 분석으로 가는 메뉴가 있다', async () => {
    mockFetch((url) =>
      url.endsWith('/api/auth/me') ? { status: 200, body: inspector } : { status: 200, body: [] },
    )
    renderAt('/')
    expect(
      await screen.findByRole('heading', { name: /민감정보부터 확인하세요/ }),
    ).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: /문서 올리기/ })).toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: /내 분석/ }).length).toBeGreaterThan(0)
  })

  it('/analyses 는 내 분석 목록을 보여 준다', async () => {
    mockFetch((url) =>
      url.endsWith('/api/auth/me') ? { status: 200, body: inspector } : { status: 200, body: [] },
    )
    renderAt('/analyses')
    expect(await screen.findByRole('heading', { name: '내 분석' })).toBeInTheDocument()
    expect(await screen.findByText(/아직 올린 문서가 없습니다/)).toBeInTheDocument()
  })

  it('점검 담당자가 검토 대기열로 직접 가면 권한 없음 화면', async () => {
    mockFetch(() => ({ status: 200, body: inspector }))
    renderAt('/reviews')
    expect(
      await screen.findByRole('heading', { name: '이 화면을 볼 권한이 없습니다.' }),
    ).toBeInTheDocument()
  })

  it('관리자 메인화면에서 문서 올리기와 관리 메뉴를 함께 쓸 수 있다', async () => {
    mockFetch((url) =>
      url.endsWith('/api/auth/me')
        ? { status: 200, body: { ...inspector, role: 'ADMIN' } }
        : { status: 200, body: [] },
    )
    renderAt('/')
    expect(await screen.findByRole('button', { name: /문서 올리기/ })).toBeInTheDocument()
    expect(await screen.findAllByRole('link', { name: /계정 관리/ })).not.toHaveLength(0)
    expect(screen.getAllByRole('link', { name: /검토 대기열/ }).length).toBeGreaterThan(0)
  })

  it('관리자는 내 분석과 검토 대기열 화면에 들어갈 수 있다', async () => {
    mockFetch((url) =>
      url.endsWith('/api/auth/me')
        ? { status: 200, body: { ...inspector, role: 'ADMIN' } }
        : { status: 200, body: [] },
    )
    renderAt('/reviews')
    expect(await screen.findByRole('heading', { name: '검토 대기열' })).toBeInTheDocument()
  })

  it('시연 정확도는 메뉴에 없고 주소로 들어가도 열리지 않는다', async () => {
    mockFetch((url) =>
      url.endsWith('/api/auth/me') ? { status: 200, body: inspector } : { status: 200, body: [] },
    )
    renderAt('/demo/accuracy')
    expect(
      await screen.findByRole('heading', { name: '페이지를 찾을 수 없습니다.' }),
    ).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /시연 정확도/ })).toBeNull()
  })

  it('관리자 계정 관리 화면이 열린다', async () => {
    mockFetch((url) =>
      url.endsWith('/api/auth/me')
        ? { status: 200, body: { ...inspector, role: 'ADMIN' } }
        : { status: 200, body: [] },
    )
    renderAt('/admin/users')
    expect(await screen.findByRole('heading', { name: '계정 관리' })).toBeInTheDocument()
  })

  it('점검 담당자는 관리자 화면에 들어갈 수 없다', async () => {
    mockFetch(() => ({ status: 200, body: inspector }))
    renderAt('/admin/audit')
    expect(
      await screen.findByRole('heading', { name: '이 화면을 볼 권한이 없습니다.' }),
    ).toBeInTheDocument()
  })

  it('/signup 은 로그인 없이 연다', async () => {
    mockFetch(() => ({ status: 401, body: {} }))
    renderAt('/signup')
    expect(await screen.findByRole('heading', { name: '회원가입' })).toBeInTheDocument()
  })
})
