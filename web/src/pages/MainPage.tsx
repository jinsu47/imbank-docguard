import { useQuery } from '@tanstack/react-query'
import {
  ChartColumn,
  ClipboardCheck,
  FileSearch,
  FileText,
  FileUp,
  Files,
  LayoutGrid,
  ListChecks,
  MapPin,
  ScanSearch,
  ScrollText,
  ShieldCheck,
  Trash2,
  UserCog,
  Waypoints,
} from 'lucide-react'
import { adminApi, analysisApi, reviewApi } from '@/api/endpoints'
import type { AnalysisSummary, Role } from '@/api/types'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { GradeBadge } from '@/features/analysis/GradeBadge'
import { formatDateTime, isInProgress, STATUS_LABELS } from '@/features/analysis/labels'
import { useUpload } from '@/features/analysis/useUpload'
import { useMe } from '@/features/auth/useMe'
import {
  FeatureCards,
  ListPanel,
  type ListRow,
  MainHero,
  type MainTab,
  MainTabs,
  NoticePanel,
  Tile,
  type TileSpec,
} from '@/features/main/MainBlocks'

const USER_NOTICES = [
  {
    title: '합성 문서만 올려 주세요',
    body: '이 서비스는 대회가 제공한 합성 문서만 처리합니다. 실제 고객 문서는 올리지 않습니다.',
  },
  {
    title: '결과는 참고 정보입니다',
    body: '위험 등급은 자체 점검 기준에 따른 결과이며, 법적 판단이나 유출·위법 여부를 확정하지 않습니다.',
  },
  {
    title: '기본 7일이 지나면 삭제를 권고합니다',
    body: '원본과 상세 결과는 암호화해 보관합니다. 기본 7일이 지나면 관리자에게 삭제를 권고하며 자동으로 지우지는 않습니다. 언제든 직접 지울 수도 있습니다.',
  },
]

const ADMIN_NOTICES = [
  {
    title: '모든 문서를 관리합니다',
    body: '관리자는 점검 담당자·검토자의 기능을 모두 쓸 수 있고, 모든 사용자의 분석을 열어 보고 지울 수 있습니다.',
  },
  {
    title: '열람은 기록에 남습니다',
    body: '다른 사람 문서의 원본 열람, 원문 보기, 삭제는 감사 기록에 남습니다. 필요할 때만 열어 보세요.',
  },
  {
    title: '변경은 바로 반영됩니다',
    body: '역할이나 상태를 바꾸면 해당 사용자의 로그인 세션에 바로 적용되고, 감사 기록에 남습니다.',
  },
]

function shortDate(iso?: string): string {
  return formatDateTime(iso).split(' 오')[0]
}

// 메인화면. 역할에 맞는 바로가기와 현황을 보여 줍니다.
export default function MainPage() {
  const { data: me } = useMe()
  if (!me) return null
  return me.role === 'ADMIN' ? <AdminMain /> : <UserMain role={me.role} />
}

