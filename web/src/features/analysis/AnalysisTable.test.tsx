import { cleanup, fireEvent, render, screen } from '@testing-library/react'
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

function row(
  name: string,
  riskGrade: AnalysisSummary['riskGrade'],
  reviewStatus: AnalysisSummary['reviewStatus'],
  extra: Partial<AnalysisSummary> = {},
): AnalysisSummary {
  return {
    id: name,
    status: 'COMPLETED',
    fileName: `${name}.pdf`,
    riskGrade,
    gradeProvisional: false,
    reviewStatus,
    createdAt: '2026-11-09T12:00:00Z',
    replay: false,
    ...extra,
  }
}

const mixed = [
  row('가', 'LOW', 'NOT_REQUIRED', { documentType: '신탁 해지 신청서' }),
  row('나', 'HIGH', 'PENDING', { documentType: '대출 신청서' }),
  row('다', 'MEDIUM', 'PENDING'),
  row('라', 'HIGH', 'NOT_REQUIRED'),
  row('마', undefined, 'NOT_REQUIRED', { status: 'RUNNING' }),
]

function showMixed(items = mixed, entry = '/analyses') {
  render(
    <MemoryRouter initialEntries={[entry]}>
      <AnalysisTable items={items} emptyText="없음" />
    </MemoryRouter>,
  )
}

const names = () => screen.getAllByRole('link').map((link) => link.textContent?.replace('.pdf', ''))

const press = (name: string) => fireEvent.click(screen.getByRole('button', { name }))

describe('AnalysisTable 검색과 위로 올리기 (F07)', () => {
  beforeAll(() => {
    Element.prototype.scrollIntoView = vi.fn()
  })

  it('등급을 고르면 같은 등급이 위로 오고 나머지 문서는 그대로 남는다', () => {
    showMixed()
    press('상 2건')
    expect(names()).toEqual(['나', '라', '가', '다', '마'])
    expect(screen.getByRole('button', { name: '상 2건' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('목록 행의 등급 표시를 눌러도 위로 오고, 다시 누르면 원래 순서로 돌아간다', () => {
    showMixed()
    press('위험 등급 중')
    expect(names()).toEqual(['다', '가', '나', '라', '마'])
    press('위험 등급 중')
    expect(names()).toEqual(['가', '나', '다', '라', '마'])
  })

  it('검토 상태를 고르면 같은 상태가 위로 온다', () => {
    showMixed()
    press('검토 대기 중 2건')
    expect(names()).toEqual(['나', '다', '가', '라', '마'])
  })

  it('목록 행의 검토 상태 표시를 눌러도 위로 온다', () => {
    showMixed()
    fireEvent.click(screen.getAllByRole('button', { name: '검토 불필요' })[0])
    expect(names()).toEqual(['가', '라', '나', '다', '마'])
  })

  it('분석 중인 문서는 검토 불필요로 세지 않는다', () => {
    showMixed()
    expect(screen.getByRole('button', { name: '검토 불필요 2건' })).toBeInTheDocument()
  })

  it('등급과 검토 상태를 함께 고르면 둘 다 맞는 문서가 먼저 온다', () => {
    showMixed()
    press('상 2건')
    press('검토 대기 중 2건')
    expect(names()).toEqual(['나', '라', '다', '가', '마'])
  })

  it('고른 항목만 보기를 켜면 맞지 않는 문서를 숨긴다', () => {
    showMixed()
    press('상 2건')
    fireEvent.click(screen.getByRole('checkbox', { name: '고른 항목만 보기' }))
    expect(names()).toEqual(['나', '라'])
    expect(screen.getByText('전체 5건 중 2건')).toBeInTheDocument()
  })

  it('고른 조건을 모두 풀면 고른 항목만 보기도 함께 꺼진다', () => {
    showMixed()
    press('상 2건')
    fireEvent.click(screen.getByRole('checkbox', { name: '고른 항목만 보기' }))
    press('상 2건')
    press('중 1건')
    expect(names()).toEqual(['다', '가', '나', '라', '마'])
  })

  it('파일 이름과 문서 종류로 검색하고, 건수도 검색 결과에 맞춘다', () => {
    showMixed()
    const box = screen.getByRole('searchbox', { name: '문서 검색' })
    fireEvent.change(box, { target: { value: '대출' } })
    expect(names()).toEqual(['나'])
    expect(screen.getByRole('button', { name: '상 1건' })).toBeInTheDocument()

    fireEvent.change(box, { target: { value: '가.PDF' } })
    expect(names()).toEqual(['가'])
  })

  it('검색 결과가 없으면 안내를 보여 주고, 조건 지우기로 되돌린다', () => {
    showMixed()
    const box = screen.getByRole('searchbox', { name: '문서 검색' })
    fireEvent.change(box, { target: { value: '없는 문서' } })
    expect(screen.getByText('조건에 맞는 문서가 없습니다.')).toBeInTheDocument()

    press('조건 지우기')
    expect(box).toHaveValue('')
    expect(names()).toEqual(['가', '나', '다', '라', '마'])
  })

  it('다른 쪽을 보다가 고르면 첫 쪽에서 위로 올라온 문서를 보여 준다', () => {
    const many = [...analyses(20), row('낮음', 'LOW', 'PENDING')]
    showMixed(many, '/analyses?page=2')
    press('하 1건')
    expect(names()[0]).toBe('낮음')
    expect(screen.getByRole('button', { name: '1쪽' })).toHaveAttribute('aria-current', 'page')
  })

  it('주소에 있는 등급을 따르고, 목록에 없는 값은 무시한다', () => {
    showMixed(mixed, '/analyses?grade=MEDIUM')
    expect(names()[0]).toBe('다')
    cleanup()
    showMixed(mixed, '/analyses?grade=NONE&only=1')
    expect(names()).toEqual(['가', '나', '다', '라', '마'])
  })

  it('값이 한 종류뿐이면 고를 수 없게 그대로 보여 준다', () => {
    showMixed(analyses(3))
    expect(screen.queryByRole('group', { name: '위험 등급' })).not.toBeInTheDocument()
    expect(screen.getAllByText('위험 등급 상')[0].closest('button')).toBeNull()
  })

  it('파일 이름을 받지 못하는 목록에서는 문서 종류로만 검색한다고 안내한다', () => {
    showMixed([row('가', 'HIGH', 'PENDING', { fileName: undefined, documentType: '대출 신청서' })])
    expect(screen.getByRole('searchbox', { name: '문서 검색' })).toHaveAttribute(
      'placeholder',
      '문서 종류로 검색',
    )
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
