import type { ReactNode } from 'react'
import { HeroArt } from '@/components/HeroArt'
import { SERVICE_NAME } from '@/lib/brand'

/** 로그인·가입 화면 틀: 왼쪽 어두운 소개, 오른쪽 입력 */
export function AuthShell({
  title,
  description,
  children,
}: {
  title: string
  description?: string
  children: ReactNode
}) {
  return (
    <main className="grid min-h-svh lg:grid-cols-[1.1fr_1fr]">
      <section className="dg-hero hidden flex-col justify-center px-16 lg:flex">
        <HeroArt />
        <div className="relative">
          <p className="text-xl font-extrabold">{SERVICE_NAME}</p>
          <p className="mt-8 text-4xl leading-snug font-bold tracking-tight xl:text-5xl xl:leading-tight">
            문서를 보내기 전에,
            <br />
            민감정보부터 확인하세요
          </p>
          <p className="mt-6 text-lg text-white/80">
            위험 등급과 가려야 할 위치를 문서 위에서 바로 보여 드립니다.
          </p>
        </div>
      </section>
      <section className="flex items-center justify-center px-6 py-12">
        <div className="w-full max-w-md">
          <p className="mb-10 text-lg font-extrabold lg:hidden">{SERVICE_NAME}</p>
          <h1 className="text-3xl font-bold tracking-tight">{title}</h1>
          {description && <p className="mt-2 text-muted-foreground">{description}</p>}
          <div className="mt-8">{children}</div>
        </div>
      </section>
    </main>
  )
}