function UserMain({ role }: { role: Role }) {
  const upload = useUpload()
  const list = useQuery({
    queryKey: ['analyses'],
    queryFn: analysisApi.list,
    refetchInterval: (query) =>
      query.state.data?.some((a) => isInProgress(a.status)) ? 2000 : false,
  })
  const reviewer = role === 'REVIEWER'
  const queue = useQuery({
    queryKey: ['reviews', 'queue'],
    queryFn: reviewApi.queue,
    enabled: reviewer,
  })

  const items: AnalysisSummary[] = list.data ?? []
  const inProgress = items.filter((a) => isInProgress(a.status))
  const latestDone = items.find((a) => a.status === 'COMPLETED')
  const high = items.filter((a) => a.riskGrade === 'HIGH').length
  const pending = reviewer
    ? (queue.data?.length ?? 0)
    : items.filter((a) => a.reviewStatus === 'PENDING').length

  const toRow = (a: AnalysisSummary): ListRow => ({
    key: a.id,
    to: `/analyses/${a.id}`,
    title: a.fileName ?? `분석 ${a.id.slice(0, 8)}`,
    aside: <GradeBadge analysis={a} />,
    meta: shortDate(a.createdAt),
  })

  const uploadTile: TileSpec = {
    key: 'upload',
    label: upload.isPending ? '올리는 중…' : '문서 올리기',
    tone: 'blue',
    icon: FileUp,
    onClick: upload.openPicker,
    disabled: upload.isPending,
  }

  const latestTile = (tone: TileSpec['tone']): TileSpec => ({
    key: 'latest',
    label: '최근 결과 보기',
    tone,
    icon: FileSearch,
    to: latestDone ? `/analyses/${latestDone.id}` : '/analyses',
  })

  const shortcuts: TileSpec[] = reviewer
    ? [
        uploadTile,
        { key: 'mine', label: '내 분석', tone: 'sky', icon: Files, to: '/analyses' },
        latestTile('mint'),
        {
          key: 'reviews',
          label: '검토 대기열',
          tone: 'teal',
          icon: ClipboardCheck,
          to: '/reviews',
        },
      ]
    : [
        uploadTile,
        { key: 'mine', label: '내 분석', tone: 'sky', icon: Files, to: '/analyses' },
        { key: 'total', label: '전체 분석', tone: 'mint', value: items.length },
        latestTile('teal'),
      ]

  const tabs: MainTab[] = [
    {
      value: 'shortcut',
      label: '바로가기',
      icon: LayoutGrid,
      tiles: shortcuts,
      center: (
        <>
          <ListPanel
            title="최근 분석"
            moreTo="/analyses"
            moreLabel="내 분석 전체 보기"
            rows={items.slice(0, 4).map(toRow)}
            emptyText="아직 올린 문서가 없습니다. 문서 올리기로 시작해 보세요."
          />
          <div className="grid grid-cols-2">
            <Tile
              tile={{ key: 'running', label: '진행 중', tone: 'gray', value: inProgress.length }}
            />
            <Tile tile={{ key: 'pending', label: '검토 대기', tone: 'white', value: pending }} />
          </div>
        </>
      ),
    },
    {
      value: 'status',
      label: '진행 현황',
      icon: ListChecks,
      tiles: [
        { key: 'total', label: '전체 분석', tone: 'blue', value: items.length },
        { key: 'running', label: '진행 중', tone: 'sky', value: inProgress.length },
        { key: 'high', label: '위험 등급 상', tone: 'mint', value: high },
        { key: 'pending', label: '검토 대기', tone: 'teal', value: pending },
      ],
      center: (
        <ListPanel
          title="진행 중인 분석"
          moreTo="/analyses"
          moreLabel="내 분석 전체 보기"
          rows={inProgress
            .slice(0, 6)
            .map((a) => ({ ...toRow(a), aside: STATUS_LABELS[a.status] }))}
          emptyText="진행 중인 분석이 없습니다."
        />
      ),
    },
    {
      value: 'guide',
      label: '이용 방법',
      icon: FileText,
      tiles: [
        { key: 'step1', label: '1. 문서 올리기', tone: 'blue', icon: FileUp },
        { key: 'step2', label: '2. 자동 분석', tone: 'sky', icon: ScanSearch },
        { key: 'step3', label: '3. 결과 확인', tone: 'mint', icon: FileSearch },
        { key: 'step4', label: '4. 검토와 삭제', tone: 'teal', icon: Trash2 },
      ],
      center: (
        <section className="bg-white px-6 py-5 text-sm leading-relaxed">
          <h2 className="mb-3 font-bold">이렇게 사용합니다</h2>
          <ol className="grid list-decimal gap-2 pl-5">
            <li>PDF, JPG, PNG 문서를 한 건 올립니다.</li>
            <li>
              문서 인식부터 보고서 작성까지 자동으로 진행합니다. 1~2쪽 문서는 보통 1분 안에 끝나고,
              쪽수가 많으면 더 걸립니다.
            </li>
            <li>
              위험 등급과 사람 검토 여부를 확인하고, 항목이나 근거 번호를 눌러 문서 위치를 확대해
              봅니다.
            </li>
            <li>애매한 문서는 검토자가 확인합니다. 다 쓴 문서는 직접 지울 수 있습니다.</li>
          </ol>
        </section>
      ),
    },
  ]

  return (
    <>
      <MainHero subtitle="문서를 올리면 위험 등급과 가려야 할 위치를 바로 보여 드립니다." />
      <main>
        <MainTabs tabs={tabs} aside={<NoticePanel notices={USER_NOTICES} />} />
        <input {...upload.inputProps} />
        {upload.error && (
          <div className="mx-auto mt-6 max-w-6xl px-4">
            <Alert variant="destructive">
              <AlertDescription>{upload.error}</AlertDescription>
            </Alert>
          </div>
        )}
        <FeatureCards
          heading="이렇게 점검합니다"
          cards={[
            {
              icon: ShieldCheck,
              category: '자동 판정',
              title: '위험 등급과 사람 검토',
              to: '/analyses',
            },
            {
              icon: MapPin,
              category: '문서 위 표시',
              title: '가려야 할 위치 확인',
              to: '/analyses',
            },
            {
              icon: Waypoints,
              category: '판정 근거',
              title: '보고서에서 문서로 이동',
              to: '/analyses',
            },
          ]}
        />
      </main>
    </>
  )
}

