import { useState } from 'react'
import { useSearchParams } from 'react-router'
import type { AnalysisStatus, ReviewStatus, RiskGrade } from '@/api/types'
import { GRADE_LABELS, REVIEW_LABELS } from './labels'

/** 목록 검색과 정렬에 쓰는 값. 내 분석, 검토 대기열, 분석 현황 목록이 함께 씁니다. */
export interface FilterableAnalysis {
  status: AnalysisStatus
  riskGrade?: RiskGrade
  reviewStatus: ReviewStatus
  fileName?: string
  documentType?: string
  ownerEmail?: string
}

export interface FilterOption<V> {
  value: V
  label: string
  count: number
}

export interface ListCriteria {
  query: string
  grade?: RiskGrade
  review?: ReviewStatus
  /** 고른 등급·검토 상태에 맞는 문서만 남깁니다. 끄면 맞는 문서를 위로 올리기만 합니다. */
  only: boolean
}

const GRADE_ORDER: RiskGrade[] = ['HIGH', 'MEDIUM', 'LOW', 'UNDECIDED', 'UNKNOWN', 'NONE']
const REVIEW_ORDER: ReviewStatus[] = ['PENDING', 'NOT_REQUIRED', 'CONFIRMED', 'DISPUTED']

function isDecided(a: FilterableAnalysis): boolean {
  return a.status === 'COMPLETED' || a.status === 'PURGED'
}

/** 판정이 끝나기 전의 문서는 등급과 검토 상태가 정해지지 않은 것으로 봅니다. */
export function gradeOf(a: FilterableAnalysis): RiskGrade | undefined {
  return isDecided(a) ? a.riskGrade : undefined
}

export function reviewOf(a: FilterableAnalysis): ReviewStatus | undefined {
  return isDecided(a) ? a.reviewStatus : undefined
}

function search<T extends FilterableAnalysis>(items: T[], query: string): T[] {
  const word = query.trim().toLowerCase()
  if (!word) {
    return items
  }
  return items.filter((a) =>
    [a.fileName, a.documentType, a.ownerEmail].some((v) => v?.toLowerCase().includes(word)),
  )
}

/**
 * 검색한 뒤, 고른 등급·검토 상태에 맞는 문서를 위로 올립니다.
 * 둘 다 고르면 둘 다 맞는 문서, 등급만 맞는 문서, 검토 상태만 맞는 문서 순서입니다.
 * 순위가 같으면 서버가 준 순서(최신순)를 유지합니다.
 */
export function arrangeAnalyses<T extends FilterableAnalysis>(
  items: T[],
  { query, grade, review, only }: ListCriteria,
): T[] {
  const searched = search(items, query)
  if (!grade && !review) {
    return searched
  }
  const gradeHit = (a: T) => gradeOf(a) === grade
  const reviewHit = (a: T) => reviewOf(a) === review
  if (only) {
    return searched.filter((a) => (!grade || gradeHit(a)) && (!review || reviewHit(a)))
  }
  const rank = (a: T) => (grade && gradeHit(a) ? 2 : 0) + (review && reviewHit(a) ? 1 : 0)
  return [...searched].sort((a, b) => rank(b) - rank(a))
}

/** 값이 한 종류뿐이면 올릴 것이 없으므로 선택지를 만들지 않습니다. 건수는 검색 결과 기준입니다. */
function optionsOf<V extends string>(
  order: V[],
  labels: Record<V, string>,
  valueOf: (a: FilterableAnalysis) => V | undefined,
  items: FilterableAnalysis[],
  searched: FilterableAnalysis[],
): FilterOption<V>[] {
  const present = order.filter((v) => items.some((a) => valueOf(a) === v))
  if (present.length < 2) {
    return []
  }
  return present.map((value) => ({
    value,
    label: labels[value],
    count: searched.filter((a) => valueOf(a) === value).length,
  }))
}

