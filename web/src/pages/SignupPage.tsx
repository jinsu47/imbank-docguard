import { useMutation } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { ApiError } from '@/api/client'
import { authApi } from '@/api/endpoints'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { AuthShell } from '@/features/auth/AuthShell'
import { passwordProblem } from '@/features/auth/rules'

// 회원가입 (F01). 가입하면 항상 점검 담당자 역할이 됩니다.
export default function SignupPage() {
  const navigate = useNavigate()
  const [displayName, setDisplayName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [agreed, setAgreed] = useState(false)
  const [touched, setTouched] = useState(false)

  const signup = useMutation({
    mutationFn: () => authApi.signup({ displayName, email, password, agreed }),
    onSuccess: () => navigate('/login', { replace: true, state: { signedUp: true } }),
  })

  const problem = passwordProblem(password, email)

  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    setTouched(true)
    if (problem || !agreed) return
    signup.mutate()
  }

  const error = signup.error instanceof ApiError ? signup.error : null

  return (
    <AuthShell
      title="회원가입"
      description="가입하면 점검 담당자 역할로 문서를 올리고 결과를 볼 수 있습니다."
    >
      <form className="grid gap-5" onSubmit={onSubmit} noValidate>
        <div className="grid gap-2">
          <Label htmlFor="displayName">이름</Label>
          <Input
            id="displayName"
            maxLength={50}
            className="h-11"
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            required
          />
        </div>
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
            autoComplete="new-password"
            className="h-11"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            aria-invalid={touched && problem ? true : undefined}
            required
          />
          <p
            className={
              touched && problem ? 'text-sm text-destructive' : 'text-sm text-muted-foreground'
            }
          >
            {touched && problem ? problem : '10자 이상, 영문과 숫자를 모두 넣어 주세요.'}
          </p>
        </div>
        <section className="border bg-muted p-4 text-sm leading-relaxed">
          <h2 className="font-bold">개인정보 처리 안내</h2>
          <ul className="mt-2 list-disc space-y-1 pl-5 text-muted-foreground">
            <li>대회가 제공한 합성 문서만 올려 주세요. 실제 고객 문서는 올리지 않습니다.</li>
            <li>
              올린 문서는 분석을 위해 외부 서비스(Upstage)로 보내며, 분석이 끝나면 그쪽 파일을
              지웁니다.
            </li>
            <li>원본과 상세 결과는 암호화해 보관하고, 보관 기간(기본 7일)이 지나면 지웁니다.</li>
          </ul>
          <label className="mt-3 flex items-center gap-2 font-medium">
            <input type="checkbox" checked={agreed} onChange={(e) => setAgreed(e.target.checked)} />
            위 안내를 읽고 동의합니다.
          </label>
          {touched && !agreed && <p className="mt-1 text-destructive">안내에 동의해 주세요.</p>}
        </section>
        {signup.isError && (
          <Alert variant="destructive">
            <AlertDescription>{error?.message ?? '가입하지 못했습니다.'}</AlertDescription>
          </Alert>
        )}
        <Button
          type="submit"
          className="h-12 w-full text-base font-semibold"
          disabled={signup.isPending}
        >
          {signup.isPending ? '가입 중…' : '가입하기'}
        </Button>
      </form>
      <p className="mt-6 text-center text-sm text-muted-foreground">
        이미 계정이 있으면{' '}
        <Link
          to="/login"
          className="font-semibold text-foreground underline-offset-4 hover:underline"
        >
          로그인
        </Link>
      </p>
    </AuthShell>
  )
}
