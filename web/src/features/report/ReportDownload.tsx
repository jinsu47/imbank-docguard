import { useState } from 'react'
import { ApiError } from '@/api/client'
import { analysisApi, type ExportFormat } from '@/api/endpoints'
import { Button } from '@/components/ui/button'

const NAMES: Record<ExportFormat, string> = {
  md: '민감정보 분석보고서.md',
  json: '분석결과.json',
  pdf: '가림본.pdf',
}

/** 내려받을 파일 이름. 원본 이름이 없으면(서버가 주지 않은 경우) 분석 번호로 만듭니다. */
export function exportFileName(
  fileName: string | undefined,
  id: string,
  format: ExportFormat,
): string {
  const stem = fileName?.replace(/\.[^.]+$/, '').trim() || `분석 ${id.slice(0, 8)}`
  return `${stem}_${NAMES[format]}`
}

function save(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = name
  document.body.append(link)
  link.click()
  link.remove()
  // 브라우저가 내려받기를 시작할 시간을 둔 뒤 메모리를 돌려줍니다.
  setTimeout(() => URL.revokeObjectURL(url), 10_000)
}

interface MaskNotice {
  areaMasked: number
  unlocated: number
}

/** 보고서·결과·가린 PDF 내려받기 (F16, F13). 어느 파일에도 가리지 않은 원문값을 넣지 않습니다. */
export function ReportDownload({
  analysisId,
  fileName,
  contentType,
}: {
  analysisId: string
  fileName?: string
  contentType?: string
}) {
  const [pending, setPending] = useState<ExportFormat>()
  const [error, setError] = useState<string>()
  const [notice, setNotice] = useState<MaskNotice>()

  const download = async (format: ExportFormat) => {
    setPending(format)
    setError(undefined)
    setNotice(undefined)
    try {
      const { blob, headers } = await analysisApi.export(analysisId, format)
      save(blob, exportFileName(fileName, analysisId, format))
      if (format === 'pdf') {
        setNotice({
          areaMasked: Number(headers.get('X-Area-Masked-Count') ?? 0),
          unlocated: Number(headers.get('X-Unlocated-Count') ?? 0),
        })
      }
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '파일을 내려받지 못했습니다.')
    } finally {
      setPending(undefined)
    }
  }

  const button = (format: ExportFormat, label: string, title: string) => (
    <Button
      size="sm"
      variant="outline"
      disabled={pending != null}
      title={title}
      onClick={() => download(format)}
    >
      {pending === format ? '준비 중…' : label}
    </Button>
  )

  return (
    <div className="grid justify-items-end gap-1">
      <div className="flex flex-wrap justify-end gap-1.5">
        {button(
          'md',
          '.md 받기',
          '보고서를 Markdown 파일로 내려받습니다. 원문값은 들어가지 않습니다.',
        )}
        {button(
          'json',
          '.json 받기',
          '판정, 발견 항목, 근거 위치를 JSON 파일로 내려받습니다. 원문값은 들어가지 않습니다.',
        )}
        {contentType === 'application/pdf' &&
          button(
            'pdf',
            '가린 PDF 받기',
            '발견한 값을 지운 PDF 를 내려받습니다. 쪽을 이미지로 바꾸므로 글자를 선택할 수 없습니다.',
          )}
      </div>
      {error && (
        <p role="alert" className="text-xs text-destructive">
          {error}
        </p>
      )}
      {notice && (
        <div role="status" className="max-w-sm text-right text-xs text-muted-foreground">
          {notice.unlocated > 0 && (
            <p className="font-semibold text-destructive">
              위치를 찾지 못한 {notice.unlocated}건은 가리지 못했습니다.
            </p>
          )}
          {notice.areaMasked > 0 && (
            <p>값의 정확한 위치가 없는 {notice.areaMasked}건은 그 영역 전체를 가렸습니다.</p>
          )}
          <p>AI가 찾지 못한 정보는 남아 있을 수 있으니 공유하기 전에 직접 확인해 주세요.</p>
        </div>
      )}
    </div>
  )
}
