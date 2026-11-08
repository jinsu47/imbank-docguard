import type { LucideIcon } from 'lucide-react'
import { ChevronLeft, ChevronRight, Plus } from 'lucide-react'
import { Tabs as TabsPrimitive } from 'radix-ui'
import { type ReactNode, useState } from 'react'
import { Link } from 'react-router'
import { HeroArt } from '@/components/HeroArt'
import { cn } from '@/lib/utils'

// 메인화면 구성 요소: 전면 배너, 배너에 걸친 탭 바, 색면 타일, 어두운 안내 칸, 선으로 나눈 소개 카드

export function MainHero({ subtitle }: { subtitle: string }) {
  return (
    <section className="dg-hero">
      <HeroArt />
      <div className="relative mx-auto flex h-[26rem] max-w-6xl flex-col items-center justify-center px-4 pb-14 text-center lg:h-[33rem]">
        <h1 className="text-4xl leading-tight font-bold tracking-tight lg:text-5xl">
          문서를 보내기 전에, <br className="sm:hidden" />
          민감정보부터 확인하세요
        </h1>
        <p className="mt-6 text-lg font-medium text-white/90 lg:text-xl">{subtitle}</p>
      </div>
    </section>
  )
}

const TONES = {
  blue: 'bg-tile-blue text-white',
  sky: 'bg-tile-sky text-[#17324a]',
  mint: 'bg-tile-mint text-[#123c34]',
  teal: 'bg-tile-teal text-white',
  gray: 'bg-tile-gray text-foreground',
  white: 'bg-white text-foreground',
}

export interface TileSpec {
  key: string
  label: string
  tone: keyof typeof TONES
  icon?: LucideIcon
  /** 아이콘 대신 크게 보여 줄 값(건수 등) */
  value?: string | number
  to?: string
  onClick?: () => void
  disabled?: boolean
}

/** 각진 색면 타일. to 면 링크, onClick 이면 버튼, 둘 다 없으면 정보 표시 */
export function Tile({ tile }: { tile: TileSpec }) {
  const Icon = tile.icon
  const body = (
    <>
      {tile.value !== undefined ? (
        <span className="text-4xl font-bold tracking-tight tabular-nums">{tile.value}</span>
      ) : (
        Icon && <Icon className="size-12" strokeWidth={1.4} aria-hidden="true" />
      )}
      <span className="font-bold">{tile.label}</span>
    </>
  )
  const className = cn(
    'flex h-full min-h-[10.5rem] w-full flex-col items-center justify-center gap-3 p-4 text-center transition-[filter] duration-200',
    TONES[tile.tone],
    (tile.to || tile.onClick) && 'hover:brightness-95',
  )
  if (tile.to) {
    return (
      <Link to={tile.to} className={className}>
        {body}
      </Link>
    )
  }
  if (tile.onClick) {
    return (
      <button type="button" className={className} onClick={tile.onClick} disabled={tile.disabled}>
        {body}
      </button>
    )
  }
  return <div className={className}>{body}</div>
}

export interface ListRow {
  key: string
  to?: string
  title: string
  aside?: ReactNode
  meta?: string
}

