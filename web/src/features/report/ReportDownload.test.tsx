import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { ApiError } from '@/api/client'
import { analysisApi } from '@/api/endpoints'
import { exportFileName, ReportDownload } from './ReportDownload'

describe('ReportDownload (F16, F13)', () => {
  let downloaded: string[]

  beforeEach(() => {
    downloaded = []
    URL.createObjectURL = vi.fn(() => 'blob:report')
    URL.revokeObjectURL = vi.fn()
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
      this: HTMLAnchorElement,
    ) {
      downloaded.push(this.download)
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  const file = (headers: Record<string, string> = {}) => ({
    blob: new Blob(['x']),
    headers: new Headers(headers),
  })

  it('원본 이름에서 확장자를 빼고 파일 이름을 만든다', () => {
    expect(exportFileName('여신 신청서.v2.pdf', 'abcdefgh-1', 'md')).toBe(
      '여신 신청서.v2_민감정보 분석보고서.md',
    )
    expect(exportFileName('set02.pdf', 'abcdefgh-1', 'json')).toBe('set02_분석결과.json')
    expect(exportFileName('set02.pdf', 'abcdefgh-1', 'pdf')).toBe('set02_가림본.pdf')
  })

  it('원본 이름이 없으면 분석 번호로 만든다', () => {
    expect(exportFileName(undefined, 'abcdefgh-1', 'md')).toBe(
      '분석 abcdefgh_민감정보 분석보고서.md',
    )
  })

  it('버튼을 누르면 고른 형식으로 받아 파일로 저장한다', async () => {
    const api = vi.spyOn(analysisApi, 'export').mockResolvedValue(file())
    render(<ReportDownload analysisId="id-1" fileName="set02.pdf" />)

    fireEvent.click(screen.getByRole('button', { name: '.json 받기' }))

    await waitFor(() => expect(downloaded).toEqual(['set02_분석결과.json']))
    expect(api).toHaveBeenCalledWith('id-1', 'json')
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('실패하면 서버가 준 안내를 보여 준다', async () => {
    vi.spyOn(analysisApi, 'export').mockRejectedValue(
      new ApiError(410, { code: 'PURGED', message: '보관 기간이 지나 삭제했습니다.' }),
    )
    render(<ReportDownload analysisId="id-1" fileName="set02.pdf" />)

    fireEvent.click(screen.getByRole('button', { name: '.md 받기' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('보관 기간이 지나 삭제했습니다.')
    expect(downloaded).toEqual([])
  })

  it('가린 PDF 버튼은 PDF 문서에만 보인다', () => {
    render(<ReportDownload analysisId="id-1" fileName="a.png" contentType="image/png" />)
    expect(screen.queryByRole('button', { name: '가린 PDF 받기' })).not.toBeInTheDocument()
  })

  it('가린 PDF 를 받으면 가리지 못한 건수와 직접 확인 안내를 보여 준다', async () => {
    vi.spyOn(analysisApi, 'export').mockResolvedValue(
      file({ 'X-Area-Masked-Count': '2', 'X-Unlocated-Count': '1' }),
    )
    render(<ReportDownload analysisId="id-1" fileName="a.pdf" contentType="application/pdf" />)

    fireEvent.click(screen.getByRole('button', { name: '가린 PDF 받기' }))

    const notice = await screen.findByRole('status')
    expect(downloaded).toEqual(['a_가림본.pdf'])
    expect(notice).toHaveTextContent('위치를 찾지 못한 1건은 가리지 못했습니다.')
    expect(notice).toHaveTextContent('2건은 그 영역 전체를 가렸습니다.')
    expect(notice).toHaveTextContent('공유하기 전에 직접 확인해 주세요.')
  })
})