function AdminMain() {
  const upload = useUpload()
  const users = useQuery({ queryKey: ['admin', 'users'], queryFn: adminApi.users })
  const analyses = useQuery({
    queryKey: ['admin', 'analyses'],
    queryFn: adminApi.analyses,
    refetchInterval: (query) =>
      query.state.data?.some((a) => isInProgress(a.status)) ? 2000 : false,
  })
  const queue = useQuery({ queryKey: ['reviews', 'queue'], queryFn: reviewApi.queue })
  const userList = users.data ?? []
  const items = analyses.data ?? []
  const blocked = userList.filter((u) => u.status !== 'ACTIVE').length
  const inProgress = items.filter((a) => isInProgress(a.status)).length
  const pending = queue.data?.length ?? 0
  const recommended = items.filter((a) => a.deletionRecommended).length

  const tabs: MainTab[] = [
    {
      value: 'shortcut',
      label: '바로가기',
      icon: LayoutGrid,
      tiles: [
        {
          key: 'upload',
          label: upload.isPending ? '올리는 중…' : '문서 올리기',
          tone: 'blue',
          icon: FileUp,
          onClick: upload.openPicker,
          disabled: upload.isPending,
        },
        { key: 'mine', label: '내 분석', tone: 'sky', icon: Files, to: '/analyses' },
        {
          key: 'reviews',
          label: '검토 대기열',
          tone: 'mint',
          icon: ClipboardCheck,
          to: '/reviews',
        },
        {
          key: 'analyses',
          label: '분석 현황',
          tone: 'teal',
          icon: ChartColumn,
          to: '/admin/analyses',
        },
      ],
      center: (
        <>
          <ListPanel
            title="최근 분석"
            moreTo="/admin/analyses"
            moreLabel="분석 현황 전체 보기"
            rows={items.slice(0, 4).map((a) => ({
              key: a.id,
              to: `/analyses/${a.id}`,
              title: a.fileName,
              aside: STATUS_LABELS[a.status],
              meta: shortDate(a.createdAt),
            }))}
            emptyText="아직 분석이 없습니다."
          />
          <div className="grid grid-cols-3">
            <Tile tile={{ key: 'running', label: '진행 중', tone: 'gray', value: inProgress }} />
            <Tile tile={{ key: 'pending', label: '검토 대기', tone: 'white', value: pending }} />
            <Tile
              tile={{
                key: 'recommend',
                label: '삭제 권고',
                tone: 'sky',
                value: recommended,
                to: '/admin/analyses?recommend=1',
              }}
            />
          </div>
        </>
      ),
    },
    {
      value: 'manage',
      label: '관리 메뉴',
      icon: UserCog,
      tiles: [
        { key: 'users', label: '계정 관리', tone: 'blue', icon: UserCog, to: '/admin/users' },
        { key: 'audit', label: '감사 기록', tone: 'sky', icon: ScrollText, to: '/admin/audit' },
        { key: 'total', label: '전체 분석', tone: 'mint', value: items.length },
        { key: 'blocked', label: '잠김·비활성', tone: 'teal', value: blocked },
      ],
      center: (
        <ListPanel
          title="최근 계정 로그인"
          moreTo="/admin/users"
          moreLabel="계정 관리로 이동"
          rows={[...userList]
            .filter((u) => u.lastLoginAt)
            .sort((a, b) => (b.lastLoginAt ?? '').localeCompare(a.lastLoginAt ?? ''))
            .slice(0, 6)
            .map((u) => ({ key: String(u.id), title: u.email, meta: shortDate(u.lastLoginAt) }))}
          emptyText="로그인 기록이 없습니다."
        />
      ),
    },
    {
      value: 'guide',
      label: '이용 방법',
      icon: FileText,
      tiles: [
        { key: 'a', label: '문서 올리기', tone: 'blue', icon: FileUp },
        { key: 'b', label: '검토 기록', tone: 'sky', icon: ClipboardCheck },
        { key: 'c', label: '모든 문서 관리', tone: 'mint', icon: Trash2 },
        { key: 'd', label: '계정·감사 관리', tone: 'teal', icon: ShieldCheck },
      ],
      center: (
        <section className="bg-white px-6 py-5 text-sm leading-relaxed">
          <h2 className="mb-3 font-bold">관리자가 할 수 있는 일</h2>
          <ul className="grid list-disc gap-2 pl-5">
            <li>점검 담당자·검토자와 똑같이 문서를 올리고, 결과를 보고, 검토를 남깁니다.</li>
            <li>모든 사용자의 분석을 열어 보고, 다시 실행하고, 지웁니다.</li>
            <li>계정의 역할을 바꾸고, 잠그거나 비활성화합니다. 자기 계정은 바꿀 수 없습니다.</li>
            <li>로그인, 원문 열람, 검토 결정 등의 기록을 기간·사용자·행동으로 찾아봅니다.</li>
          </ul>
        </section>
      ),
    },
  ]

  return (
    <>
      <MainHero subtitle="계정과 감사 기록을 관리하고, 모든 분석을 확인합니다." />
      <main className="pb-16">
        <MainTabs tabs={tabs} aside={<NoticePanel notices={ADMIN_NOTICES} />} />
        <input {...upload.inputProps} />
        {upload.error && (
          <div className="mx-auto mt-6 max-w-6xl px-4">
            <Alert variant="destructive">
              <AlertDescription>{upload.error}</AlertDescription>
            </Alert>
          </div>
        )}
      </main>
    </>
  )
}
