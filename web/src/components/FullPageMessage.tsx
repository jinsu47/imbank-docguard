import type { ReactNode } from 'react'

export function FullPageMessage({
  title,
  description,
  children,
}: {
  title: string
  description?: string
  children?: ReactNode
}) {
  return (
    <main className="flex min-h-[60svh] flex-col items-center justify-center gap-3 px-4 text-center">
      <h1 className="text-lg font-semibold">{title}</h1>
      {description && <p className="text-sm text-muted-foreground">{description}</p>}
      {children}
    </main>
  )
}
