import type { ReportSection } from '@/api/types'
import { splitCitations } from './citations'

/** 보고서 항목 (F06). 【†n】을 누르면 문서의 해당 위치로 이동합니다. */
export function ReportView({
  sections,
  onCitation,
  activeCitation,
}: {
  sections: ReportSection[]
  onCitation: (index: number) => void
  activeCitation?: number
}) {
  if (sections.length === 0) {
    return <p className="text-sm text-muted-foreground">보고서 항목을 읽지 못했습니다.</p>
  }
  return (
    <div className="grid gap-5">
      {sections.map((section) => (
        <section key={section.title}>
          <h3 className="mb-1.5 border-l-[3px] border-brand-navy pl-2 text-sm font-bold">
            {section.title}
          </h3>
          <p className="text-sm leading-relaxed whitespace-pre-line text-foreground/90">
            {splitCitations(section.body).map((part, i) =>
              part.type === 'text' ? (
                <span key={i}>{part.text}</span>
              ) : (
                <button
                  key={i}
                  type="button"
                  onClick={() => onCitation(part.index)}
                  className={
                    'mx-0.5 inline-flex h-5 min-w-5 items-center justify-center rounded px-1 align-text-top text-[11px] font-medium ' +
                    (activeCitation === part.index
                      ? 'bg-primary text-primary-foreground'
                      : 'bg-muted text-muted-foreground hover:bg-primary/15 hover:text-foreground')
                  }
                  aria-label={`근거 ${part.index}번 위치 보기`}
                >
                  {part.index}
                </button>
              ),
            )}
          </p>
        </section>
      ))}
    </div>
  )
}
