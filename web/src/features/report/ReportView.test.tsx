import { render, screen } from '@testing-library/react'
import { ReportView } from './ReportView'

describe('ReportView', () => {
  it('반복 제목을 이어 붙인 문단의 줄바꿈과 인용 번호를 유지한다', () => {
    const { container } = render(
      <ReportView
        sections={[{ title: '판정 근거', body: '첫 근거【†1】\n둘째 근거【†2】' }]}
        onCitation={() => {}}
      />,
    )

    const paragraph = container.querySelector('p')
    expect(paragraph).toHaveClass('whitespace-pre-line')
    expect(paragraph?.textContent).toContain('\n둘째 근거')
    expect(screen.getByRole('button', { name: '근거 1번 위치 보기' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '근거 2번 위치 보기' })).toBeInTheDocument()
  })
})
