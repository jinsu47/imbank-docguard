import { render, screen } from '@testing-library/react'
import type { AnalysisDetail, ResultView, RiskGrade } from '@/api/types'
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

  it('판정 경로가 있으면 등급 아래에 보여 준다', () => {
    const result = { sections: [], findings: [], citations: [], gradeRoute: 'RULE' } as ResultView
    render(<ResultSummary detail={detail('HIGH')} result={result} />)
    expect(screen.getByText('고위험 규칙으로 판정')).toBeInTheDocument()
  })

  it('v4 이전 결과에는 판정 경로를 표시하지 않는다', () => {
    const result = { sections: [], findings: [], citations: [] } as ResultView
    render(<ResultSummary detail={detail('HIGH')} result={result} />)
    expect(screen.queryByText(/으로 판정/)).not.toBeInTheDocument()
  })

  it('하 등급은 노출 미발견의 뜻과 확인 범위를 함께 보여 준다', () => {
    const result = {
      sections: [],
      findings: [],
      citations: [],
      gradeRoute: 'DECISION',
    } as ResultView
    render(<ResultSummary detail={detail('LOW')} result={result} />)
    expect(
      screen.getByText('규칙상 노출 미발견 (확인한 범위 기준) · AI가 기준표로 판정'),
    ).toBeInTheDocument()
  })

  it('v4 이전의 하 등급에는 노출 미발견 문구를 붙이지 않는다', () => {
    const result = { sections: [], findings: [], citations: [] } as ResultView
    render(<ResultSummary detail={detail('LOW')} result={result} />)
    expect(screen.queryByText(/노출 미발견/)).not.toBeInTheDocument()
  })

  it('중 등급에는 노출 미발견 문구를 붙이지 않는다', () => {
    const result = {
      sections: [],
      findings: [],
      citations: [],
      gradeRoute: 'DECISION',
    } as ResultView
    render(<ResultSummary detail={detail('MEDIUM')} result={result} />)
    expect(screen.getByText('AI가 기준표로 판정')).toBeInTheDocument()
    expect(screen.queryByText(/노출 미발견/)).not.toBeInTheDocument()
  })

  it('보고서를 읽지 못하면 판정 확인 필요 안내를 보여 준다', () => {
    render(<ResultSummary detail={detail('UNKNOWN')} />)
    expect(screen.getByText(/읽지 못해/)).toBeInTheDocument()
  })
})
