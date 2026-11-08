import { progressMessage, progressPercent } from './labels'

const STEPS = ['PARSE', 'PARSE_CHECK', 'CLASSIFY', 'EXTRACT', 'MATCH', 'EXTRACT_CHECK', 'REPORT']

describe('progressPercent', () => {
  it('끝난 단계 수로 퍼센트를 계산한다', () => {
    expect(progressPercent(0, 7)).toBe(0)
    expect(progressPercent(4, 7)).toBe(57)
  })
  it('분석이 끝나기 전에는 100 을 보여 주지 않는다', () => {
    expect(progressPercent(7, 7)).toBe(99)
  })
  it('단계 정보가 없으면 0', () => {
    expect(progressPercent(0, 0)).toBe(0)
  })
})

describe('progressMessage', () => {
  it('업로드와 대기는 상태로 안내한다', () => {
    expect(progressMessage('UPLOADING', STEPS, [])).toBe('문서를 올리는 중')
    expect(progressMessage('QUEUED', STEPS, [])).toBe('분석 순서를 기다리는 중')
  })
  it('진행 중이면 아직 끝나지 않은 첫 단계를 안내한다', () => {
    expect(progressMessage('RUNNING', STEPS, ['PARSE', 'PARSE_CHECK', 'CLASSIFY'])).toBe(
      '민감정보를 찾는 중',
    )
  })
  it('모든 단계가 끝났으면 결과를 정리한다고 안내한다', () => {
    expect(progressMessage('RUNNING', STEPS, STEPS)).toBe('결과를 정리하는 중')
  })
})
