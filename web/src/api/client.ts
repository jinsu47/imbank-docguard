import type { ErrorBody } from './types'

// 모든 API 호출은 이 래퍼를 거칩니다. (CLAUDE.md 6장)
// - 세션 쿠키를 같은 출처로 보냅니다.
// - 상태를 바꾸는 요청에는 XSRF-TOKEN 쿠키 값을 X-XSRF-TOKEN 헤더로 붙입니다. 쿠키가 없으면 먼저 발급받습니다.
// - 401 이면 등록된 처리기(로그인 화면 이동)를 부릅니다.

const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly requestId?: string

  constructor(status: number, body: Partial<ErrorBody>) {
    super(body.message ?? '잠시 후 다시 시도해 주세요.')
    this.name = 'ApiError'
    this.status = status
    this.code = body.code ?? 'UNKNOWN'
    this.requestId = body.requestId
  }
}

let unauthorizedHandler: (() => void) | null = null

/** 401 응답을 받았을 때 부를 함수를 등록합니다(로그인 화면으로 이동). */
export function onUnauthorized(handler: (() => void) | null) {
  unauthorizedHandler = handler
}

function readCookie(name: string): string | null {
  const match = document.cookie.split('; ').find((part) => part.startsWith(`${name}=`))
  return match ? decodeURIComponent(match.slice(name.length + 1)) : null
}

async function ensureCsrfToken(force = false): Promise<string> {
  let token = force ? null : readCookie(CSRF_COOKIE)
  if (!token) {
    await fetch('/api/auth/csrf', { credentials: 'same-origin' })
    token = readCookie(CSRF_COOKIE)
  }
  if (!token) {
    throw new ApiError(0, { code: 'CSRF_UNAVAILABLE', message: '보안 토큰을 받지 못했습니다.' })
  }
  return token
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PATCH' | 'DELETE'
  json?: unknown
  form?: FormData
  signal?: AbortSignal
  /** 401 이어도 로그인 화면으로 보내지 않습니다(로그인 확인용). */
  skipUnauthorizedRedirect?: boolean
}

async function send(path: string, options: RequestOptions, retried = false): Promise<Response> {
  const method = options.method ?? 'GET'
  const headers: Record<string, string> = { Accept: 'application/json' }
  let body: BodyInit | undefined
  if (options.json !== undefined) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(options.json)
  } else if (options.form) {
    body = options.form
  }
  if (method !== 'GET') {
    headers[CSRF_HEADER] = await ensureCsrfToken(retried)
  }
  const response = await fetch(path, {
    method,
    headers,
    body,
    credentials: 'same-origin',
    signal: options.signal,
  })
  if (!response.ok) {
    const error = await toError(response)
    // 토큰이 만료되었으면 새로 받아 한 번만 다시 보냅니다.
    if (error.code === 'CSRF_INVALID' && !retried) {
      return send(path, options, true)
    }
    if (error.status === 401 && !options.skipUnauthorizedRedirect) {
      unauthorizedHandler?.()
    }
    throw error
  }
  return response
}

async function toError(response: Response): Promise<ApiError> {
  try {
    const body = (await response.json()) as Partial<ErrorBody>
    return new ApiError(response.status, body)
  } catch {
    return new ApiError(response.status, {})
  }
}

/** JSON 응답을 받는 요청. 본문이 없으면 undefined 를 돌려줍니다. */
export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const response = await send(path, options)
  if (response.status === 204 || response.headers.get('Content-Length') === '0') {
    return undefined as T
  }
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}

/** 파일과 응답 헤더가 함께 필요한 경우(가린 PDF 의 가림 건수 등). */
export async function apiFile(
  path: string,
  options: RequestOptions = {},
): Promise<{ blob: Blob; headers: Headers }> {
  const response = await send(path, options)
  return { blob: await response.blob(), headers: response.headers }
}

/** 파일 응답(원본 문서). */
export async function apiBlob(path: string, options: RequestOptions = {}): Promise<Blob> {
  const response = await send(path, options)
  return response.blob()
}
