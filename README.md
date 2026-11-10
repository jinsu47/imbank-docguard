# iM Bank DocGuard

**금융 문서 민감정보 탐지 · 위험도 분류 AI Agent**

- 팀명: **금융보안원이입다잘부탁드립니다**
- 용도: iM뱅크 AI Agent 대회 프로젝트 협업
- 저장소 상태: 웹 서비스(`server/`, `web/`) 구현 추가

## 프로젝트 목표

금융 문서에서 민감정보 후보를 식별하고, 판단 근거와 위험등급을 담은 분석보고서를 만들어 검토를 돕습니다.

처리 흐름:

1. 사용자가 웹에서 문서(PDF, JPG, PNG)를 올립니다.
2. 서버가 Upstage Studio 워크플로를 호출합니다. 판정(위험 등급, 사람 검토 여부)은 워크플로에서만 내립니다.
3. 서버가 결과를 해석해 암호화 저장합니다.
4. 화면이 등급, 문서 위 위치, 판정 근거를 보여 줍니다.
5. 사람 검토가 필요한 문서는 검토자가 확인합니다.

## 구현 범위

| 구분 | 내용 |
| --- | --- |
| 구현됨 | 회원가입·로그인, 역할(점검 담당자, 검토자, 관리자), 문서 업로드와 진행 표시, 결과 요약, 문서 위 가림 상자·돋보기·위치 확대, 발견 항목, 보고서 근거 연결, 원문 보기(사유·감사 기록), 검토 대기열과 검토 기록, 계정 관리, 감사 기록, 보관 기간 정리 |
| 계획 | 가린 PDF 다운로드, 여러 문서 한 번에 올리기, 외부 배포 |

관리자는 점검 담당자·검토자의 기능을 모두 쓸 수 있고 모든 사용자의 분석을 열어 보고 지울 수 있습니다. 다른 사람 문서의 원본 열람, 원문 보기, 삭제는 감사 기록에 남습니다.

## 저장소 구조

| 경로 | 내용 |
| --- | --- |
| `server/` | 백엔드 (Java 21, Spring Boot 3.5, PostgreSQL 16, Flyway) |
| `web/` | 프런트엔드 (React 19, Vite, TypeScript, Tailwind CSS) |
| `scripts/` | 로컬 `.env` 생성, 역할별 시험 계정 생성 |
| `docs/` | 기획, 요구사항, 위험도 기준, 회의 기록 |
| `workflows/` | Upstage Studio 워크플로 설정과 사용 방법 |
| `src/` | 초기 구조용 빈 폴더 (구현 코드는 `server/`, `web/`) |
| `tests/` | 검증 및 평가 안내 |
| `data/samples/` | 합성 문서와 가상 데이터 |
| `.github/` | 이슈 및 Pull Request 템플릿 |

## 시작하기

```bash
git clone https://github.com/jinsu47/imbank-docguard.git
cd imbank-docguard
```

비공개 저장소이므로 접근 권한이 있는 GitHub 계정이 필요합니다.

필요한 도구는 JDK 21, Node.js 24, Docker입니다. 저장소 최상위에서 다음 순서로 실행합니다.

```powershell
# 1. 로컬 .env 생성 (DB 비밀번호와 암호화 키를 무작위로 만듭니다. macOS·Linux 는 sh scripts/create-env.sh)
powershell -ExecutionPolicy Bypass -File scripts\create-env.ps1

# 2. PostgreSQL 실행 (호스트 포트 5434)
docker compose up -d

# 3. 역할별 시험 계정 생성 (비밀번호는 .local/accounts.txt 에만 기록됩니다)
powershell -ExecutionPolicy Bypass -File scripts\seed-accounts.ps1

# 4. 서버 실행 (http://localhost:8080)
cd server; .\gradlew.bat bootRun

# 5. 다른 터미널에서 화면 실행 (http://localhost:5173)
cd web; npm install; npm run dev
```

실제 분석을 하려면 `.env`에 `UPSTAGE_API_KEY`, `UPSTAGE_PROJECT_ID`, `UPSTAGE_AGENT_ID`를 넣고 `UPSTAGE_MODE=live`로 바꿉니다. `UPSTAGE_MODE=replay`는 저장해 둔 응답을 재생하는 개발용 모드이며, 응답 파일(`*_raw.json`)이 있어야 하고 올린 문서의 실제 판정이 아닙니다.

## 테스트

```bash
cd server && ./gradlew test   # 통합 테스트에는 Docker 가 필요합니다
cd web && npm test && npm run build
```

테스트는 실제 Upstage API를 호출하지 않습니다. 대회 평가 자료(`eval/`)가 없는 환경에서는 그 자료를 쓰는 테스트를 건너뜁니다.

## 협업 방식

**Issue → 작업 브랜치 → Pull Request → 리뷰 → main 병합**

- 작업 범위와 완료 기준을 이슈에 먼저 남깁니다.
- 각자 브랜치에서 작업하고 PR에 변경 목적과 확인 결과를 기록합니다.
- 기획 내용과 실제 구현·검증 결과를 구분합니다.

자세한 내용은 [협업 안내](CONTRIBUTING.md)를 참고하세요.

## 데이터와 설정

- 샘플은 합성 데이터로 구성합니다.
- 실제 고객 문서, 개인정보, API 키와 비밀번호는 커밋하지 않습니다.
- 환경변수가 필요하면 `.env.example`을 참고해 로컬 `.env`를 만듭니다.
