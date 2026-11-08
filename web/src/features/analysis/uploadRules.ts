// 업로드 전 확인 (BR05). 서버가 파일 시그니처와 쪽수로 다시 확인하므로, 여기서는 빠른 안내만 합니다.

export const MAX_UPLOAD_MB = 20
export const ACCEPT = '.pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png'

const ALLOWED_TYPES = ['application/pdf', 'image/jpeg', 'image/png']
const ALLOWED_EXTENSIONS = ['pdf', 'jpg', 'jpeg', 'png']

export function uploadProblem(file: Pick<File, 'name' | 'size' | 'type'>): string | null {
  const ext = file.name.split('.').pop()?.toLowerCase() ?? ''
  if (!ALLOWED_TYPES.includes(file.type) && !ALLOWED_EXTENSIONS.includes(ext)) {
    return 'PDF, JPG, PNG 파일만 올릴 수 있습니다.'
  }
  if (file.size === 0) {
    return '빈 파일입니다.'
  }
  if (file.size > MAX_UPLOAD_MB * 1024 * 1024) {
    return `파일은 ${MAX_UPLOAD_MB}MB 이하만 올릴 수 있습니다.`
  }
  return null
}
