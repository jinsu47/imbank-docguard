# IM-BANK 프로젝트 에이전트 지침

- 프로젝트명: IM-BANK
- 최종 수정일: 2026-11-08
- 상태: draft

이 문서는 `IM-BANK/` 디렉터리에서 새 채팅을 연 에이전트가 다른 설명 없이 작업을 이어 갈 수 있도록 맥락과 규칙을 담습니다.

## 1. 서비스 한 줄 요약

금융기관 실무자가 금융문서를 올리면, Upstage Studio 워크플로(v2.5)가 민감정보를 찾아 위험 등급을 판정하고, 웹 서비스가 그 결과를 **문서 위 위치 표시, 판정 근거, 역할별 검토 흐름**과 함께 보여 줍니다. iM뱅크 주최 대회("AI기반 금융문서 민감정보 자동 식별 및 위험도 등급화") 출품작입니다.

## 2. 지금까지의 경과

1. **워크플로 (완료):** 팀원이 만든 원본 Studio 워크플로를 v2~v2.5로 고쳤습니다. v2.5는 평가 문서 10건에서 추출 행과 보고서 판정이 모두 정답과 일치했고, 2026-11-06 사용자가 최종본으로 정했습니다. 팀이 규칙 번호(R1, R2 등)를 원하면 같은 판정의 v2.4를 씁니다.
2. **API 호출 (완료):** 2026-11-06 v2.5를 Agents API로 호출하는 데 성공했습니다(set02 67초, set10 36초, 채점 2/2). 모든 요청에 `x-project-id` 헤더가 필요합니다.
3. **웹 서비스 기획 (2026-11-08):** 이 디렉터리의 문서 7종을 작성했습니다. 사용자가 프런트엔드와 백엔드를 모두 구현합니다.
4. **M0 골격 (2026-11-08 완료):** `docker-compose.yml`, `.env.example`, `scripts/create-env.*`, `server/`(Spring Boot 골격, `V1__init_schema.sql`, 최소 보안 설정), `web/`(Vite + React 빈 화면)을 만들었습니다. 8장에 결과가 있습니다.
5. **API 키 연동 전 구현 (2026-11-08):** 사용자 요청으로 실제 키 연결 전까지 할 수 있는 백엔드·프런트엔드를 만들었습니다. M1(Studio 클라이언트·변환기·보고서 파서·작업 스케줄러), M2(결과 화면 세 영역), M3 일부(가입·로그인·역할·검토), M4 일부(감사 기록 저장, 보관 기간 정리)입니다. 키가 없어도 **재생 모드**(`UPSTAGE_MODE=replay`)로 끝까지 시험할 수 있습니다. 8장에 결과가 있습니다.
6. **실제 API 연결 (다음 단계):** 사용자가 `.env`에 키 3개를 넣고 `UPSTAGE_MODE=live`로 바꾼 뒤 확인합니다.

## 3. 문서의 역할과 읽는 순서

| 순서 | 문서 | 읽는 이유 |
|---|---|---|
| 1 | 이 문서 | 규칙, 현재 단계, 다음 할 일 |
| 2 | [PRD.md](./PRD.md) | 무엇을 왜 만드는지, 기능 ID(F01~F15)와 우선순위, 마일스톤 |
| 3 | [ARCHITECTURE.md](./ARCHITECTURE.md) | 구성, 스택, **Studio API 호출 순서와 응답 해석 규칙**, 서버 API, 환경 변수 |
| 4 | [USER_FLOW.md](./USER_FLOW.md) | 화면 목록과 흐름, 예외 처리 |
| 5 | [DATABASE.md](./DATABASE.md) | 테이블, 암호화 컬럼, 인덱스, 마이그레이션 규칙 |
| 6 | [DOMAIN.md](./DOMAIN.md) | 용어, 업무 규칙(BR01~BR16), 상태 전이, 표시값 가림 규칙 |
| 7 | [SECURITY.md](./SECURITY.md) | 권한 표, 인증, 로깅 금지 항목, 보관 기간, 속도 제한 |

참고 자료:

| 자료 | 내용 |
|---|---|
| WORKFLOW_CHANGES.md | 원본 워크플로에서 v2.5까지 바꾼 내용 (팀원용) |
| studio/README.md | 워크플로 버전, 검증 결과, **API 응답 구조 실측 표** |
| `studio/mission2-v2.5-proposal.json` | 최종 워크플로 설정 (Studio 불러오기용) |
| `studio/api_probe.py` | API 호출 시험 스크립트 (OpenAI Python SDK) |
| eval/README.md | 평가 정답표와 채점 방법 |
| `eval/compare.py` | 결과 채점 스크립트. `parse_report`가 서버 보고서 해석 규칙의 기준 |
| `eval/testmdfile/v2.5/` | v2.5 실행 결과 10건 (보고서, 추출 JSON). 서버 보고서 파서의 테스트 자료 |
| `eval/testmdfile/api-20261106-111210/` | API 원본 응답 2건 (`*_raw.json`). Studio 응답 변환기의 테스트 자료 |

원본 평가 문서 PDF는 저장소 밖(담당자 PC)에 있습니다.

## 4. 기술 스택 요약

