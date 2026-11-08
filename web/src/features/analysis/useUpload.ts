import { useMutation, useQueryClient } from '@tanstack/react-query'
import { type ChangeEvent, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { ApiError } from '@/api/client'
import { analysisApi } from '@/api/endpoints'
import { ACCEPT, uploadProblem } from './uploadRules'

/**
 * 문서 올리기 (F02). 파일을 고르면 미리 확인한 뒤 올리고, 분석 화면으로 이동해 진행 상태를 보여 줍니다.
 * 파일 선택 칸(inputProps)과 그 칸을 여는 함수(openPicker)를 돌려주므로 버튼·타일 어디서든 쓸 수 있습니다.
 */
export function useUpload() {
  const inputRef = useRef<HTMLInputElement>(null)
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [localError, setLocalError] = useState<string | null>(null)

  const upload = useMutation({
    mutationFn: (file: File) => analysisApi.create(file),
    onSuccess: ({ id }) => {
      queryClient.invalidateQueries({ queryKey: ['analyses'] })
      navigate(`/analyses/${id}`)
    },
  })

  const onChange = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return
    const problem = uploadProblem(file)
    setLocalError(problem)
    if (!problem) upload.mutate(file)
  }

  const serverError =
    upload.error instanceof ApiError
      ? upload.error.message
      : upload.error
        ? '업로드하지 못했습니다.'
        : null

  return {
    inputProps: {
      ref: inputRef,
      type: 'file' as const,
      accept: ACCEPT,
      className: 'hidden',
      'aria-label': '올릴 문서 선택',
      onChange,
    },
    openPicker: () => inputRef.current?.click(),
    isPending: upload.isPending,
    error: localError ?? serverError,
  }
}
