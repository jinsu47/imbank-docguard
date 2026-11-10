import { uploadProblem } from '@/features/analysis/uploadRules'
import { passwordProblem, safeNext } from '@/features/auth/rules'

describe('safeNext', () => {
  it('같은 사이트 안의 경로만 허용한다', () => {
    expect(safeNext('/analyses/1')).toBe('/analyses/1')
    expect(safeNext('//evil.example')).toBe('/')
    expect(safeNext('https://evil.example')).toBe('/')
    expect(safeNext(null)).toBe('/')
  })
})

describe('passwordProblem', () => {
  it('10자 이상, 영문과 숫자, 이메일과 다름', () => {
    expect(passwordProblem('short1', 'a@b.c')).not.toBeNull()
    expect(passwordProblem('onlyletters', 'a@b.c')).not.toBeNull()
    expect(passwordProblem('myname1234', 'myname1234@test.local')).not.toBeNull()
    expect(passwordProblem('goodpass123', 'a@b.c')).toBeNull()
  })
})

describe('uploadProblem', () => {
  it('형식과 빈 파일만 미리 확인하고, 크기는 제한하지 않는다', () => {
    expect(uploadProblem({ name: 'a.pdf', size: 10, type: 'application/pdf' })).toBeNull()
    expect(uploadProblem({ name: 'a.docx', size: 10, type: 'application/msword' })).not.toBeNull()
    expect(uploadProblem({ name: 'a.pdf', size: 0, type: 'application/pdf' })).not.toBeNull()
    expect(uploadProblem({ name: 'a.png', size: 800 * 1024 * 1024, type: 'image/png' })).toBeNull()
  })
})
