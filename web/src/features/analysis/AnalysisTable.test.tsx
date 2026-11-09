import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import type { AnalysisSummary } from '@/api/types'
import { pageWindow } from '@/lib/pagination'
import { AnalysisTable } from './AnalysisTable'

function analyses(count: number): AnalysisSummary[] {
  return Array.from({ length: count }, (_, i) => ({
    id: `id-${i + 1}`,
    status: 'COMPLETED',
    fileName: `문서${i + 1}.pdf`,
    riskGrade: 'HIGH',
    gradeProvisional: false,
    reviewRequired: false,
    reviewStatus: 'NOT_REQUIRED',
    createdAt: '2026-11-09T12:00:00Z',
    replay: false,
  }))
}

function show(count: number, entry = '/analyses') {
  render(
    <MemoryRouter initialEntries={[entry]}>
      <AnalysisTable items={analyses(count)} emptyText="없음" />
    </MemoryRouter>,
  )
}

const rows = () => screen.getAllByRole('row').length - 1

describe('AnalysisTable 쪽 나누기 (F07)', () => {
  beforeAll(() => {
    Element.prototype.scrollIntoView = vi.fn()
  })

  it('15건까지는 쪽 이동을 보여 주지 않는다', () => {
    show(15)
    expect(rows()).toBe(15)
    expect(screen.queryByRole('navigation', { name: '목록 쪽 이동' })).not.toBeInTheDocument()
  })

  it('16건이면 15건만 보여 주고 다음 쪽으로 넘어간다', () => {
    show(16)
    expect(rows()).toBe(15)
    expect(screen.getByText('전체 16건 · 1 / 2쪽')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '이전' })).toBeDisabled()

    fireEvent.click(screen.getByRole('button', { name: '2쪽' }))

    expect(rows()).toBe(1)
    expect(screen.getByRole('link', { name: '문서16.pdf' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '2쪽' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('button', { name: '다음' })).toBeDisabled()
  })

  it('주소의 쪽 번호를 따르고, 없는 쪽이면 마지막 쪽을 보여 준다', () => {
    show(31, '/analyses?page=2')
    expect(screen.getByRole('link', { name: '문서16.pdf' })).toBeInTheDocument()
  })

  it('쪽 수보다 큰 번호는 마지막 쪽으로 맞춘다', () => {
    show(31, '/analyses?page=99')
    expect(rows()).toBe(1)
    expect(screen.getByRole('link', { name: '문서31.pdf' })).toBeInTheDocument()
  })

  it('숫자가 아닌 쪽 번호는 첫 쪽으로 본다', () => {
    show(16, '/analyses?page=abc')
    expect(screen.getByRole('link', { name: '문서1.pdf' })).toBeInTheDocument()
  })
})

describe('pageWindow', () => {
  it('쪽이 적으면 모두 보여 준다', () => {
    expect(pageWindow(1, 3)).toEqual([1, 2, 3])
  })

  it('현재 쪽 양옆만 남기고 사이를 줄인다', () => {
    expect(pageWindow(5, 10)).toEqual([1, null, 4, 5, 6, null, 10])
    expect(pageWindow(1, 10)).toEqual([1, 2, null, 10])
  })

  it('한 쪽만 건너뛰면 줄임표 대신 번호를 보여 준다', () => {
    expect(pageWindow(4, 10)).toEqual([1, 2, 3, 4, 5, null, 10])
  })
})
