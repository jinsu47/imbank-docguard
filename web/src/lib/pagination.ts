import { useRef } from 'react'
import { useSearchParams } from 'react-router'

/** 목록 한 쪽에 보여 줄 행 수. 이보다 많아지면 쪽 이동이 나타납니다. */
export const PAGE_SIZE = 15

/**
 * 화면에 보여 줄 쪽 번호를 만듭니다. 첫 쪽, 끝 쪽, 현재 쪽과 그 양옆만 남기고 나머지는 null(줄임표)입니다.
 * 한 쪽만 건너뛰게 되면 줄임표 대신 그 번호를 보여 줍니다.
 */
export function pageWindow(page: number, totalPages: number): (number | null)[] {
  const result: (number | null)[] = []
  let last = 0
  for (let p = 1; p <= totalPages; p++) {
    if (p !== 1 && p !== totalPages && Math.abs(p - page) > 1) {
      continue
    }
    if (p - last === 2) {
      result.push(p - 1)
    } else if (p - last > 2) {
      result.push(null)
    }
    result.push(p)
    last = p
  }
  return result
}

/**
 * 서버가 전체를 돌려주는 목록을 화면에서 나눠 보여 줍니다.
 * 현재 쪽은 주소의 page 값에 두어, 문서를 열었다가 뒤로 와도 보던 쪽에 머뭅니다.
 * 지워서 쪽 수가 줄면 마지막 쪽으로 맞춥니다.
 */
export function usePagedList<T>(items: T[], pageSize = PAGE_SIZE) {
  const [params, setParams] = useSearchParams()
  const listRef = useRef<HTMLDivElement>(null)
  const totalPages = Math.max(1, Math.ceil(items.length / pageSize))
  const requested = Number(params.get('page'))
  const page = Number.isInteger(requested) && requested >= 1 ? Math.min(requested, totalPages) : 1

  const goTo = (next: number) => {
    setParams(
      (prev) => {
        const updated = new URLSearchParams(prev)
        if (next <= 1) {
          updated.delete('page')
        } else {
          updated.set('page', String(next))
        }
        return updated
      },
      { replace: true },
    )
    listRef.current?.scrollIntoView({ block: 'start' })
  }

  return {
    page,
    totalPages,
    pageItems: items.slice((page - 1) * pageSize, page * pageSize),
    goTo,
    listRef,
  }
}
