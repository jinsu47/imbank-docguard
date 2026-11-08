import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import type { Role } from '@/api/types'
import { FullPageMessage } from '@/components/FullPageMessage'
import ForbiddenPage from '@/pages/ForbiddenPage'
import { useMe } from './useMe'

/**
 * 로그인이 필요한 화면. 세션이 없으면 원래 경로를 기억하고 로그인 화면으로 보냅니다(USER_FLOW.md 3장).
 * 역할에 따른 화면 숨김은 편의 기능일 뿐이고, 실제 권한 확인은 서버가 합니다(SECURITY.md 3장).
 */
export function RequireAuth({ roles, children }: { roles?: Role[]; children: ReactNode }) {
  const { data: me, isPending, isError } = useMe()
  const location = useLocation()

  if (isPending) {
    return <FullPageMessage title="불러오는 중입니다." />
  }
  if (isError) {
    return (
      <FullPageMessage
        title="서버에 연결할 수 없습니다."
        description="잠시 후 다시 시도해 주세요."
      />
    )
  }
  if (!me) {
    const next = encodeURIComponent(location.pathname + location.search)
    return <Navigate to={`/login?next=${next}`} replace />
  }
  if (roles && !roles.includes(me.role)) {
    return <ForbiddenPage />
  }
  return <>{children}</>
}