- 백엔드: Java 21, Spring Boot 3.5.7, Gradle 8.14 wrapper, Spring Security(세션 + CSRF), Spring Data JPA, Flyway, PostgreSQL 16, `RestClient`
- 프런트엔드: React 19.3, Vite 8, TypeScript 7(strict), React Router 8, TanStack Query 5, Tailwind CSS 4, shadcn/ui(radix-nova 프리셋), `react-pdf`(PDF.js, M2에서 추가)
- 테스트: JUnit 5, Spring Security Test, Testcontainers **1.21.4**, Vitest 5, Testing Library
- 로컬 실행: PostgreSQL은 Docker Compose **5434** 포트, 서버 8080, Vite 5173 (`/api` 프록시)
- npm 의존성은 정확한 버전으로 고정합니다(`web/.npmrc`의 `save-exact=true`).

### 로컬 실행 순서

```powershell
# IM-BANK 디렉터리에서 (처음 한 번)
powershell -ExecutionPolicy Bypass -File scripts\create-env.ps1   # .env 생성, 이미 있으면 멈춤
docker compose up -d                                              # PostgreSQL 16 (5434)

cd server; .\gradlew.bat test      # 통합 테스트 포함 (Docker 필요)
cd server; .\gradlew.bat bootRun   # local 프로필 → ../.env 를 읽음, http://localhost:8080/actuator/health
cd web; npm install; npm run dev   # http://localhost:5173
```

- `.env`는 `IM-BANK/` 루트 하나만 둡니다. Docker Compose와 서버(`application-local.yml`의 `spring.config.import`)가 같은 파일을 읽습니다.
- 테스트는 `local` 프로필을 켜지 않으므로 `.env`를 읽지 않습니다. 테스트 DB는 Testcontainers가 만듭니다.
- Upstage 값(`UPSTAGE_API_KEY`, `UPSTAGE_PROJECT_ID`, `UPSTAGE_AGENT_ID`)은 `.env`에 비어 있으며, 사용자가 직접 넣습니다. M1부터 필요합니다.

## 5. 디렉터리 구조 규약

```
IM-BANK/
├── CLAUDE.md, PRD.md, ARCHITECTURE.md, USER_FLOW.md,
│   DATABASE.md, DOMAIN.md, SECURITY.md      ← 기획 문서 원본 (팀 저장소에는 docs/ 사본)
├── WORKFLOW_CHANGES.md                       ← 팀원용 설명 (로컬 전용)
├── README.md                                 ← 공개용 실행 안내 (구현 시 작성)
├── docker-compose.yml                        ← PostgreSQL 16 (5434)
├── .env.example                              ← 변수 이름만
├── scripts/
│   ├── create-env.*                          ← .env 생성, 비밀 값은 무작위 생성
│   └── seed-accounts.*                       ← 역할별 계정 생성, 비밀번호는 .local/accounts.txt
├── server/                                   ← Spring Boot (com.imbank.docguard)
│   └── src/main/java/com/imbank/docguard/
│       ├── auth/ user/ analysis/ studio/ result/
│       └── review/ audit/ storage/ common/
├── web/                                      ← React + Vite
│   └── src/ pages/ features/ api/ components/
├── deploy/                                   ← 네이버클라우드 배포 안내와 스크립트
├── studio/                                   ← Studio 워크플로 JSON, API 시험 스크립트
├── eval/                                     ← 정답표, 채점, 실행 결과 (비공개)
├── storage/                                  ← 원본 파일 (암호화, .gitignore)
└── .local/                                   ← 계정 비밀번호 등 (.gitignore)
```

`docguard`는 서비스 이름이 정해지기 전까지 쓰는 코드 이름입니다.

## 6. 코딩 규칙

### 공통

- 화면 문구, 문서, 코드 주석은 한국어로 씁니다. 식별자와 기술 용어는 영어를 씁니다.
- 판정 용어는 [DOMAIN.md](./DOMAIN.md) 1장의 이름을 그대로 씁니다. 화면에는 보고서 표기(「본인 금융정보 결합 노출」 등)만 쓰고, R1 같은 내부 코드는 쓰지 않습니다.
- 기능을 만들 때 기능 ID(F02 등)와 업무 규칙 ID(BR08 등)를 커밋 메시지나 테스트 이름에 남겨 문서와 연결합니다.

### 백엔드

- Studio 응답 형식은 `studio` 패키지 밖으로 내보내지 않습니다. 다른 패키지는 `StudioResultMapper`가 만든 서비스 객체만 씁니다.
- 단계 이름(`Information Extract - 공통민감정보 추출` 등)은 `application.yml`의 `upstage.steps.*`에 둡니다. 이름으로 못 찾으면 `step_type`으로 찾습니다.
- 다른 패키지는 `StudioService`(입구)와 `StudioModels`(서비스 객체)만 씁니다. `StudioGateway` 구현은 `StudioHttpClient`(live)와 `ReplayStudioGateway`(replay) 두 가지입니다.
- Studio 작업 상태 조회는 `analysis/AnalysisJobProcessor`가 합니다(문서의 `studio.poller` 자리. studio 가 analysis 를 참조하지 않도록 옮김). 스케줄은 `analysis/SchedulingConfig`이며 `app.scheduling.enabled=false`로 끕니다(테스트).
- 오류 응답은 `common/ApiException` → `{code, message, requestId}`입니다. 볼 수 없는 분석은 404 입니다.
- 인가는 컨트롤러가 아니라 서비스의 `AnalysisAccessPolicy`에서 확인합니다. 관리자는 모든 분석을 보고 관리할 수 있습니다(BR11).
- JPA `ddl-auto: validate`, 스키마 변경은 Flyway 새 버전 파일로만 합니다.
- 테스트:
  - 보고서 파서: `eval/testmdfile/v2.5`의 보고서 10건으로 `eval/answer_key.json`의 등급·사람 검토와 같은지 확인합니다.
  - 응답 변환기: `api-20261106-111210/*_raw.json`으로 확인합니다.
  - Studio 호출: 가짜 서버(MockRestServiceServer 또는 WireMock)로 시험합니다. **테스트에서 실제 Upstage API를 호출하지 않습니다.**
  - 권한: `AuthorizationMatrixTest`로 역할 × API 전체를 확인합니다.
  - 통합 테스트는 `IntegrationTestBase`를 상속합니다(PostgreSQL 컨테이너 하나 공유, 무작위 암호화 키, 임시 저장 폴더, replay 모드 지연 0, 스케줄러 끔).
  - MockMvc 에서는 spring-security-test 가 CSRF 저장소를 바꿔 XSRF-TOKEN 쿠키가 나오지 않습니다. 테스트는 `csrf()`를 쓰고, 실제 쿠키 흐름은 서버를 띄워 curl 로 확인합니다.
  - `eval/` 자료가 없으면(공개 저장소) 그 자료를 쓰는 테스트는 건너뜁니다.
