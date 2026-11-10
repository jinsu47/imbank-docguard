import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { adminApi } from '@/api/endpoints'
import type { AdminAnalysis } from '@/api/types'
import AdminAnalysesPage from './AdminAnalysesPage'

function row(n: number, deletionRecommended: boolean): AdminAnalysis {
  return {
    id: `id-${n}`,
    fileName: `문서${n}.pdf`,
    ownerId: 1,
    ownerEmail: 'inspector@docguard.local',
    status: 'COMPLETED',
    riskGrade: 'HIGH',
    reviewStatus: 'NOT_REQUIRED',
    createdAt: new Date(Date.now() - 9 * 86_400_000).toISOString(),
    expiresAt: new Date(Date.now() - 2 * 86_400_000).toISOString(),
    replay: false,
    deletionRecommended,
  }
}

async function show(rows: AdminAnalysis[], entry = '/admin/analyses') {
  vi.spyOn(adminApi, 'analyses').mockResolvedValue(rows)
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter initialEntries={[entry]}>
        <AdminAnalysesPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
  await screen.findByRole('link', { name: `${rows[0].fileName}` })
}

describe('AdminAnalysesPage 삭제 권고 (BR15)', () => {
  beforeAll(() => {
    Element.prototype.scrollIntoView = vi.fn()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('권고 문서가 없으면 안내를 보여 주지 않는다', async () => {
    await show([row(1, false)])
    expect(screen.queryByText(/삭제 권고 \d+건/)).not.toBeInTheDocument()
  })

  it('권고 건수와 문서별 표시를 보여 주고, 자동으로 지우지 않는다고 안내한다', async () => {
    await show([row(1, true), row(2, false)])

    expect(screen.getByText('삭제 권고 1건')).toBeInTheDocument()
    expect(screen.getByText(/자동으로 지워지지 않으니/)).toBeInTheDocument()
    const recommendedRow = screen.getByRole('link', { name: '문서1.pdf' }).closest('tr')!
    expect(within(recommendedRow).getByText('삭제 권고')).toBeInTheDocument()
    expect(within(recommendedRow).getByText(/올린 지 9일/)).toBeInTheDocument()
    const normalRow = screen.getByRole('link', { name: '문서2.pdf' }).closest('tr')!
    expect(within(normalRow).queryByText('삭제 권고')).not.toBeInTheDocument()
  })

  it('권고 문서만 볼 수 있고 다시 전체로 돌아간다', async () => {
    await show([row(1, true), row(2, false)])

    fireEvent.click(screen.getByRole('button', { name: '권고 문서만 보기' }))
    expect(screen.queryByRole('link', { name: '문서2.pdf' })).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '전체 문서 보기' }))
    expect(screen.getByRole('link', { name: '문서2.pdf' })).toBeInTheDocument()
  })

  it('주소의 recommend 값으로 권고 문서만 연다', async () => {
    await show([row(1, true), row(2, false)], '/admin/analyses?recommend=1')
    expect(screen.queryByRole('link', { name: '문서2.pdf' })).not.toBeInTheDocument()
  })

  it('권고 문서가 없으면 recommend 값이 있어도 전체 목록을 보여 준다', async () => {
    await show([row(2, false)], '/admin/analyses?recommend=1')
    expect(screen.getByRole('link', { name: '문서2.pdf' })).toBeInTheDocument()
  })
})
