import { useEffect, useRef } from 'react'
import type { FindingView, RevealedFinding } from '@/api/types'
import { markKind } from '@/features/viewer/boxes'
import { cn } from '@/lib/utils'

// 보고서와 같은 쉬운 말로 보여 줍니다(내부 용어 미마스킹 등은 화면에 쓰지 않음).
const MASK_LABELS: Record<string, string> = {
  미마스킹: '가림 없음',
  부분마스킹: '일부 가림',
  전체마스킹: '전체 가림',
}

export function maskLabel(state: string): string {
  return MASK_LABELS[state] ?? state
}

/** 정보주체 역할별로 묶은 발견 항목 목록 (F05). 항목을 누르면 문서 위치로 이동합니다. */
export function FindingList({
  findings,
  revealed,
  selectedRow,
  onSelect,
}: {
  findings: FindingView[]
  revealed?: Map<number, RevealedFinding>
  selectedRow?: number
  onSelect: (rowIndex: number) => void
}) {
  const listRef = useRef<HTMLDivElement>(null)
  // 문서나 보고서에서 항목을 고르면 목록도 그 항목까지 스크롤합니다.
  useEffect(() => {
    listRef.current
      ?.querySelector<HTMLElement>('[data-selected="true"]')
      ?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
  }, [selectedRow])

  if (findings.length === 0) {
    return <p className="text-sm text-muted-foreground">보호 대상이 드러난 항목이 없습니다.</p>
  }
  const groups = new Map<string, FindingView[]>()
  for (const f of findings) {
    groups.set(f.subjectRole, [...(groups.get(f.subjectRole) ?? []), f])
  }
  return (
    <div ref={listRef} className="grid gap-4">
      {[...groups.entries()].map(([role, items]) => (
        <section key={role}>
          <h3 className="mb-2 text-sm font-bold">
            {role} · {items.length}건
          </h3>
          <ul className="grid gap-1.5">
            {items.map((f) => {
              const kind = f.page != null ? markKind(f) : 'none'
              const raw = revealed?.get(f.rowIndex)
              return (
                <li key={f.rowIndex}>
                  <button
                    type="button"
                    data-selected={selectedRow === f.rowIndex ? 'true' : undefined}
                    onClick={() => onSelect(f.rowIndex)}
                    className={cn(
                      'w-full border bg-white px-3 py-2.5 text-left text-sm transition-colors hover:bg-muted',
                      selectedRow === f.rowIndex && 'border-brand-navy ring-1 ring-brand-navy',
                    )}
                  >
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-medium">{f.itemLabel}</span>
                      <span className="shrink-0 text-xs text-muted-foreground">{f.infoType}</span>
                    </div>
                    <div className="mt-1 font-mono text-[13px] break-all">
                      {raw ? <span className="text-red-700">{raw.rawValue}</span> : f.displayValue}
                    </div>
                    <div className="mt-1 flex flex-wrap gap-x-2 text-xs text-muted-foreground">
                      <span>{maskLabel(f.maskState)}</span>
                      {f.valueNature && f.valueNature !== '해당없음' && (
                        <span>· {f.valueNature}</span>
                      )}
                      {!f.policyLinked && (
                        <span className="text-amber-700">· 정책 연결 확인 필요</span>
                      )}
                      {kind === 'none' && <span>· 문서 위치 정보 없음</span>}
                      {kind === 'area-only' && <span>· {f.page}쪽, 값 위치 없이 영역만 있음</span>}
                      {(kind === 'mask' || kind === 'already-masked') && <span>· {f.page}쪽</span>}
                    </div>
                    {raw?.reviewReason && (
                      <div className="mt-1 text-xs text-brand-navy">
                        검토 사유: {raw.reviewReason}
                      </div>
                    )}
                  </button>
                </li>
              )
            })}
          </ul>
        </section>
      ))}
    </div>
  )
}