- 빌드·테스트: `server/gradlew test`. 통합 테스트가 SKIPPED면 Docker Desktop이 켜져 있는지 먼저 확인합니다.

### 프런트엔드

- TypeScript `strict`, oxlint(`npm run lint`, Vite 템플릿 기본 린터로 ESLint 대신 사용) + Prettier(`npm run format`), 함수 컴포넌트와 훅만 씁니다.
- import 경로 별칭은 `@/` = `web/src/`입니다. shadcn/ui 컴포넌트는 `npx shadcn@4.23.0 add <이름>`으로 `src/components/ui/`에 추가하고, 직접 고친 경우가 아니면 Prettier 대상에서 뺍니다(`.prettierignore`).
- API 호출은 `src/api`의 래퍼만 씁니다. 래퍼가 CSRF 헤더와 401 처리를 맡습니다.
- 서버가 준 문자열을 HTML로 넣지 않습니다(`dangerouslySetInnerHTML` 금지).
- 좌표는 0~1 상대값이므로, 백분율 위치(`features/viewer/boxes.ts`의 `toPercentRect`)로 페이지 위에 겹쳐 그립니다. 화면 크기가 바뀌어도 다시 계산할 필요가 없습니다.
- 서버 상태는 TanStack Query 키로 관리합니다: `['me']`, `['analyses']`, `['analysis', id]`, `['analysis-result', id]`, `['analysis-file', id]`, `['reviews', 'queue']`. 진행 중이면 2초마다 다시 조회합니다.
- 화면에는 내부 용어(미마스킹 등)를 쓰지 않고 쉬운 말(가림 없음 등)로 바꿉니다(`features/analysis/FindingList.tsx`의 `maskLabel`).
- 색과 모양은 `index.css`의 토큰(`bg-brand-navy`, `bg-tile-blue` 등)과 `.dg-*` 클래스를 씁니다. 외부 글꼴·이미지 주소를 쓰지 않습니다(CSP `default-src 'self'`).
- 테스트: `web/npm test`(Vitest).

### 커밋 메시지

- 형식: `<type>: <한국어 요약>`. type은 `feat`, `fix`, `refactor`, `test`, `docs`, `chore` 중 하나입니다. 예: `feat: Studio 작업 상태 조회 스케줄러 추가 (F02)`.
- 저장소는 팀 공용 비공개 저장소 `jinsu47/imbank-docguard`입니다(2026-11-08 연결). `IM-BANK/`가 작업 폴더이고, 웹 서비스 코드와 기획 문서 사본은 `feature/docguard-web-service` 브랜치에 올렸습니다(PR 미작성). 팀 규칙은 `CONTRIBUTING.md`(최신 `main`에서 `feature/...` 브랜치, PR 에 확인 결과 기록)를 따릅니다.
- 최상위의 기획 문서 7종 원본, `WORKFLOW_CHANGES.md`, `eval/`, `studio/`, 그리고 `docs/`의 PDF 는 `.git/info/exclude`로 로컬 전용입니다. 팀 `.gitignore`에는 넣지 않았습니다. 커밋 전에 `git status`로 이 파일들이 스테이징되지 않았는지 확인합니다.
- 기획 문서 7종은 사용자 요청으로 `docs/`에 사본을 올렸습니다(2026-11-08). 최상위 원본을 고치면 `docs/` 사본도 맞춰 커밋합니다. 사본에는 Agent ID, 개인 PC 경로, 평가 문서별 판정 결과(등급·행 수)를 넣지 않고, 저장소에 없는 파일(`WORKFLOW_CHANGES.md`, `studio/`, `eval/`)로 가는 링크는 글자로만 남깁니다.
- `.gitignore`에 폴더 이름만 쓰면 같은 이름의 소스 패키지까지 무시됩니다. `/storage/`처럼 최상위로 한정합니다(`storage/`로 썼다가 Java `storage` 패키지가 커밋에서 빠진 적이 있음). 푸시 전에는 깨끗한 복제본에서 빌드·테스트를 확인합니다.

> ⚠️ 가정: 커밋 메시지 형식과 린트 도구는 사용자가 지정하지 않아 다른 프로젝트 관례를 참고해 정했습니다.

