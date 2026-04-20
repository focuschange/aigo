# Changelog

All notable changes to **aigo** (AI Go 바둑 게임) will be documented in this file.
Format: [Keep a Changelog](https://keepachangelog.com/) · CalVer `vYYYY.MM.DD[.N]`

## [Unreleased]

## [v2026.04.20] — 2026-04-20

첫 공식 릴리스. Spring Boot + KataGo 백엔드와 React 프론트엔드 기반 AI 바둑 게임.
초기 보안 점검에서 도출된 DoS·CSRF·입력 검증·동시성 이슈를 모두 해소하고
프론트엔드를 React 스택으로 전환했다.

### Added
- #1 게임 세션 메모리 DoS 방지 — Caffeine LRU + 2단계 TTL(활성 60분 / 종료 5분), Bucket4j IP 레이트 리밋(10req/min), 세션 미존재 시 410 Gone
- #2 KataGo 프로세스 독점 DoS 방지 — fair `ReentrantLock` + `tryLock(3s)` → 503+Retry-After, 게임 단위 힌트 쿨다운(5s) → 429+Retry-After
- #5 입력 검증 — Jakarta Validation (`@Valid`, `@Pattern`, `@Min/@Max`) + `@RestControllerAdvice` 로 400 응답 중앙화, Whitelabel/스택트레이스 노출 차단
- #4 CSRF 보호 — Spring Security + `CookieCsrfTokenRepository`(double-submit) + BREACH-safe 마스킹, `/api/csrf` 프라이밍 엔드포인트, 프론트 자동 `X-XSRF-TOKEN` 주입
- `/api/game/{id}/hints` — KataGo `kata-analyze` 기반 최대 5개 추천수 + 승률
- 다크 테마 UI, 효과음, 힌트 표시, 기권/패스/계가 기능

### Changed
- 프론트엔드 전체 스택 전환: 순수 HTML/JS → Vite + React 19 + TypeScript + Tailwind CSS v3 + TanStack Query + Lucide React (#프론트엔드 전환)
- Canvas 렌더링을 순수 함수(`canvas/boardRenderer.ts`)로 격리, `GoBoard` 컴포넌트가 `useRef + useEffect` 로 호출 — VDOM 외부에서 그리기
- 착수 UX: TanStack Query `onMutate` 기반 optimistic update (실패 시 자동 롤백)
- Gradle 빌드 파이프라인: `buildFrontend`(NpmTask) → `processResources` → `bootJar` 일원화, 시스템 Node 사용
- #3 CORS 정책 — 동일 오리진으로 재편됨에 따라 `@CrossOrigin(origins = "*")` 제거
- #6 동시성 — GameSession 당 `ReentrantLock` 으로 같은 gameId 의 상태 변경 경로 직렬화 (락 순서: GameSession → KataGoEngine 고정)
- 서버 포트: 8080 → **22001**

### Security
- #1 세션/레이트 리밋 관련 보안 조치 (Added 참고)
- #2 엔진 독점 보호 (Added 참고)
- #3 CORS wildcard 제거 (Changed 참고)
- #4 CSRF 보호 (Added 참고)
- #5 입력 검증·에러 응답 (Added 참고)
- #6 동시성 경합 제거 (Changed 참고)
- #7 예외 메시지 에코 가드 — `GameController.handleError` 고정 문구로 대체
- #8 HTTP 요청 크기 제한 — `server.tomcat.max-http-form-post-size=64KB`, `spring.servlet.multipart.*` 명시

### Tests
- 총 31개 (기존 0 → 31): 세션 LRU/TTL 4, 레이트 리밋 6, 힌트 쿨다운 3, 엔진 혼잡 1, 예외/검증 매핑 10, 동시성 2, CSRF 5
