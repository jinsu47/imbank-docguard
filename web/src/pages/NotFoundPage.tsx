import { Link } from 'react-router'
import { FullPageMessage } from '@/components/FullPageMessage'
import { Button } from '@/components/ui/button'

export default function NotFoundPage() {
  return (
    <FullPageMessage title="페이지를 찾을 수 없습니다.">
      <Button variant="outline" asChild>
        <Link to="/">처음 화면으로</Link>
      </Button>
    </FullPageMessage>
  )
}