## 7. 절대 하지 말 것

- **비밀 값을 파일·코드·로그·채팅에 쓰지 않습니다.** Upstage API 키는 사용자가 자기 터미널이나 `.env`에 직접 넣습니다. 키를 채팅에 붙여 달라고 요청하지 않습니다. 비밀 값 자리에 임시 값(`changeme` 등)도 넣지 않고, 스크립트로 생성합니다.
- Project ID와 Agent ID를 저장소 파일에 적지 않습니다(환경 변수로만).
- 판정 로직(등급 규칙)을 서버나 화면에서 다시 구현하지 않습니다. 등급은 Studio 보고서에서만 읽습니다.
- 보고서를 읽지 못했을 때 등급을 추측하지 않습니다(`UNKNOWN` + 사람 검토 필요).
- 원문값, 원문 근거, 보고서 본문, 파일 이름을 로그에 남기지 않습니다.
- 테스트나 개발 중에 실제 고객 문서를 쓰지 않습니다. 대회 합성 문서만 씁니다.
- `UPSTAGE_CONFIG_ID` 없이 Studio를 호출하지 않습니다(빼면 최신 설정이 실행됨).
- Studio 워크플로 설정(`studio/*.json`)을 사용자 요청 없이 고치지 않습니다. 고치면 10건 재검증이 필요합니다.
- `eval/answer_key.json`, `eval/testmdfile/`을 저장소에 올리지 않습니다. 기획 문서는 비공개 저장소의 `docs/` 사본만 허용하며, 저장소를 공개로 바꾸기 전에 사본을 먼저 뺍니다.
- `IM-BANK/` 밖의 디렉터리를 수정하지 않습니다.

## 8. 현재 진행 단계와 다음 할 일

**현재 단계:** P0·P1 기능 구현, 실제 API 연결 확인, 프런트엔드 디자인 적용 완료 (2026-11-08). 사용자가 `.env`에 키를 넣고 `UPSTAGE_MODE=live`로 바꿨습니다.

**실제 API 첫 실행 (2026-11-08, set02):** 웹 서버로 set02 를 올려 Config 2 로 실행했습니다. 업로드부터 완료까지 약 50초(Studio 작업 29초)가 걸렸습니다. 웹 결과로 만든 추출·보고서를 `eval/compare.py --plain`으로 채점해 정답표와 완전히 일치했습니다. 추출 행은 11-06 API 실행과 같았습니다. 작업이 끝난 뒤 Studio 파일이 지워졌고, 서버 로그에 원문값·파일 이름·키가 없었습니다. **진행 중 응답에도 끝난 단계가 들어옵니다**(PARSE → … → REPORT 순서로 표시됨). 나머지 9건은 아직 웹에서 실행하지 않았습니다.

**사용자 직접 시험과 수정 (2026-11-08):** 사용자가 set08 등을 직접 올려 보았고, 웹 실행 2건(set02, set08)이 정답과 일치합니다(시연 정확도 화면 기준). 사용자 피드백으로 두 가지를 고쳤습니다.
- 원본 문서 칸이 흔들리고 잘리던 문제: PDF 를 칸 너비마다 다시 그리던 것을 300px 단위 해상도로 한 번 그리고 CSS(`.pdf-page canvas`)로 맞추도록 바꿈.
- set08 전자우편(`*****`)처럼 값 위치(`word_coordinates`)가 없는 항목이 표 덩어리 전체를 검게 칠하던 문제: `markKind`로 값 상자만 가리고, 이미 전체 가림인 값은 점선, 영역만 있는 항목은 고를 때만 점선으로 표시.

**남은 기능 구현 (2026-11-08, 디자인은 나중에 Figma 예제로 사용자와 함께):**
- 관리자: `GET /api/admin/users`, `PATCH /api/admin/users/{id}`(역할·상태, 사유 선택, 자기 계정 변경 금지, 이전·새 값 감사 기록), `GET /api/admin/audit-logs`(행동·사용자·기간 필터, 페이지), `GET /api/admin/analyses`(메타데이터만, 파일 이름 없음). 화면 `/admin/users`, `/admin/audit`, `/admin/analyses`. 관리자가 `/`로 오면 계정 관리로 보냄.
- 세션 즉시 반영: `SessionUserRefreshFilter`가 요청마다 사용자 상태를 확인해 관리자가 비활성화·잠근 계정은 세션을 끊고, 역할이 바뀌면 세션 권한을 바꿈. 로그인 실패 잠금(해제 시각 있음)은 끊지 않음(남이 로그인 중인 사용자를 내보내지 못하게).
- 그 밖의 API 속도 제한: `ApiRateLimitFilter` 사용자(로그인 전은 IP)당 분당 300회.
- 시연 정확도(F12): `GET /api/demo/accuracy` + `/demo/accuracy`. 내 완료 분석을 파일 이름의 setNN 으로 찾아 태그별 최근 결과를 정답표와 비교(재생 모드 제외). 정답표 경로는 `app.answer-key`(기본 `../eval/answer_key.json`, 없으면 화면에 안내).
- 가입 후 로그인 화면 안내, 분석 화면 지연 로딩(첫 번들 1.1MB → 466KB).
- 테스트: 서버 72건, 웹 19건 통과. 화면 확인용으로 만든 `screen-*@docguard.local` 계정들은 비활성화해 둠.

