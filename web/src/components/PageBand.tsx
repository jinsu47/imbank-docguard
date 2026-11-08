import type { ReactNode } from 'react'
import { HeroArt } from '@/components/HeroArt'

/** 하위 화면 맨 위의 어두운 띠: 화면 제목과 한 줄 설명 */
export function PageBand({ title, description }: { title: string; description?: ReactNode }) {
  return (
    <section className="dg-hero">
      <HeroArt />
      <div className="relative mx-auto max-w-6xl px-4 py-12 text-center lg:py-14">
        <h1 className="text-3xl font-bold tracking-tight lg:text-4xl">{title}</h1>
        {description && <p className="mt-3 text-white/80">{description}</p>}
      </div>
    </section>
  )
}

/** 띠 아래 본문 영역 */
export function PageBody({ children, wide }: { children: ReactNode; wide?: boolean }) {
  return (
    <main className={`mx-auto grid gap-6 px-4 py-10 ${wide ? 'max-w-[1400px]' : 'max-w-6xl'}`}>
      {children}
    </main>
  )
}
