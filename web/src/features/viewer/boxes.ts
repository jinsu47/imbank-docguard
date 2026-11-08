import type { Point } from '@/api/types'

/** 꼭짓점 좌표(0~1)를 감싸는 사각형을 백분율로 돌려줍니다. 렌더링된 페이지 크기와 관계없이 맞습니다. */
export function toPercentRect(
  points: Point[],
): { left: string; top: string; width: string; height: string } | null {
  if (points.length === 0) return null
  const xs = points.map((p) => p.x)
  const ys = points.map((p) => p.y)
  const minX = clamp(Math.min(...xs))
  const minY = clamp(Math.min(...ys))
  const maxX = clamp(Math.max(...xs))
  const maxY = clamp(Math.max(...ys))
  if (maxX <= minX || maxY <= minY) return null
  return {
    left: `${minX * 100}%`,
    top: `${minY * 100}%`,
    width: `${(maxX - minX) * 100}%`,
    height: `${(maxY - minY) * 100}%`,
  }
}

function clamp(v: number): number {
  return Math.min(1, Math.max(0, v))
}

/** 값 자체의 상자(wordBoxes)를 쓰고, 없으면 요소 상자(elementBox)를 씁니다. */
export function boxesOf(item: { wordBoxes?: Point[][]; elementBox?: Point[] }): Point[][] {
  if (item.wordBoxes && item.wordBoxes.length > 0) return item.wordBoxes
  if (item.elementBox && item.elementBox.length > 0) return [item.elementBox]
  return []
}

/**
 * 문서 위에 무엇을 그릴지 정합니다(BR10).
 * - mask: 값 자체의 상자(wordBoxes)가 있으면 그 자리만 가립니다.
 * - already-masked: 문서에 이미 전체 가림으로 적힌 값은 가릴 것이 없으므로 점선 테두리만 그립니다.
 * - area-only: 값 위치는 없고 값이 들어 있는 요소(표 등) 영역만 있으면, 그 넓은 영역을 칠하지 않고
 *   항목을 골랐을 때만 점선으로 영역을 보여 줍니다.
 */
export type MarkKind = 'mask' | 'already-masked' | 'area-only' | 'none'

export function markKind(item: {
  maskState: string
  wordBoxes?: Point[][]
  elementBox?: Point[]
}): MarkKind {
  if (item.wordBoxes && item.wordBoxes.length > 0) {
    return item.maskState === '전체마스킹' ? 'already-masked' : 'mask'
  }
  if (item.elementBox && item.elementBox.length > 0) return 'area-only'
  return 'none'
}

/** 항목을 골랐을 때 확대 배율. 고른 영역 양옆에 문맥이 조금 보이도록 페이지 너비의 15%씩 여유를 둡니다. */
export const MAX_ZOOM = 4

export function zoomScaleFor(boxes: Point[][]): number {
  const points = boxes.flat()
  if (points.length === 0) return 1
  const xs = points.map((p) => p.x)
  const width = Math.max(0.02, Math.max(...xs) - Math.min(...xs))
  const scale = 1 / (width + 0.3)
  // 이미 넓은 영역(표 전체 등)은 확대해도 이득이 적으므로 그대로 둡니다.
  if (scale < 1.3) return 1
  return Math.min(MAX_ZOOM, Math.round(scale * 10) / 10)
}
