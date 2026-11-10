import { Button } from '@/components/ui/button'
import { pageWindow } from '@/lib/pagination'
import { cn } from '@/lib/utils'

/** 목록 아래의 쪽 이동. 한 쪽에 다 들어가면 아무것도 그리지 않습니다. */
export function ListPagination({
  page,
  totalPages,
  total,
  onChange,
}: {
  page: number
  totalPages: number
  total: number
  onChange: (page: number) => void
}) {
  if (totalPages <= 1) {
    return null
  }
  return (
    <nav
      aria-label="목록 쪽 이동"
      className="flex flex-wrap items-center justify-between gap-3 text-sm"
    >
      <span className="text-muted-foreground">
        전체 {total}건 · {page} / {totalPages}쪽
      </span>
      <div className="flex flex-wrap items-center gap-1">
        <Button
          variant="outline"
          size="sm"
          disabled={page === 1}
          onClick={() => onChange(page - 1)}
        >
          이전
        </Button>
        {pageWindow(page, totalPages).map((p, i) =>
          p === null ? (
            <span key={`gap-${i}`} aria-hidden="true" className="px-1 text-muted-foreground">
              …
            </span>
          ) : (
            <Button
              key={p}
              variant="outline"
              size="sm"
              aria-label={`${p}쪽`}
              aria-current={p === page ? 'page' : undefined}
              className={cn(
                'min-w-7',
                p === page &&
                  'border-brand-navy bg-brand-navy text-white hover:bg-brand-navy hover:text-white',
              )}
              onClick={() => onChange(p)}
            >
              {p}
            </Button>
          ),
        )}
        <Button
          variant="outline"
          size="sm"
          disabled={page === totalPages}
          onClick={() => onChange(page + 1)}
        >
          다음
        </Button>
      </div>
    </nav>
  )
}
