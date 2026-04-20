# aigo

> **웹 기반 AI 바둑 게임** — Spring Boot + KataGo 엔진 + React 프론트엔드
>
> 한국식 계가 규칙, 다크 테마 UI, 힌트 기능, 실시간 승률 표시.

[![Release](https://img.shields.io/github/v/release/focuschange/aigo)](https://github.com/focuschange/aigo/releases)
[![Java](https://img.shields.io/badge/Java-23-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-19-61dafb)](https://react.dev/)

---

## ✨ 주요 기능

- **실제 서비스급 AI** — KataGo 최강 모델(`kata1-b18c384nbt`) GTP 연동, Apple M2 Max Metal 가속
- **3가지 판 크기** — 9×9 / 13×13 / 19×19
- **한국식 계가** — `빈집(영역) + 잡은 돌 + 코미(6.5)`
- **힌트 시스템** — `kata-analyze` 기반 상위 추천수 5개 + 승률 표시
- **난이도 3단계** — EASY / MEDIUM / HARD
- **실시간 승률 바** — 매 수마다 흑 승률 갱신
- **Canvas 렌더링** — 격자, 화점, 돌, 마지막 수 마커, 힌트 오버레이 직접 그림
- **다크 테마 UI** — Tailwind CSS v3 기반

## 🔒 보안 특성 (v2026.04.20 기준)

| 영역 | 구현 |
|------|------|
| DoS 방지 (세션) | Caffeine LRU + 2단계 TTL (활성 60분 / 종료 5분), 최대 5,000 세션 |
| DoS 방지 (네트워크) | Bucket4j IP 기반 레이트 리밋 — `/api/game/new` 10 req/min |
| DoS 방지 (엔진) | fair `ReentrantLock` + `tryLock(3s)` → 503+Retry-After, 힌트 쿨다운 5초 → 429+Retry-After |
| CSRF | Spring Security + `CookieCsrfTokenRepository` (double-submit cookie, BREACH-safe) |
| 입력 검증 | Jakarta Validation + `@RestControllerAdvice` 중앙 매핑 |
| 에러 응답 | Whitelabel/스택트레이스 노출 차단, 고정 문구 반환 |
| 동시성 | `GameSession` 당 `ReentrantLock` 으로 동일 gameId 직렬화 |
| HTTP 크기 제한 | `max-http-form-post-size=64KB`, `multipart.max-request-size=1MB` |

상세: [`CHANGELOG.md`](./CHANGELOG.md)

---

## 🏗️ 아키텍처

```
┌─────────────────────┐   HTTP/JSON   ┌────────────────────────┐   GTP stdio   ┌──────────┐
│  Browser (React)    │◀─────────────▶│  Spring Boot (22001)   │◀─────────────▶│  KataGo  │
│  Vite + TS + TW     │               │  Controller → Service  │               │ (subproc)│
│  TanStack Query     │               │  Caffeine cache        │               └──────────┘
│  Canvas 2D          │               │  Bucket4j rate-limit   │
└─────────────────────┘               │  Spring Security (CSRF)│
                                      └────────────────────────┘
```

**핵심 계층**

- `controller/GameController` — REST 엔드포인트 (`/api/game/...`), 라우팅만
- `service/GameService` — 세션 관리(Caffeine), 플레이어 착수 → AI 응수 오케스트레이션
- `model/Game`, `model/Board` — 바둑 규칙 엔진 (착수, 포획, 패/슈퍼코, 영역 계산)
- `ai/KataGoEngine` — KataGo 서브프로세스 관리, fair `ReentrantLock` 으로 GTP 명령 직렬화
- `config/SecurityConfig` — CSRF + STATELESS 세션 + permitAll
- `ratelimit/NewGameRateLimitFilter` — IP 단위 토큰 버킷
- `frontend/src` — React 19 + TypeScript + Tailwind v3 + TanStack Query + Lucide

---

## 🚀 Quick Start

### 사전 요구사항

1. **Java 23** (Amazon Corretto 권장)
2. **KataGo** 설치 (없어도 앱은 실행되며 AI 는 자동 패스)

   ```bash
   brew install katago          # macOS
   # 기본 모델과 설정이 /opt/homebrew/share/katago/ 아래 설치됨
   ```

3. **Node.js 18+** (프론트엔드 빌드용, 시스템 설치 사용)

### 빌드 & 실행

```bash
# 프로덕션 빌드 (프론트엔드 Vite 빌드 + Spring Boot bootJar)
./gradlew build -x test

# 서버 실행 → http://localhost:22001
./gradlew bootRun

# 테스트 (31개)
./gradlew test
```

### 프론트엔드 개발 모드 (HMR)

```bash
cd frontend && npm run dev       # Vite dev server: http://localhost:5173
```

`/api` 요청은 자동으로 `localhost:22001` (Spring Boot) 로 프록시됩니다.

### KataGo 경로 커스터마이즈

`src/main/resources/application.properties`:

```properties
katago.executable=/opt/homebrew/bin/katago
katago.model=/opt/homebrew/share/katago/kata1-b18c384nbt-s9996604416-d4316597426.bin.gz
katago.config=/opt/homebrew/share/katago/configs/gtp_example.cfg
```

---

## 📡 API

| Method | Endpoint | 설명 |
|--------|----------|------|
| `POST` | `/api/game/new` | 새 게임 — `{boardSize, playerColor, difficulty}` |
| `POST` | `/api/game/{id}/move` | 착수 — `{row, col}` → AI 자동 응수 |
| `POST` | `/api/game/{id}/pass` | 패스 → AI 자동 응수 |
| `POST` | `/api/game/{id}/resign` | 기권 |
| `GET`  | `/api/game/{id}` | 상태 조회 |
| `GET`  | `/api/game/{id}/hints` | 힌트 (최대 5개 추천수 + 승률) |
| `GET`  | `/api/csrf` | CSRF 토큰 쿠키 프라이밍 (204 No Content) |

### CSRF 사용법

- 서버가 `XSRF-TOKEN` 쿠키를 내려줌 (JS 읽기 가능)
- 상태 변경 요청(`POST`/`PUT`/`DELETE`) 시 동일 값을 `X-XSRF-TOKEN` 헤더로 재전송
- 프론트엔드(`frontend/src/api/client.ts`)는 자동 주입 + 쿠키 없으면 priming 수행

```bash
# 수동 예시
curl -c cookies.txt http://localhost:22001/api/csrf
TOKEN=$(grep XSRF-TOKEN cookies.txt | awk '{print $NF}')
curl -X POST http://localhost:22001/api/game/new \
  -b cookies.txt -H "X-XSRF-TOKEN: $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"boardSize":9,"playerColor":"BLACK","difficulty":"EASY"}'
```

### 에러 응답

| 코드 | 상황 |
|------|------|
| `400` | `@Valid` 실패, 잘못된 JSON, 범위 초과 |
| `403` | CSRF 토큰 누락/불일치 |
| `410 Gone` | 게임 세션 만료 또는 미존재 |
| `429 Too Many Requests` | 레이트 리밋 초과, 힌트 쿨다운 중 (`Retry-After` 헤더 포함) |
| `503 Service Unavailable` | KataGo 엔진 혼잡 (`Retry-After` 헤더 포함) |

---

## 🧰 기술 스택

### 백엔드
- Java 23 · Spring Boot 3.3.4 · Gradle 8.10
- Spring Security (CSRF)
- Jakarta Validation
- Caffeine 3.1.8 (세션 캐시)
- Bucket4j 8.10.1 (레이트 리밋)
- KataGo v1.16.4 (외부 프로세스, GTP 프로토콜)

### 프론트엔드
- React 19 · TypeScript · Vite 8
- Tailwind CSS v3
- TanStack Query v5 (서버 상태)
- Lucide React (아이콘)
- Canvas 2D (바둑판 렌더링 — `frontend/src/canvas/boardRenderer.ts` 순수 함수)

### 테스트 (31개)
- JUnit 5 + Mockito + AssertJ
- Spring Boot Test (`@WebMvcTest`, `@SpringBootTest`)
- Spring Security Test (`.with(csrf())`)

---

## 📁 프로젝트 구조

```
aigo/
├── src/main/java/com/aigo/
│   ├── AiGoApplication.java
│   ├── controller/      # REST 엔드포인트 + GlobalExceptionHandler + CsrfController
│   ├── service/         # GameService, GameSession
│   ├── model/           # Game, Board, Stone, GameState, 요청/응답 DTO
│   ├── ai/              # KataGoEngine + EngineBusyException, HintCooldownException
│   ├── config/          # SecurityConfig, SessionProperties, EngineProperties, RateLimitConfig
│   └── ratelimit/       # NewGameRateLimitFilter
├── src/main/resources/
│   ├── application.properties
│   └── static/          # Vite 빌드 산출물 (gitignored)
├── src/test/java/com/aigo/
│   ├── security/        # CsrfProtectionTest
│   ├── service/         # GameSessionEvictionTest, ConcurrentGameAccessTest, HintCooldownTest, EngineBusyPropagationTest
│   ├── controller/      # GameControllerExceptionMappingTest
│   └── ratelimit/       # NewGameRateLimitFilterTest
├── frontend/
│   ├── src/
│   │   ├── api/         # client.ts (CSRF 자동), hooks.ts (TanStack Query)
│   │   ├── canvas/      # boardRenderer.ts (React 비의존 순수 함수)
│   │   ├── components/  # GoBoard, Header, SetupPanel, GamePanel, Scoreboard, WinRateBar, …
│   │   ├── App.tsx
│   │   └── main.tsx
│   ├── vite.config.ts   # /api 프록시 + 빌드 산출물 경로
│   └── tailwind.config.js
├── build.gradle         # node-gradle-plugin 으로 npm build 통합
├── CHANGELOG.md
├── CLAUDE.md            # Claude Code 작업 가이드
└── README.md
```

---

## 🔄 릴리스 프로세스

**Calendar Versioning** — `vYYYY.MM.DD[.N]`

- `develop` 브랜치에서 작업, 완료 시 로컬 `develop` 에 머지
- 릴리스 시점에 `CHANGELOG.md` 정리 → `develop` 커밋 & 푸시
- `main` 에 `--no-ff` 머지 + annotated 태그 + GitHub Release

최신 릴리스: [`v2026.04.20`](https://github.com/focuschange/aigo/releases/tag/v2026.04.20)

---

## 📜 License

아직 라이선스가 지정되지 않았습니다. 사용 전 저장소 소유자(`focuschange`) 와 협의하세요.

---

## 🙋 Contributing

이슈/PR 환영합니다. 개발 가이드와 아키텍처 규약은 [`CLAUDE.md`](./CLAUDE.md) 를 참고하세요.
