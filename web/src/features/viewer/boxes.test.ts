import { boxesOf, markKind, MAX_ZOOM, toPercentRect, zoomScaleFor } from './boxes'

describe('toPercentRect', () => {
  it('상대 좌표 꼭짓점 4개를 백분율 사각형으로 바꾼다', () => {
    const rect = toPercentRect([
      { x: 0.2, y: 0.1 },
      { x: 0.4, y: 0.1 },
      { x: 0.4, y: 0.15 },
      { x: 0.2, y: 0.15 },
    ])
    expect(rect?.left).toBe('20%')
    expect(parseFloat(rect!.width)).toBeCloseTo(20)
    expect(parseFloat(rect!.height)).toBeCloseTo(5)
  })

  it('넓이가 없거나 점이 없으면 null', () => {
    expect(toPercentRect([])).toBeNull()
    expect(toPercentRect([{ x: 0.1, y: 0.1 }])).toBeNull()
  })
})

describe('boxesOf', () => {
  it('값 상자가 없으면 요소 상자를 쓴다', () => {
    const element = [
      { x: 0, y: 0 },
      { x: 1, y: 1 },
    ]
    expect(boxesOf({ elementBox: element })).toEqual([element])
    expect(boxesOf({})).toEqual([])
  })
})

describe('markKind', () => {
  const word = [
    [
      { x: 0.1, y: 0.1 },
      { x: 0.2, y: 0.12 },
    ],
  ]
  const element = [
    { x: 0.05, y: 0.3 },
    { x: 0.93, y: 0.5 },
  ]
  it('값 상자가 있으면 그 자리만 가린다', () => {
    expect(markKind({ maskState: '미마스킹', wordBoxes: word, elementBox: element })).toBe('mask')
  })
  it('이미 전체 가림인 값은 칠하지 않는다', () => {
    expect(markKind({ maskState: '전체마스킹', wordBoxes: word })).toBe('already-masked')
  })
  it('값 상자 없이 요소 영역만 있으면 넓은 영역을 칠하지 않는다(set08 전자우편)', () => {
    expect(markKind({ maskState: '전체마스킹', elementBox: element })).toBe('area-only')
    expect(markKind({ maskState: '미마스킹', elementBox: element })).toBe('area-only')
  })
  it('위치가 없으면 그리지 않는다', () => {
    expect(markKind({ maskState: '미마스킹' })).toBe('none')
  })
})

describe('zoomScaleFor', () => {
  it('좁은 값(이름 칸)은 크게, 최대 배율까지만 확대한다', () => {
    const name = [
      [
        { x: 0.217, y: 0.195 },
        { x: 0.257, y: 0.206 },
      ],
    ]
    expect(zoomScaleFor(name)).toBeGreaterThanOrEqual(2.5)
    expect(zoomScaleFor(name)).toBeLessThanOrEqual(MAX_ZOOM)
  })
  it('표 전체처럼 넓은 영역은 확대하지 않는다', () => {
    expect(
      zoomScaleFor([
        [
          { x: 0.06, y: 0.3 },
          { x: 0.93, y: 0.5 },
        ],
      ]),
    ).toBe(1)
  })
  it('상자가 없으면 1', () => {
    expect(zoomScaleFor([])).toBe(1)
  })
})