**문서 뷰어 사용자 편의 (2026-11-08, 사용자 요청):**
- 돋보기: 마우스를 올린 자리를 1.5배(240px 네모, 사용자 요청으로 원·2.5배에서 바꿈)로 보여 줌. 마우스를 올릴 때 화면의 PDF 캔버스를 돋보기 캔버스에 복사하고, 같은 `Marks`(가림 상자)를 위에 그려 확대해도 가린 값이 보이지 않음. 상단 "돋보기 켜짐/꺼짐" 버튼, 설정은 localStorage(`docguard.magnifier`).
- 항목 확대: 발견 항목·문서 위 상자·보고서 근거 번호를 누르면 `zoomScaleFor`(고른 영역 + 양옆 15% 여유, 1~4배, 넓은 영역은 확대 안 함)로 확대하고 그 위치를 칸 가운데로 옮김. 같은 것을 한 번 더 누르거나 "전체 보기"를 누르면 100%로 돌아감.
- 문서 칸은 `DocumentViewer`가 자기 안에서 위아래·좌우로 스크롤함. PDF 해상도는 칸 너비의 약 2.5배(1200~2400px, 커질 때만 다시 그림).
- 확인: 확대 240%·250%·300%에서 고른 상자가 칸 정중앙(오차 0px), 다시 누르면 100%·맨 위. 돋보기 안 가림 상자 21개 = 페이지 21개. Claude 쪽 브라우저 창이 숨겨져 PDF 그림 자체가 그려지지 않아, 돋보기 안의 그림 선명도는 사용자 화면에서 확인 필요. 웹 테스트 22건.

**프런트엔드 디자인 (2026-11-08, 사용자 요청):** 사용자가 `Harness-Prog/front-end-ex/example_1`(금융 랜딩 템플릿)의 분위기를 참고해 달라고 함. 아키텍처와 서버는 그대로 두고 `web/`만 바꿈. 템플릿의 CSS·이미지·아이콘·로고는 복사하지 않고 분위기만 새로 구현함.
- 공통: Pretendard(`pretendard` npm, 자체 호스팅이라 CSP 영향 없음), 남색 `--brand-navy`·녹색 `--brand-green` 축의 색 토큰과 각진 모서리(`--radius: 0.125rem`), `web/src/index.css`의 `.dg-hero`(어두운 배너), `.dg-tabbar`, `.dg-gnb-link`, `.dg-table`, `.dg-select`. 서비스 이름은 `lib/brand.ts`의 `SERVICE_NAME` 하나만 바꾸면 됨.
- 틀: `AppLayout`(고정 상단 메뉴, 좁은 화면의 전체 메뉴, 검은 푸터), `PageBand`·`PageBody`(하위 화면의 어두운 띠), `HeroArt`(문서 값이 가려지는 추상 그림, 자체 SVG), `AuthShell`(로그인·가입 좌우 분할).
- **메인화면:** `/`가 `MainPage`(배너, 배너에 걸친 탭 3개, 색면 타일, 최근 분석, 이용 안내 칸, 소개 카드, 역할별 내용)이고, 내 분석 목록은 `/analyses`(`MyAnalysesPage`)로 옮김. 관리자도 `/`에서 자기 메인화면을 봄(전에는 계정 관리로 이동).
- **분석 중 화면:** 돌아가는 원 안에 완료 퍼센트(`progressPercent`, 끝난 단계 수 기준, 끝나기 전 최대 99), 아래에 작은 문구(`progressMessage`), 그 아래에 7단계 칸(끝난 단계는 녹색과 체크, 진행 중인 단계는 남색으로 깜박임, 팀원 요청으로 추가).
- 결과 화면 상단은 색면 타일(위험 등급·사람 검토·문서 종류·발견 항목). 항목을 고르면 문서 칸이 창 안에 들어오도록 페이지도 함께 내림.
- 문서 올리기 로직은 `features/analysis/useUpload.ts`로 분리(메인 타일과 내 분석 화면이 함께 씀).
- 확인: 임시 자동 브라우저(playwright-core, 저장소 밖)로 실제 폼 가입·로그인, 메인 탭, 돋보기(캔버스에 문서 그림 있음, 가림 상자 21개), 클릭 확대 240·300%와 복귀, 원문 보기, 분석 중 화면(응답을 꾸밈, 실제 호출 없음), 검토자·관리자 화면, 390px 폭(가로 넘침 없음), 로그아웃을 통과함. 웹 테스트 32건, 서버 파일 변경 없음.

**관리자 권한 확대와 시연 정확도 숨김 (2026-11-08, 사용자 요청):**
- 관리자는 점검 담당자·검토자의 기능을 모두 씁니다: 문서 올리기, 내 분석, 결과·원본 보기, 원문 보기(사유 필수), 검토 대기열과 검토 기록, 다시 실행, 삭제. **모든 사용자의 분석**에 대해 가능합니다(BR11 변경, 위의 "메타데이터만" 설명은 이 결정으로 대체됨). 자기 분석을 검토할 수 없는 규칙(BR13)은 관리자에게도 적용합니다. Studio 판정(등급)은 관리자도 바꿀 수 없습니다(BR01).
- 서버: `SecurityConfig` 경로 규칙, `AnalysisAccessPolicy`(`canManage`·`requireManage`, ADMIN 전체 열람), `GET /api/admin/analyses`에 파일 이름·올린 사람 추가, 상세 응답에 `canManage` 추가, 삭제 감사 기록에 `ownerId` 추가.
- 화면: 관리자 메뉴 5개, 관리자 메인화면(문서 올리기 포함, 탭 "관리 메뉴"), 분석 현황에서 문서 열기·삭제.
- 시연 정확도: `web/src/lib/features.ts`의 `SHOW_ACCURACY = false`로 메뉴와 경로를 숨김(주소로 들어가면 404 화면). 서버 API(`/api/demo/accuracy`)와 `AccuracyPage`는 남겨 둠.
- 확인: 서버 테스트 74건, 웹 테스트 34건 통과. 자동 브라우저로 관리자가 다른 사람 문서 열기 → 원문 보기 → 검토 기록 → 삭제까지 통과했고 네 행동이 감사 기록에 남음(재생 모드 시험 분석 1건은 이 과정에서 삭제됨).

