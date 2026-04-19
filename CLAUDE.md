# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
./gradlew build -x test   # 빌드 (프론트엔드 npm build 포함, 테스트 제외)
./gradlew bootRun          # 서버 실행 (http://localhost:22001)
./gradlew test             # 보안 테스트 (Caffeine 세션 evict, 레이트 리밋 필터)
```

### 프론트엔드 개발 모드
```bash
cd frontend && npm run dev   # Vite dev server: http://localhost:5173
# /api 요청은 자동으로 localhost:22001 (Spring Boot) 로 프록시
```

**사전 요구사항:** KataGo가 설치되어 있어야 AI가 동작함. 없으면 AI가 자동 패스(그레이스풀 폴백).
KataGo 경로는 `src/main/resources/application.properties`에서 설정.

## Architecture

Spring Boot 3.3.4 + Java 23 웹 기반 바둑 게임. KataGo AI와 GTP 프로토콜로 통신.

**요청 흐름:**
```
Browser (Canvas + JS) → GameController (REST) → GameService → Game/Board (규칙) + KataGoEngine (AI)
```

**핵심 계층:**
- **`controller/GameController`** — REST 엔드포인트 (`/api/game/...`). 라우팅만 담당
- **`service/GameService`** — 게임 세션 관리(`ConcurrentHashMap`), 플레이어 착수 → AI 응수 오케스트레이션
- **`model/Game`** — 게임 상태 머신. 착수, 패스, 기권, 슈퍼코 감지, 한국식 계가
- **`model/Board`** — 바둑 규칙 엔진. 착수 유효성, 포획, 사활, 영역 계산
- **`ai/KataGoEngine`** — KataGo 서브프로세스 관리. `ReentrantLock`으로 GTP 명령 직렬화
- **`frontend/src`** — React 19 + TS + Tailwind. Canvas 렌더링은 `canvas/boardRenderer.ts` 순수 함수로 분리, `GoBoard` 컴포넌트가 useRef+useEffect 로 호출. 서버 상태는 TanStack Query 훅(`api/hooks.ts`)으로 관리

**스레드 안전:** KataGoEngine은 단일 프로세스를 `ReentrantLock`으로 보호. GameService는 Caffeine 캐시(LRU + 활성/종료 2단계 TTL)로 게임 세션 관리.

## KataGo GTP 통신 주의사항

- `sendRawCommand()`는 **블로킹** — GTP 응답(`= ...\n\n`)까지 대기
- `kata-analyze`는 **스트리밍** 명령 — 초기 `=\n\n` 응답 후 `info` 줄을 지속 출력. 반드시 `reader.ready()` 논블로킹 읽기 + `syncAfterAnalysis()`로 GTP 상태 복구 필요
- `kata-analyze` 출력: 수마다 별도 줄(`info move D4 visits 231 winrate 0.816 ... order 0`), 갱신 배치 사이 빈 줄 구분
- 좌표 변환: 내부 `[row, col]` (0-indexed, row 0=상단) ↔ GTP `D16` (column A-T, no I)

## 한국식 계가 규칙

- `흑 점수 = 잡은 백돌 + 영역`
- `백 점수 = 잡은 흑돌 + 영역 + 코미(6.5)`
- Board.java의 `score()` 메서드에서 flood fill로 영역 계산

## API 엔드포인트

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/game/new` | 새 게임 (`{boardSize, playerColor, difficulty}`) |
| POST | `/api/game/{id}/move` | 착수 (`{row, col}`) → AI 자동 응수 |
| POST | `/api/game/{id}/pass` | 패스 → AI 자동 응수 |
| POST | `/api/game/{id}/resign` | 기권 |
| GET | `/api/game/{id}` | 상태 조회 |
| GET | `/api/game/{id}/hints` | 힌트 (최대 5개 추천수 + 승률) |

## 프론트엔드

**스택:** Vite + React 19 + TypeScript + Tailwind CSS v3 + TanStack Query + Lucide React.

**Canvas 격리 원칙:**
- `canvas/boardRenderer.ts` 는 React 비의존 순수 함수. 바둑판의 모든 그리기 로직이 여기 모여 있음.
- `components/GoBoard.tsx` 가 `useRef<HTMLCanvasElement>` + `useEffect` 로 렌더러를 호출. VDOM 다이프 밖에서 그리므로 `react-konva` 같은 래퍼가 불필요.

**상태 관리:**
- **UI 상태** (선택된 보드 크기·색·난이도, 힌트 표시 여부): `useState` / `useReducer`
- **서버 상태** (게임 상태, 힌트 결과): `@tanstack/react-query` 훅 (`api/hooks.ts`)
- **착수 최적화**: `usePlayerMove` 의 `onMutate` 에서 돌을 즉시 그리고(optimistic update), 실패 시 자동 롤백

**빌드 파이프라인:**
- `./gradlew build` → `buildFrontend` (npm run build) → `src/main/resources/static/` 산출 → `bootJar` 포함
- `src/main/resources/static/` 은 빌드 산출물이므로 `.gitignore` 처리됨 (git으로 추적하지 않음)
