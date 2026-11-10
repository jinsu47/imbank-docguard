import { useQuery } from '@tanstack/react-query'
import { ApiError } from '@/api/client'
import { authApi } from '@/api/endpoints'
import type { Me } from '@/api/types'

export const ME_QUERY_KEY = ['me'] as const

/** 현재 로그인 사용자. 로그인하지 않았으면 null 입니다. */
export function useMe() {
  return useQuery<Me | null>({
    queryKey: ME_QUERY_KEY,
    queryFn: async () => {
      try {
        return await authApi.me()
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) {
          return null
        }
        throw error
      }
    },
    staleTime: 60_000,
    retry: false,
  })
}

export const ROLE_LABELS: Record<Me['role'], string> = {
  INSPECTOR: '점검 담당자',
  REVIEWER: '검토자',
  ADMIN: '관리자',
}
