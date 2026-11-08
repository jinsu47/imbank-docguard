# 프로젝트 문서

기획서, 요구사항, 위험도 판단 기준, 회의 기록을 관리합니다.

문서에는 작성일과 담당자, 결정한 내용, 남은 질문을 적습니다.
기존 기획 자료는 검토 후 이 폴더에 정리합니다.

## 웹 서비스 기획 문서

`server/`, `web/` 구현의 근거가 되는 문서입니다. 코드 주석이 가리키는 `DOMAIN.md`, `SECURITY.md` 등은 이 폴더의 문서를 뜻합니다.

| 읽는 순서 | 문서 | 내용 |
| --- | --- | --- |
| 1 | [CLAUDE.md](./CLAUDE.md) | 개발 규칙, 진행 경과, 다음 할 일 |
| 2 | [PRD.md](./PRD.md) | 만드는 이유, 기능 ID(F01~F15)와 우선순위 |
| 3 | [ARCHITECTURE.md](./ARCHITECTURE.md) | 구성, Studio API 호출과 응답 해석, 서버 API, 환경 변수 |
| 4 | [USER_FLOW.md](./USER_FLOW.md) | 화면 목록과 흐름, 예외 처리 |
| 5 | [DATABASE.md](./DATABASE.md) | 테이블, 암호화 컬럼, 인덱스, 마이그레이션 규칙 |
| 6 | [DOMAIN.md](./DOMAIN.md) | 용어, 업무 규칙(BR01~BR16), 상태 전이 |
| 7 | [SECURITY.md](./SECURITY.md) | 권한 표, 인증, 로깅 금지 항목, 보관 기간 |

문서에 나오는 `WORKFLOW_CHANGES.md`, `studio/`, `eval/`은 담당자 PC에만 있고 이 저장소에는 없습니다. Upstage의 Project ID와 Agent ID는 문서에 적지 않고 `.env`의 환경 변수로만 관리합니다.
