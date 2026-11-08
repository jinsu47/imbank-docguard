import { useMutation, useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { Link, Navigate, useLocation, useNavigate, useSearchParams } from 'react-router'
import { ApiError } from '@/api/client'
import { authApi } from '@/api/endpoints'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { AuthShell } from '@/features/auth/AuthShell'
import { safeNext } from '@/features/auth/rules'
import { ME_QUERY_KEY, useMe } from '@/features/auth/useMe'

// 로그인 (F01). 실패 문구는 계정이 있든 없든 같습니다(SECURITY.md 2장).
export default function LoginPage() {
  const [params] = useSearchParams()
  const location = useLocation()
  const signedUp = (location.state as { signedUp?: boolean } | null)?.signedUp === true
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { data: me } = useMe()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const next = safeNext(params.get('next'))

  const login = useMutation({
    mutationFn: () => authApi.login(email, password),
    onSuccess: (user) => {
      queryClient.setQueryData(ME_QUERY_KEY, user)
      navigate(next, { replace: true })
    },
  })

  if (me) {
    return <Navigate to={next} replace />
  }

  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    login.mutate()
  }

  const error = login.error instanceof ApiError ? login.error : null

  return (
    <AuthShell title="로그인" description="계정으로 로그인해 문서 점검을 시작하세요.">
      {signedUp && !login.isError && (
        <Alert className="mb-5">
          <AlertDescription>가입했습니다. 방금 만든 계정으로 로그인해 주세요.</AlertDescription>
        </Alert>
      )}
      <form className="grid gap-5" onSubmit={onSubmit}>
        <div className="grid gap-2">
          <Label htmlFor="email">이메일</Label>
          <Input
            id="email"
            type="email"
            autoComplete="username"
            className="h-11"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
        </div>
        <div className="grid gap-2">
          <Label htmlFor="password">비밀번호</Label>
          <Input
            id="password"
            type="password"
            autoComplete="current-password"
            className="h-11"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </div>
        {login.isError && (
          <Alert variant="destructive">
            <AlertDescription>
              {error?.message ?? '로그인하지 못했습니다. 잠시 후 다시 시도해 주세요.'}
            </AlertDescription>
          </Alert>
        )}
        <Button
          type="submit"
          className="h-12 w-full text-base font-semibold"
          disabled={login.isPending}
        >
          {login.isPending ? '로그인 중…' : '로그인'}
        </Button>
      </form>
      <p className="mt-6 text-center text-sm text-muted-foreground">
        계정이 없으면{' '}
        <Link
          to="/signup"
          className="font-semibold text-foreground underline-offset-4 hover:underline"
        >
          회원가입
        </Link>
      </p>
    </AuthShell>
  )
}
