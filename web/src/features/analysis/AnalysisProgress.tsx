import { Check } from 'lucide-react'
import { useEffect, useState } from 'react'
import type { AnalysisDetail } from '@/api/types'
import { cn } from '@/lib/utils'
import { progressMessage, progressPercent, STEP_LABELS } from './labels'

const RADIUS = 54
const CIRCUMFERENCE = 2 * Math.PI * RADIUS

/**
 * 분석 중 화면 (F02, US02). 돌아가는 원 안에 완료 퍼센트, 아래에 지금 하는 일을 작은 문구로 보여 주고,
 * 그 아래 칸에 워크플로 7단계를 끝난 순서대로 색칠합니다.
 * 퍼센트는 Studio 가 돌려준 끝난 단계 수로 계산합니다.
 */
export function AnalysisProgress({ detail }: { detail: AnalysisDetail }) {
  const startedAt = detail.submittedAt ?? detail.summary.createdAt
  const elapsed = useElapsedSeconds(startedAt)
  const total = detail.steps.length
  const done = detail.completedSteps.length
  const percent = progressPercent(done, total)
  const message = progressMessage(detail.summary.status, detail.steps, detail.completedSteps)
  const completed = new Set(detail.completedSteps)
  const current =
    detail.summary.status === 'RUNNING' ? detail.steps.find((s) => !completed.has(s)) : undefined

  return (
    <section className="flex flex-col items-center border bg-white px-6 py-16">
      <div
        role="progressbar"
        aria-label="분석 진행률"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={percent}
        className="relative size-52"
      >
        <svg viewBox="0 0 120 120" className="size-full" aria-hidden="true">
          <defs>
            <linearGradient id="dg-progress" x1="0" y1="0" x2="1" y2="1">
              <stop offset="0%" stopColor="var(--brand-navy)" />
              <stop offset="100%" stopColor="var(--brand-green)" />
            </linearGradient>
          </defs>
          <circle
            cx="60"
            cy="60"
            r={RADIUS}
            fill="none"
            stroke="var(--tile-gray)"
            strokeWidth="8"
          />
          <circle
            cx="60"
            cy="60"
            r={RADIUS}
            fill="none"
            stroke="url(#dg-progress)"
            strokeWidth="8"
            strokeDasharray={CIRCUMFERENCE}
            strokeDashoffset={CIRCUMFERENCE * (1 - percent / 100)}
            transform="rotate(-90 60 60)"
            className="transition-[stroke-dashoffset] duration-700 ease-out"
          />
        </svg>
        {/* 단계가 바뀌지 않는 동안에도 멈춘 것처럼 보이지 않도록 바깥에서 도는 호 */}
        <svg
          viewBox="0 0 120 120"
          className="absolute inset-0 size-full animate-spin [animation-duration:1.8s] motion-reduce:animate-none"
          aria-hidden="true"
        >
          <circle
            cx="60"
            cy="60"
            r="59"
            fill="none"
            stroke="var(--brand-green)"
            strokeWidth="1.5"
            strokeLinecap="round"
            strokeDasharray="46 325"
          />
        </svg>
        <div className="absolute inset-0 grid place-content-center">
          <span className="text-5xl font-bold tracking-tight tabular-nums">
            {percent}
            <span className="text-2xl">%</span>
          </span>
        </div>
      </div>
      <p className="mt-6 text-sm font-semibold" aria-live="polite">
        {message}
      </p>
      <p className="mt-1 text-xs text-muted-foreground tabular-nums">
        {done} / {total}단계 · {elapsed}초 경과 · 보통 40~70초 걸립니다
      </p>
      <ol
        aria-label="분석 단계"
        className="mt-10 grid w-full max-w-5xl grid-cols-2 gap-1 sm:grid-cols-4 lg:grid-cols-7"
      >
        {detail.steps.map((step, index) => {
          const isDone = completed.has(step)
          const isCurrent = step === current
          return (
            <li
              key={step}
              aria-current={isCurrent ? 'step' : undefined}
              className={cn(
                'flex min-h-14 items-center justify-center gap-1.5 px-2 py-3 text-center text-sm font-semibold transition-colors duration-500',
                isDone && 'bg-tile-teal text-white',
                isCurrent && 'animate-pulse bg-brand-navy text-white motion-reduce:animate-none',
                !isDone && !isCurrent && 'bg-tile-gray text-muted-foreground',
              )}
            >
              {isDone ? (
                <Check className="size-4 shrink-0" aria-hidden="true" />
              ) : (
                <span className="tabular-nums" aria-hidden="true">
                  {index + 1}
                </span>
              )}
              <span>
                {STEP_LABELS[step] ?? step}
                {isDone && <span className="sr-only"> 완료</span>}
                {isCurrent && <span className="sr-only"> 진행 중</span>}
              </span>
            </li>
          )
        })}
      </ol>
    </section>
  )
}

function useElapsedSeconds(from: string): number {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(timer)
  }, [])
  return Math.max(0, Math.floor((now - new Date(from).getTime()) / 1000))
}
