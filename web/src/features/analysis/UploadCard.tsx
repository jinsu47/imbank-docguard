import { FileUp } from 'lucide-react'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { MAX_UPLOAD_MB } from './uploadRules'
import { useUpload } from './useUpload'

/** 내 분석 화면의 문서 올리기 (F02) */
export function UploadCard() {
  const { inputProps, openPicker, isPending, error } = useUpload()

  return (
    <section className="border bg-white p-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-4">
          <span className="grid size-14 shrink-0 place-items-center rounded-full bg-tile-gray text-brand-navy">
            <FileUp className="size-7" strokeWidth={1.6} />
          </span>
          <div>
            <h2 className="text-lg font-bold">문서 올리기</h2>
            <p className="mt-0.5 text-sm text-muted-foreground">
              PDF, JPG, PNG 한 건 · {MAX_UPLOAD_MB}MB, 20쪽 이하 · 대회가 제공한 합성 문서만 올려
              주세요.
            </p>
          </div>
        </div>
        <input {...inputProps} />
        <Button className="h-11 px-6 font-semibold" onClick={openPicker} disabled={isPending}>
          {isPending ? '올리는 중…' : '파일 선택'}
        </Button>
      </div>
      {error && (
        <Alert variant="destructive" className="mt-4">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}
    </section>
  )
}
