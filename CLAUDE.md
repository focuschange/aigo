# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
./gradlew build -x test   # 빌드 (테스트 제외)
./gradlew bootRun          # 서버 실행 (http://localhost:8080)
./gradlew test             # 테스트 (현재 테스트 없음)
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
- **`static/js/game.js`** — 프론트엔드 전체. Canvas 렌더링 + REST 통신 (프레임워크 없음)

**스레드 안전:** KataGoEngine은 단일 프로세스를 `ReentrantLock`으로 보호. GameService는 `ConcurrentHashMap`으로 게임 세션 관리.

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

순수 HTML5 Canvas + JS (프레임워크 없음). `state` 객체로 모든 UI 상태 관리.
`applyState(data)` → `render()` 패턴으로 서버 응답을 Canvas에 반영.
