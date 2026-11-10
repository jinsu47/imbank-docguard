import { lazy, type ReactNode, Suspense } from 'react'
import { Navigate, type RouteObject } from 'react-router'
import type { Role } from '@/api/types'
import { AppLayout } from '@/components/AppLayout'
import { FullPageMessage } from '@/components/FullPageMessage'
import { RequireAuth } from '@/features/auth/RequireAuth'
import AccuracyPage from '@/pages/AccuracyPage'
import AdminAnalysesPage from '@/pages/AdminAnalysesPage'
import AdminAuditPage from '@/pages/AdminAuditPage'
import AdminUsersPage from '@/pages/AdminUsersPage'
import LoginPage from '@/pages/LoginPage'
import MainPage from '@/pages/MainPage'
import MyAnalysesPage from '@/pages/MyAnalysesPage'
import NotFoundPage from '@/pages/NotFoundPage'
import ReviewsPage from '@/pages/ReviewsPage'
import SignupPage from '@/pages/SignupPage'
import { SHOW_ACCURACY, SIGNUP_ENABLED } from '@/lib/features'

// 분석 화면은 PDF 뷰어(react-pdf)를 포함해 크므로, 처음 열 때 따로 내려받습니다.
const AnalysisPage = lazy(() => import('@/pages/AnalysisPage'))

// 문서를 올리고 결과를 보는 역할. 관리자도 점검 담당자·검토자의 기능을 모두 씁니다.
const ANALYSIS_ROLES: Role[] = ['INSPECTOR', 'REVIEWER', 'ADMIN']

function only(roles: Role[], page: ReactNode) {
  return <RequireAuth roles={roles}>{page}</RequireAuth>
}

// 화면 목록은 USER_FLOW.md 2장을 따릅니다.
export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  { path: '/signup', element: SIGNUP_ENABLED ? <SignupPage /> : <Navigate to="/login" replace /> },
  {
    element: (
      <RequireAuth>
        <AppLayout />
      </RequireAuth>
    ),
    children: [
      { path: '/', element: <MainPage /> },
      { path: '/analyses', element: only(ANALYSIS_ROLES, <MyAnalysesPage />) },
      {
        path: '/analyses/:id',
        element: only(
          ANALYSIS_ROLES,
          <Suspense fallback={<FullPageMessage title="불러오는 중입니다." />}>
            <AnalysisPage />
          </Suspense>,
        ),
      },
      { path: '/reviews', element: only(['REVIEWER', 'ADMIN'], <ReviewsPage />) },
      ...(SHOW_ACCURACY
        ? [{ path: '/demo/accuracy', element: only(ANALYSIS_ROLES, <AccuracyPage />) }]
        : []),
      { path: '/admin/users', element: only(['ADMIN'], <AdminUsersPage />) },
      { path: '/admin/audit', element: only(['ADMIN'], <AdminAuditPage />) },
      { path: '/admin/analyses', element: only(['ADMIN'], <AdminAnalysesPage />) },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]