**공개 배포 보안 수정 (2026-11-08):** 배포 후 점검에서 로그인 없이 보낸 multipart 본문이 거절 전에 서버 임시 폴더에 전부 저장되는 것을 발견해 고쳤습니다. `MultipartGuardFilter`, `SpaCsrfTokenRequestHandler`(multipart 는 헤더만), Caddy 본문 1MB 제한(업로드 주소 제외)입니다. 공개 서버에서 30MB 를 5가지 경로로 보내 임시 파일 0개, 0.1초 안에 거절됨을 확인했습니다. 서버 보안 업데이트를 적용해 재부팅했고 자동 보안 업데이트를 켰습니다(`linux-generic` 등 3개는 이미지에서 hold 되어 있어 건드리지 않음). 서버 테스트 84건 통과.
- 테스트 주의: 업로드(`/api/analyses`)는 `csrf()` 대신 `IntegrationTestBase.csrfHeader()`를 씁니다.
- 남은 보안 일: 코드 전체 보안 검토, 서버(Java) 라이브러리 취약점 검사, 서버의 `accounts.txt` 삭제, DB 백업, 공개 주소에서 로그인·실제 분석 확인.

**네이버클라우드 배포 준비 (2026-11-08, 사용자 요청):** 새 서버에 올리고, `<IP>.sslip.io` 주소로 HTTPS 공개, 회원가입은 닫기로 정했습니다. 절차는 `deploy/README.md`, 설치 스크립트는 `deploy/`에 있습니다(미커밋). 2026-11-08 새 서버에 배포를 마쳤고, 주소와 접속 정보는 `.local/deploy.txt`에 있습니다.
- 공개 주소 확인: 인증서 정상, HTTP→HTTPS 이동, 화면 경로 200, 인증 없는 API 401, 가입 403, 쿠키 Secure, 8080·5432 외부 차단, 서버에서 Upstage 파일 목록 200. 계정 로그인과 실제 문서 분석은 사용자 확인이 남아 있습니다.
- 네이버클라우드 주의: 서버가 상권분석 챗봇과 같은 Subnet 이라 같은 Network ACL 을 씁니다. 재배포는 `bash deploy/deploy.sh <IP>`(Git Bash)이며, 이 PC 의 키에는 암호가 있어 Windows ssh 를 씁니다.
- 서버: `SpaResourceConfig`(jar 안의 React 빌드 제공, 화면 경로는 index.html), `SecurityConfig`(API 가 아닌 GET 공개), `application-prod.yml`(127.0.0.1 바인딩, 프록시 헤더), `APP_SIGNUP_ENABLED`. jar 는 `gradlew bootJar -PwebDist=../web/dist`로 만듭니다.
- 화면: `VITE_SIGNUP_ENABLED=false`로 빌드하면 가입 링크와 경로가 사라집니다.
- 확인: 운영 jar 를 로컬 8090 에서 재생 모드로 띄워 가입 차단(화면·API 403), 로그인, set02 업로드, 주소로 바로 열기, PDF 렌더링을 확인했습니다. CSP 위반은 대화상자의 스크롤 잠금용 `<style>` 1건뿐이며, 배경 스크롤이 잠기지 않는 것 외에 영향이 없어 CSP 를 넓히지 않았습니다. 서버 테스트 80건, 웹 테스트 37건 통과.
- 주의: 로컬에서 서버 두 대를 같은 DB 로 띄우지 않습니다. 두 스케줄러가 같은 분석을 함께 처리합니다.

