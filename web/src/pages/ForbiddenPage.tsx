import { Link } from 'react-router'
import { FullPageMessage } from '@/components/FullPageMessage'
import { Button } from '@/components/ui/button'

export default function ForbiddenPage() {
  return (
    <FullPageMessage title="이 화면을 볼 권한이 없습니다.">
      <Button variant="outline" asChild>
        <Link to="/">처음 화면으로</Link>
      </Button>
    </FullPageMessage>
  )
}