export interface FilterToggleProps {
  enabled: boolean
  pressed: boolean
  onToggle: () => void
}

export interface ListFilterControls {
  query: string
  setQuery: (query: string) => void
  gradeOptions: FilterOption<RiskGrade>[]
  reviewOptions: FilterOption<ReviewStatus>[]
  grade?: RiskGrade
  review?: ReviewStatus
  toggleGrade: (value: RiskGrade) => void
  toggleReview: (value: ReviewStatus) => void
  only: boolean
  setOnly: (only: boolean) => void
  clear: () => void
  hasSelection: boolean
}

/**
 * 목록의 검색어와 고른 조건을 관리합니다.
 * 등급·검토 상태는 주소에 두어 문서를 열었다가 뒤로 와도 유지합니다.
 * 검색어에는 파일 이름에 든 개인정보가 들어갈 수 있어 주소와 브라우저 저장소에 남기지 않습니다.
 */
export function useListFilter<T extends FilterableAnalysis>(items: T[]) {
  const [params, setParams] = useSearchParams()
  const [query, setQuery] = useState('')

  const searched = search(items, query)
  const gradeOptions = optionsOf(GRADE_ORDER, GRADE_LABELS, gradeOf, items, searched)
  const reviewOptions = optionsOf(REVIEW_ORDER, REVIEW_LABELS, reviewOf, items, searched)
  const grade = gradeOptions.find((o) => o.value === params.get('grade'))?.value
  const review = reviewOptions.find((o) => o.value === params.get('review'))?.value
  const only = params.get('only') === '1'

  // 조건이 바뀌면 순서도 바뀌므로 첫 쪽으로 돌아갑니다.
  const change = (updates: Record<string, string | undefined>) => {
    setParams(
      (prev) => {
        const updated = new URLSearchParams(prev)
        updated.delete('page')
        for (const [name, value] of Object.entries(updates)) {
          if (value === undefined) {
            updated.delete(name)
          } else {
            updated.set(name, value)
          }
        }
        return updated
      },
      { replace: true },
    )
  }

  const toggleGrade = (value: RiskGrade) => {
    const next = value === grade ? undefined : value
    change({ grade: next, ...(next || review ? {} : { only: undefined }) })
  }

  const toggleReview = (value: ReviewStatus) => {
    const next = value === review ? undefined : value
    change({ review: next, ...(next || grade ? {} : { only: undefined }) })
  }

  const controls: ListFilterControls = {
    query,
    setQuery: (next) => {
      setQuery(next)
      if (params.has('page')) {
        change({})
      }
    },
    gradeOptions,
    reviewOptions,
    grade,
    review,
    toggleGrade,
    toggleReview,
    only,
    setOnly: (next) => change({ only: next ? '1' : undefined }),
    clear: () => {
      setQuery('')
      change({ grade: undefined, review: undefined, only: undefined })
    },
    hasSelection: Boolean(grade || review),
  }

  return {
    controls,
    visible: arrangeAnalyses(items, { query, grade, review, only }),
    /** 목록 행의 등급 표시를 눌렀을 때의 동작 */
    gradeToggle: (a: FilterableAnalysis, afterToggle?: () => void): FilterToggleProps => {
      const value = gradeOf(a)
      return {
        enabled: gradeOptions.some((o) => o.value === value),
        pressed: value !== undefined && value === grade,
        onToggle: () => {
          if (value) {
            toggleGrade(value)
          }
          afterToggle?.()
        },
      }
    },
    /** 목록 행의 검토 상태 표시를 눌렀을 때의 동작 */
    reviewToggle: (a: FilterableAnalysis, afterToggle?: () => void): FilterToggleProps => {
      const value = reviewOf(a)
      return {
        enabled: reviewOptions.some((o) => o.value === value),
        pressed: value !== undefined && value === review,
        onToggle: () => {
          if (value) {
            toggleReview(value)
          }
          afterToggle?.()
        },
      }
    },
  }
}
