import { render, screen } from '@testing-library/react'
import type { AnalysisDetail, RiskGrade } from '@/api/types'
import { ResultSummary } from './ResultSummary'

function detail(riskGrade: RiskGrade): AnalysisDetail {
  return {
    summary: {
      id: 'a',
      status: 'COMPLETED',
      riskGrade,
      gradeProvisional: false,
      reviewRequired: true,
      reviewStatus: 'PENDING',
      createdAt: new Date().toISOString(),
      replay: false,
    },
    contentType: 'application/pdf',
    studioConfigId: '5',
    steps: [],
    completedSteps: [],
    expiresAt: new Date().toISOString(),
    owner: true,
    canManage: true,
    canReview: false,
  } as AnalysisDetail
}

describe('ResultSummary', () => {
  it('미확정은 읽기 실패 안내 없이 미확정으로 보여 준다', () => {
    render(<ResultSummary detail={detail('UNDECIDED')} />)
    expect(screen.getByText('미확정')).toBeInTheDocument()
    expect(screen.getByText('위험 등급 미확정')).toBeInTheDocument()
    expect(screen.queryByText(/읽지 못해/)).not.toBeInTheDocument()
  })

  it('보고서를 읽지 못하면 판정 확인 필요 안내를 보여 준다', () => {
    render(<ResultSummary detail={detail('UNKNOWN')} />)
    expect(screen.getByText(/읽지 못해/)).toBeInTheDocument()
  })
})
