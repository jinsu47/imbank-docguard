# docguard web

금융문서 민감정보 점검 서비스의 React 화면입니다. 구성과 규칙은 상위 디렉터리의 문서를 따릅니다.

```bash
npm install
npm run dev      # http://localhost:5173, /api 는 http://localhost:8080 으로 프록시
npm test         # Vitest
npm run build    # tsc -b && vite build → dist/
npm run lint     # oxlint
npm run format   # Prettier
```

- 스택: React 19, Vite 8, TypeScript(strict), React Router, TanStack Query, Tailwind CSS 4, shadcn/ui
- shadcn/ui 컴포넌트 추가: `npx shadcn@4.23.0 add <이름>` (결과는 `src/components/ui/`)