**업로드 크기·쪽수 제한 제거 (2026-11-08, 사용자 요청):** 실제 문서가 20쪽을 넘는 경우가 많아 20MB·20쪽 제한을 서버와 화면에서 없애고 형식 검사(시그니처, 빈 파일, 손상, 암호 PDF)만 남겼습니다(BR05). 이어서 사용자 요청으로 사용자별 진행 중 3건·시간당 30건 제한도 없앴습니다(BR06). 로그인·가입·원문 보기 제한과 그 밖의 API 제한(분당 300회)은 남아 있습니다. 함께 고친 것:
- Studio 429: 업로드 단계에서 일시적인 오류(429·5xx·네트워크)는 3회로 끊지 않고 제한 시간까지 다시 시도합니다(`AnalysisJobProcessorSubmitTest`).
- 제한 시간: `StudioService.jobTimeoutSec(pageCount)`가 기본값(600초)과 쪽수 × 60초 중 큰 값을 씁니다.
- 문서 칸: 모든 쪽을 고해상도로 한꺼번에 그리면 브라우저 메모리가 부족해지므로, 쪽 크기만 먼저 읽어 자리를 잡고 `LazyPdfPage`가 칸에서 1,200px 안에 들어온 쪽만 그립니다. 가림 상자(`Marks`)는 모든 쪽에 있어 위치 이동이 그대로 됩니다. `react-pdf` 11의 `Page`는 기본으로 Suspense 를 쓰므로 쪽마다 `Suspense` 경계를 둡니다(없으면 새 쪽을 그릴 때 화면 전체가 대기 화면으로 바뀌고 스크롤이 초기화됨).
- 확인: 서버 테스트 79건, 웹 테스트 37건 통과. 재생 모드 서버와 자동 브라우저로 30MB·60쪽 PDF 를 올려 처리 완료, 한 번에 3~5쪽만 그림, 가로 쪽 비율 일치, 50쪽에서 1쪽 항목을 골랐을 때 칸 정중앙으로 이동함을 확인했습니다. 실제 Studio 로 긴 문서를 돌려 보지는 않았습니다.
- 남은 한계: Studio 한도(500MB, 1,000쪽)를 넘으면 실패합니다. 서버는 파일을 메모리에 올려 처리합니다. v2.5 는 1~2쪽 문서로만 검증했으므로 긴 문서의 판정 품질·시간·사용량은 확인이 필요합니다.

**API 키 연동 전 구현 결과 (2026-11-08 확인):**

- 백엔드
  - 엔티티 6종(V1 스키마와 `validate` 일치), `storage`(AES-256-GCM `EncryptionService`, 무작위 이름 `FileStorage`), `common`(오류 응답, 요청 ID 필터, 메모리 속도 제한).
  - `auth`: 가입(항상 INSPECTOR), 세션 로그인(세션 ID 재발급, CSRF 토큰 재발급), 5회 실패 15분 잠금(423 `ACCOUNT_LOCKED`), 로그아웃, `/me`. CSRF 는 `XSRF-TOKEN` 쿠키 + `X-XSRF-TOKEN` 헤더(`SpaCsrfTokenRequestHandler`).
  - `studio`: `StudioHttpClient`(업로드 시 원래 파일 이름 대신 `document.pdf` 전송, 오류는 상태·code 만 로그), `ReplayStudioGateway`(`eval/testmdfile/api-20261106-111210/*_raw.json` 재생, 파일 이름에 `set02`·`set10`이 있으면 그 응답, 단계별 진행 흉내), `StudioResultMapper`.
  - `result`: `ReportParser`(compare.py 와 같은 규칙, 실패 시 UNKNOWN), `DisplayValueMasker`(BR09), `AnalysisResultService`(원문값·보고서를 암호화 저장, 결과 응답은 표시값만).
  - `analysis`: 업로드 검증(시그니처, 암호 PDF 거부, 크기·쪽수·건수 제한 없음), `AnalysisJobProcessor`(UPLOADING→QUEUED→RUNNING→COMPLETED/FAILED, 재시도, 시간 초과, Studio 파일 삭제와 재삭제), `RetentionJob`(매시간 PURGED), 원본 파일(no-store, 세션당 첫 열람 감사), 원문 보기(사유 필수, 감사), 삭제, 다시 실행(`POST /api/analyses/{id}/retry`, ARCHITECTURE 5장에 없던 경로).
  - `review`: 대기열(자기 분석 제외), 검토 기록(이의는 사유 필수, 한 번만, 등급은 바꾸지 않음).
  - `user/AccountSeeder` + `scripts/seed-accounts.*`: inspector·inspector2·reviewer·admin `@docguard.local` 계정, 비밀번호는 `.local/accounts.txt`에만 기록(이미 만듦).
  - 테스트 62건 통과, SKIPPED 없음: 보고서 파서(v2.5 10건 정답 일치 + UNKNOWN 5종), 변환기(set02·set10 원본 응답), HTTP 클라이언트(가짜 서버, 헤더·본문·오류), 가림 규칙, 암호화, 인증 흐름, 분석 흐름(결과 응답에 원문값 없음 확인), `AuthorizationMatrixTest`(14개 경로 × 4역할).
- 프런트엔드
  - `src/api`(fetch 래퍼: CSRF 쿠키→헤더, 토큰 만료 시 한 번 재시도, 401 이면 로그인으로), 로그인·가입(안내 동의, 비밀번호 규칙), 역할별 메뉴, `RequireAuth`(원래 경로 기억), 내 분석 목록 + 업로드, 검토 대기열.
  - 분석 화면: 진행 단계 7개 표시, 결과 요약(등급·검토·재생 모드 경고·UNKNOWN 경고), `react-pdf` 문서 뷰어(가림 상자, 원문 보기 시 테두리), 정보주체 역할별 발견 항목, 보고서 【†n】 → 문서 위치 이동, 원문 보기 대화상자, 검토 입력, 실패 시 다시 실행·삭제. 1280px 이상은 세 영역, 그보다 좁으면 탭.
  - Vitest 13건 통과, `tsc -b`·`npm run build` 성공, `npm audit --omit=dev` 0건. 빌드 결과 JS 가 1.1MB(react-pdf 포함)라 크기 경고가 납니다.
- 직접 확인: 재생 모드 서버 + Vite 로 set02 원본 PDF 를 올려 진행 표시 → 완료 → 상자 위치 → 인용 5번 이동 → 원문 보기까지 브라우저에서 확인함. 서버 로그에 원문값·파일 이름 0건, `storage/`의 파일은 모두 암호문.

