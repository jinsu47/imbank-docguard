import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Menu, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router'
import { onUnauthorized } from '@/api/client'
import { authApi } from '@/api/endpoints'
import type { Role } from '@/api/types'
import { ME_QUERY_KEY, ROLE_LABELS, useMe } from '@/features/auth/useMe'
import { SERVICE_NAME } from '@/lib/brand'
import { SHOW_ACCURACY } from '@/lib/features'

/** 역할별 상단 메뉴 (USER_FLOW.md 2장). 메뉴 숨김은 편의 기능이고 권한 확인은 서버가 합니다. */
const ACCURACY_LINK = SHOW_ACCURACY ? [{ to: '/demo/accuracy', label: '시연 정확도' }] : []

const MENUS: Record<Role, { to: string; label: string }[]> = {
  INSPECTOR: [{ to: '/analyses', label: '내 분석' }, ...ACCURACY_LINK],
  REVIEWER: [
    { to: '/analyses', label: '내 분석' },
    { to: '/reviews', label: '검토 대기열' },
    ...ACCURACY_LINK,
  ],
  ADMIN: [
    { to: '/analyses', label: '내 분석' },
    { to: '/reviews', label: '검토 대기열' },
    { to: '/admin/analyses', label: '분석 현황' },
    { to: '/admin/users', label: '계정 관리' },
    { to: '/admin/audit', label: '감사 기록' },
  ],
}

/** 로그인 후 화면의 공통 틀: 고정 상단 메뉴, 본문, 검은 푸터. */
export function AppLayout() {
  const { data: me } = useMe()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const location = useLocation()
  const [menuOpen, setMenuOpen] = useState(false)

  // 세션이 만료되어 API 가 401 을 돌려주면 로그인 화면으로 보내고, 로그인 후 원래 화면으로 돌아옵니다.
  useEffect(() => {
    onUnauthorized(() => {
      queryClient.setQueryData(ME_QUERY_KEY, null)
      navigate(`/login?next=${encodeURIComponent(location.pathname + location.search)}`, {
        replace: true,
      })
    })
    return () => onUnauthorized(null)
  }, [queryClient, navigate, location.pathname, location.search])

  const logout = useMutation({
    mutationFn: authApi.logout,
    onSettled: () => {
      queryClient.clear()
      navigate('/login', { replace: true })
    },
  })

  const links = me ? MENUS[me.role] : []

  return (
    <div className="flex min-h-svh flex-col bg-background">
      <header className="sticky top-0 z-40 border-b bg-white">
        <div className="mx-auto flex h-[var(--header-h)] max-w-[1600px] items-center justify-between gap-4 px-4 lg:px-8">
          <Link
            to="/"
            className="text-lg font-extrabold tracking-tight whitespace-nowrap lg:text-xl"
          >
            {SERVICE_NAME}
          </Link>
          <nav aria-label="주 메뉴" className="hidden lg:block">
            <ul className="flex">
              {links.map((link) => (
                <li key={link.to}>
                  <NavLink to={link.to} className="dg-gnb-link">
                    {link.label}
                  </NavLink>
                </li>
              ))}
            </ul>
          </nav>
          <div className="flex items-center gap-4">
            {me && (
              <div className="hidden items-center gap-4 text-sm lg:flex">
                <span className="whitespace-nowrap text-muted-foreground">
                  {me.displayName} · {ROLE_LABELS[me.role]}
                </span>
                <button
                  type="button"
                  className="font-semibold whitespace-nowrap underline-offset-4 hover:underline"
                  onClick={() => logout.mutate()}
                  disabled={logout.isPending}
                >
                  로그아웃
                </button>
              </div>
            )}
            <button
              type="button"
              className="p-1 lg:hidden"
              aria-label="전체 메뉴 열기"
              aria-expanded={menuOpen}
              onClick={() => setMenuOpen(true)}
            >
              <Menu className="size-7" />
            </button>
          </div>
        </div>
      </header>

      {menuOpen && (
        <FullMenu
          links={links}
          userLabel={me ? `${me.displayName} · ${ROLE_LABELS[me.role]}` : ''}
          onClose={() => setMenuOpen(false)}
          onLogout={() => logout.mutate()}
        />
      )}

      <div className="flex-1">
        <Outlet />
      </div>
      <SiteFooter />
    </div>
  )
}

/** 좁은 화면의 전체 메뉴: 화면을 덮는 어두운 판 */
function FullMenu({
  links,
  userLabel,
  onClose,
  onLogout,
}: {
  links: { to: string; label: string }[]
  userLabel: string
  onClose: () => void
  onLogout: () => void
}) {
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKey)
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', onKey)
      document.body.style.overflow = ''
    }
  }, [onClose])

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="전체 메뉴"
      className="fixed inset-0 z-50 flex flex-col bg-brand-ink px-6 py-5 text-white lg:hidden"
    >
      <div className="flex items-center justify-between">
        <span className="text-lg font-extrabold">{SERVICE_NAME}</span>
        <button type="button" aria-label="전체 메뉴 닫기" autoFocus onClick={onClose}>
          <X className="size-7" />
        </button>
      </div>
      <nav className="mt-10 grid gap-1" aria-label="전체 메뉴 항목">
        <Link to="/" className="py-3 text-2xl font-bold" onClick={onClose}>
          메인
        </Link>
        {links.map((link) => (
          <NavLink key={link.to} to={link.to} className="py-3 text-2xl font-bold" onClick={onClose}>
            {link.label}
          </NavLink>
        ))}
      </nav>
      <div className="mt-auto flex items-center justify-between border-t border-white/20 pt-4 text-sm">
        <span className="text-white/70">{userLabel}</span>
        <button type="button" className="font-semibold" onClick={onLogout}>
          로그아웃
        </button>
      </div>
    </div>
  )
}

function SiteFooter() {
  return (
    <footer className="bg-brand-ink text-white">
      <div className="mx-auto flex max-w-[1600px] flex-col gap-3 px-4 py-8 lg:flex-row lg:items-start lg:gap-12 lg:px-8">
        <p className="font-extrabold whitespace-nowrap">{SERVICE_NAME}</p>
        <div className="grid gap-1 text-sm text-white/70">
          <p>iM뱅크 주최 대회 출품작입니다. 대회가 제공한 합성 문서만 처리합니다.</p>
          <p>
            분석 결과는 자체 점검 기준에 따른 참고 정보이며, 법적 판단이나 유출·위법 여부의 확정이
            아닙니다.
          </p>
        </div>
      </div>
    </footer>
  )
}
