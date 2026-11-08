// 로그인·가입 화면 규칙. 서버도 같은 규칙으로 다시 확인합니다.

/** 로그인 후 돌아갈 경로. 같은 사이트 안의 경로만 허용합니다. */
export function safeNext(next: string | null): string {
  if (!next || !next.startsWith('/') || next.startsWith('//')) {
    return '/'
  }
  return next
}

/** 비밀번호 규칙 (SECURITY.md 2장). 서버도 같은 규칙으로 다시 확인합니다. */
export function passwordProblem(password: string, email: string): string | null {
  if (password.length < 10 || password.length > 64) return '10자 이상 64자 이하로 입력해 주세요.'
  if (!/[A-Za-z]/.test(password) || !/\d/.test(password)) return '영문과 숫자를 모두 넣어 주세요.'
  const lower = password.toLowerCase()
  const mail = email.trim().toLowerCase()
  if (mail && (lower === mail || lower === mail.split('@')[0]))
    return '이메일과 같은 비밀번호는 쓸 수 없습니다.'
  return null
}
