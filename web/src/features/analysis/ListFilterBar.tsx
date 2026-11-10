import type { ReactNode } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'
import type { FilterOption, FilterToggleProps, ListFilterControls } from './listFilter'

/** 목록 위의 검색창과 등급·검토 상태 선택. 누른 항목에 맞는 문서가 목록 위로 올라옵니다. */
export function ListFilterBar({
  filter,
  shown,
  total,
  placeholder,
}: {
  filter: ListFilterControls
  shown: number
  total: number
  placeholder: string
}) {
  const { query, setQuery, gradeOptions, reviewOptions, grade, review } = filter
  const { toggleGrade, toggleReview, only, setOnly, clear, hasSelection } = filter
  return (
    <div className="grid gap-3 border bg-white px-5 py-4">
      <Input
        type="search"
        aria-label="문서 검색"
        placeholder={placeholder}
        value={query}
        onChange={(e) => setQuery(e.target.value)}
      />
      <ChipGroup label="위험 등급" options={gradeOptions} selected={grade} onToggle={toggleGrade} />
      <ChipGroup
        label="사람 검토"
        options={reviewOptions}
        selected={review}
        onToggle={toggleReview}
      />
      {(hasSelection || query.trim() !== '') && (
        <div className="flex flex-wrap items-center gap-x-4 gap-y-2 text-sm">
          {hasSelection && (
            <label className="flex items-center gap-2">
              <input type="checkbox" checked={only} onChange={(e) => setOnly(e.target.checked)} />
              고른 항목만 보기
            </label>
          )}
          <span className="text-muted-foreground">
            전체 {total}건 중 {shown}건
          </span>
          <Button variant="outline" size="sm" onClick={clear}>
            조건 지우기
          </Button>
        </div>
      )}
    </div>
  )
}

function ChipGroup<V extends string>({
  label,
  options,
  selected,
  onToggle,
}: {
  label: string
  options: FilterOption<V>[]
  selected?: V
  onToggle: (value: V) => void
}) {
  if (options.length === 0) {
    return null
  }
  return (
    <div role="group" aria-label={label} className="flex flex-wrap items-center gap-1.5">
      <span className="mr-1 text-sm font-semibold">{label}</span>
      {options.map((o) => (
        <Button
          key={o.value}
          variant="outline"
          size="sm"
          aria-pressed={o.value === selected}
          className={cn(
            o.value === selected &&
              'border-brand-navy bg-brand-navy text-white hover:bg-brand-navy hover:text-white',
          )}
          onClick={() => onToggle(o.value)}
        >
          {o.label} {o.count}건
        </Button>
      ))}
    </div>
  )
}

/** 목록 행의 등급·검토 상태 표시를 누를 수 있게 합니다. 고를 수 없는 값이면 그대로 보여 줍니다. */
export function FilterToggle({
  enabled,
  pressed,
  onToggle,
  children,
}: FilterToggleProps & { children: ReactNode }) {
  if (!enabled) {
    return <>{children}</>
  }
  return (
    <button
      type="button"
      aria-pressed={pressed}
      title="누르면 같은 항목을 목록 위로 올립니다"
      className={cn(
        'cursor-pointer rounded-4xl outline-offset-2 hover:opacity-75',
        pressed && 'outline-2 outline-brand-navy',
      )}
      onClick={onToggle}
    >
      {children}
    </button>
  )
}

export function ListFilterEmpty() {
  return (
    <div className="border border-dashed bg-muted p-12 text-center text-sm text-muted-foreground">
      조건에 맞는 문서가 없습니다.
    </div>
  )
}
