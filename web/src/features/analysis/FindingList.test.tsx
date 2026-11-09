import { render, screen } from '@testing-library/react'
import type { FindingView } from '@/api/types'
import { FindingList } from './FindingList'

function finding(extra: Partial<FindingView>): FindingView {
  return {
    rowIndex: 0,
    itemLabel: '카드번호',
    infoType: '카드번호',
    subjectRole: '신청인',
    maskState: '부분마스킹',
    policyLinked: true,
    displayValue: '1234-56**-****-3456',
    ...extra,
  }
}

describe('FindingList', () => {
  it('일부 가린 항목에 남은 정보의 종류를 보여 준다', () => {
    render(<FindingList findings={[finding({ residualInfo: 'BIN·뒤 4자리' })]} onSelect={() => {}} />)
    expect(screen.getByText('· 남은 정보: BIN·뒤 4자리')).toBeInTheDocument()
  })

  it('남은 정보가 없으면 표시하지 않는다', () => {
    render(<FindingList findings={[finding({})]} onSelect={() => {}} />)
    expect(screen.queryByText(/남은 정보/)).not.toBeInTheDocument()
  })
})