/** 제목과 더보기가 있는 짧은 목록 */
export function ListPanel({
  title,
  moreTo,
  moreLabel,
  rows,
  emptyText,
}: {
  title: string
  moreTo?: string
  moreLabel?: string
  rows: ListRow[]
  emptyText: string
}) {
  return (
    <section className="bg-white px-6 py-5">
      <div className="mb-4 flex items-center justify-between">
        <h2 className="font-bold">{title}</h2>
        {moreTo && (
          <Link
            to={moreTo}
            aria-label={moreLabel ?? `${title} 더보기`}
            className="p-1 hover:opacity-70"
          >
            <Plus className="size-5" />
          </Link>
        )}
      </div>
      {rows.length === 0 ? (
        <p className="py-4 text-sm text-muted-foreground">{emptyText}</p>
      ) : (
        <ul className="grid gap-3">
          {rows.map((row) => (
            <li key={row.key} className="flex items-center justify-between gap-3 text-sm">
              {row.to ? (
                <Link to={row.to} className="min-w-0 truncate underline-offset-4 hover:underline">
                  {row.title}
                </Link>
              ) : (
                <span className="min-w-0 truncate">{row.title}</span>
              )}
              <span className="flex shrink-0 items-center gap-3">
                {row.aside}
                {row.meta && <span className="text-muted-foreground tabular-nums">{row.meta}</span>}
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

export interface MainTab {
  value: string
  label: string
  icon: LucideIcon
  tiles: TileSpec[]
  center: ReactNode
}

/** 배너에 걸친 탭 바와 그 아래 격자. 오른쪽 안내 칸은 탭과 관계없이 그대로입니다. */
export function MainTabs({ tabs, aside }: { tabs: MainTab[]; aside: ReactNode }) {
  return (
    <TabsPrimitive.Root
      defaultValue={tabs[0].value}
      className="relative z-10 mx-auto -mt-14 max-w-6xl px-4"
    >
      <TabsPrimitive.List aria-label="메인 메뉴" className="dg-tabbar grid grid-cols-3">
        {tabs.map((tab) => {
          const Icon = tab.icon
          return (
            <TabsPrimitive.Trigger
              key={tab.value}
              value={tab.value}
              className="flex h-14 items-center justify-center gap-2 border-r border-white/40 text-sm font-semibold text-white transition-colors last:border-r-0 hover:bg-white/10 data-[state=active]:bg-white data-[state=active]:text-black sm:text-base"
            >
              <Icon className="size-4" aria-hidden="true" />
              {tab.label}
            </TabsPrimitive.Trigger>
          )
        })}
      </TabsPrimitive.List>
      <div className="mt-8 grid border lg:grid-cols-[2.5fr_1fr]">
        <div>
          {tabs.map((tab) => (
            <TabsPrimitive.Content
              key={tab.value}
              value={tab.value}
              className="grid h-full lg:grid-cols-[1fr_1.5fr]"
            >
              <div className="grid grid-cols-2">
                {tab.tiles.map((tile) => (
                  <Tile key={tile.key} tile={tile} />
                ))}
              </div>
              <div className="flex flex-col justify-between">{tab.center}</div>
            </TabsPrimitive.Content>
          ))}
        </div>
        {aside}
      </div>
    </TabsPrimitive.Root>
  )
}

/** 어두운 안내 칸: 이전·다음으로 안내 문구를 넘깁니다(자동으로 넘어가지 않음). */
export function NoticePanel({ notices }: { notices: { title: string; body: string }[] }) {
  const [index, setIndex] = useState(0)
  const notice = notices[index]
  const move = (delta: number) => setIndex((index + delta + notices.length) % notices.length)
  return (
    <section className="flex flex-col bg-brand-slate p-6 text-white">
      <div className="flex items-center justify-between">
        <h2 className="font-bold">이용 안내</h2>
        <div className="flex items-center gap-1 text-sm tabular-nums">
          <button
            type="button"
            aria-label="이전 안내"
            onClick={() => move(-1)}
            className="p-1 hover:opacity-70"
          >
            <ChevronLeft className="size-5" />
          </button>
          <span aria-live="polite">
            {index + 1} / {notices.length}
          </span>
          <button
            type="button"
            aria-label="다음 안내"
            onClick={() => move(1)}
            className="p-1 hover:opacity-70"
          >
            <ChevronRight className="size-5" />
          </button>
        </div>
      </div>
      <div className="mt-5 flex-1 bg-tile-mint p-6 text-[#123c34]">
        <p className="text-xl leading-snug font-bold">{notice.title}</p>
        <p className="mt-3 text-sm leading-relaxed">{notice.body}</p>
      </div>
    </section>
  )
}

export interface FeatureCard {
  icon: LucideIcon
  category: string
  title: string
  to: string
}

/** 선으로 나눈 소개 카드. 올리면 동그라미가 진한 색으로 바뀝니다. */
export function FeatureCards({ heading, cards }: { heading: string; cards: FeatureCard[] }) {
  return (
    <section className="mx-auto max-w-6xl px-4 py-16">
      <h2 className="mb-6 text-2xl font-bold tracking-tight">{heading}</h2>
      <div className="grid border md:grid-cols-3 md:divide-x">
        {cards.map((card) => {
          const Icon = card.icon
          return (
            <Link
              key={card.title}
              to={card.to}
              className="group flex items-center gap-4 border-b p-7 last:border-b-0 md:border-b-0"
            >
              <span className="grid size-16 shrink-0 place-items-center rounded-full bg-tile-gray transition-colors duration-300 group-hover:bg-brand-navy group-hover:text-white">
                <Icon className="size-8" strokeWidth={1.4} aria-hidden="true" />
              </span>
              <span className="min-w-0">
                <span className="block text-sm font-semibold text-muted-foreground">
                  {card.category}
                </span>
                <strong className="block font-bold transition-colors duration-300 group-hover:text-brand-navy">
                  {card.title}
                </strong>
              </span>
            </Link>
          )
        })}
      </div>
    </section>
  )
}
