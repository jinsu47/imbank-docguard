import { render, screen, within } from '@testing-library/react'
import type { AnalysisDetail } from '@/api/types'
import { AnalysisProgress } from './AnalysisProgress'

const STEPS = ['PARSE', 'PARSE_CHECK', 'CLASSIFY', 'EXTRACT', 'MATCH', 'EXTRACT_CHECK', 'REPORT']

function detail(
  status: AnalysisDetail['summary']['status'],
  completedSteps: string[],
): AnalysisDetail {
  return {
    summary: {
      id: 'a',
      status,
      gradeProvisional: false,
      reviewStatus: 'NOT_REQUIRED',
      createdAt: new Date().toISOString(),
      replay: false,
    },
    contentType: 'application/pdf',
    studioConfigId: '2',
    steps: STEPS,
    completedSteps,
    expiresAt: new Date().toISOString(),
    owner: true,
    canManage: true,
    canReview: false,
  }
}

describe('AnalysisProgress', () => {
  it('원형 진행률과 7단계 칸을 함께 보여 주고, 끝난 단계와 진행 중인 단계를 구분한다', () => {
    render(<AnalysisProgress detail={detail('RUNNING', STEPS.slice(0, 3))} />)
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '43')
    const items = within(screen.getByRole('list', { name: '분석 단계' })).getAllByRole('listitem')
    expect(items).toHaveLength(7)
    expect(items[0]).toHaveTextContent('문서 인식 완료')
    expect(items[3]).toHaveTextContent('민감정보 추출 진행 중')
    expect(items[3]).toHaveAttribute('aria-current', 'step')
    expect(items[4]).not.toHaveTextContent('완료')
  })

  it('대기 중에는 진행 중인 칸이 없다', () => {
    render(<AnalysisProgress detail={detail('QUEUED', [])} />)
    const items = within(screen.getByRole('list', { name: '분석 단계' })).getAllByRole('listitem')
    expect(items.filter((item) => item.getAttribute('aria-current'))).toHaveLength(0)
  })
})
