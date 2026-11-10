import { splitCitations } from './citations'

describe('splitCitations', () => {
  it('인용 표시를 번호 조각으로 나눈다', () => {
    expect(splitCitations('이름이 보인다.【†1】【†12】 끝')).toEqual([
      { type: 'text', text: '이름이 보인다.' },
      { type: 'cite', index: 1 },
      { type: 'cite', index: 12 },
      { type: 'text', text: ' 끝' },
    ])
  })

  it('인용이 없으면 문장 그대로', () => {
    expect(splitCitations('활용 맥락은 반영하지 않았다.')).toEqual([
      { type: 'text', text: '활용 맥락은 반영하지 않았다.' },
    ])
  })

  it('HTML 처럼 보이는 문자열도 글자로 둔다', () => {
    expect(splitCitations('<b>x</b>【†2】')).toEqual([
      { type: 'text', text: '<b>x</b>' },
      { type: 'cite', index: 2 },
    ])
  })
})