**실제 API 연결 방법 (사용자가 할 일):**

1. `.env`를 편집기로 열어 `UPSTAGE_API_KEY`, `UPSTAGE_PROJECT_ID`, `UPSTAGE_AGENT_ID`를 넣고 `UPSTAGE_MODE=live`로 바꿉니다. 키는 채팅에 붙이지 않습니다.
2. `server/gradlew bootRun`, `web/npm run dev` 후 `.local/accounts.txt`의 inspector 계정으로 로그인해 set02 를 올립니다.
3. 결과가 재생 모드와 같은지 확인하고, 이어서 10건을 웹에서 실행해 정답표와 비교합니다(F12).

**M0 결과 (2026-11-08 확인):**

- 루트: `docker-compose.yml`(컨테이너 `docguard-postgres`, 볼륨 `docguard-pgdata`, 5434), `.env.example`(이름만), `scripts/create-env.ps1`(기본, UTF-8 BOM으로 저장해야 PowerShell 5.1에서 한글이 깨지지 않음), `scripts/create-env.sh`. 사용자 PC에는 `.env`를 이미 만들었고 Upstage 값 3개는 비어 있습니다.
- `server/`: Gradle 8.14 wrapper, `com.imbank.docguard.DocguardApplication`, `auth/SecurityConfig`(health만 공개, 그 밖의 actuator와 `/api/**`는 401, 폼 로그인·Basic 끔, CSP·Referrer-Policy 헤더), `application.yml`(`ddl-auto: validate`, 세션 쿠키 `DOCGUARD_SESSION`, `upstage.*`·`app.*` 속성 자리), `application-local.yml`(`.env` 읽기), `V1__init_schema.sql`(테이블 6개, CHECK 제약, 부분 인덱스). 기본 사용자를 만들어 임시 비밀번호를 로그에 찍는 `UserDetailsServiceAutoConfiguration`은 꺼 두었습니다. JPA 엔티티는 아직 없습니다.
- 테스트: `SchemaMigrationIntegrationTest` 5건 통과(Flyway V1 적용, 테이블 6개, 평문 원문값 컬럼 없음, 역할 CHECK, health 공개, 나머지 401). SKIPPED 없음.
- `bootRun`: local 프로필로 Docker DB에 V1을 적용했고 `/actuator/health`가 `{"status":"UP"}`을 돌려줌. Vite 프록시로 `/api/analyses`를 부르면 서버의 401이 돌아옴.
- `web/`: `/`(내 분석 빈 상태), `/login`(틀만), `*`(404). `npm test` 3건 통과, `npm run build` 성공. `npm audit` 경고 6건(낮음 2, 보통 4)은 모두 개발 도구인 shadcn CLI의 하위 의존성이며, `npm audit --omit=dev`는 0건입니다.

**다음 할 일:**

1. 나머지 평가 문서 9건을 웹에서 실행해 10건 채점(F12 시연 정확도 화면의 근거).
2. 시연 정확도 화면은 사용자 요청으로 사이트에서 숨겨 두었습니다(아래 참고). 다시 쓰려면 `web/src/lib/features.ts`의 `SHOW_ACCURACY`를 켭니다.
3. P2 기능(사용자 결정 후): 가린 PDF 다운로드(F13), 여러 문서 한 번에 올리기(F14), 외부 배포(F15). 감사 기록 1년 보관 정리 작업도 아직 없습니다(보관 기간 TBD).
4. 네이버클라우드 배포는 끝났습니다. 공개 주소에서 로그인과 실제 문서 분석을 확인합니다.
5. 문서와 다른 점을 기획 문서에 반영할지 사용자에게 확인: `UPSTAGE_MODE`·`UPSTAGE_REPLAY_DIR` 환경 변수, `POST /api/analyses/{id}/retry`, `GET /api/admin/analyses`, `GET /api/demo/accuracy`, 상태 조회기 위치(analysis 패키지), 암호화 결과 JSON 의 키 이름(영문).

**사용자 결정 대기 (TBD):**

- 서비스 이름, 대회 제출일·시연일과 제출물 형식 ([PRD](./PRD.md) 9장)
- 원본 보관 기간(기본 7일), 감사 기록 보관 기간 ([SECURITY](./SECURITY.md) 6장)
- 검토자의 "이의"를 등급 표시에 반영할지 (지금은 별도 의견)
- `studio/*.json`과 `WORKFLOW_CHANGES.md`를 공개 저장소에 올릴지 (지금은 `.gitignore`)
- 워크플로 팀 결정 항목 (WORKFLOW_CHANGES.md 8장)

## 9. 환경 주의점

- 사용자 PC는 Windows 11이고, 사용자 홈 경로에 한글이 들어 있습니다.
- 전역 `gradle`이 없습니다. `~/.gradle/wrapper/dists`에 캐시된 Gradle 8.14로 wrapper를 만듭니다.
- Docker Desktop이 꺼져 있을 때가 있습니다. Testcontainers는 1.21.4 이상이어야 이 PC의 Docker 29와 통신합니다.
- Node.js 24.15, JDK 21, Python 3.13이 설치되어 있습니다.
- `studio/api_probe.py`를 실행하려면 `pip install openai`와 환경 변수 `UPSTAGE_API_KEY`가 필요합니다.
