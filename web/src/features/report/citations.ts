// 보고서 문장의 【†n】 표시를 나눕니다. 화면은 문자열을 HTML 로 넣지 않고 조각마다 컴포넌트로 그립니다(SECURITY.md 4장).

export type ReportPart = { type: 'text'; text: string } | { type: 'cite'; index: number }

const CITATION = /【†(\d+)】/g

export function splitCitations(text: string): ReportPart[] {
  const parts: ReportPart[] = []
  let last = 0
  for (const match of text.matchAll(CITATION)) {
    const start = match.index ?? 0
    if (start > last) {
      parts.push({ type: 'text', text: text.slice(last, start) })
    }
    parts.push({ type: 'cite', index: Number(match[1]) })
    last = start + match[0].length
  }
  if (last < text.length) {
    parts.push({ type: 'text', text: text.slice(last) })
  }
  return parts
}
