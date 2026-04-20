# aigo

웹 기반 **AI 바둑 게임**. Spring Boot + KataGo 엔진 + React 프론트엔드로 구성됩니다.
한국식 계가 규칙, 다크 테마 UI, 힌트 기능, 실시간 승률 표시를 지원합니다.

[![Release](https://img.shields.io/github/v/release/focuschange/aigo)](https://github.com/focuschange/aigo/releases)
[![Java](https://img.shields.io/badge/Java-23-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-19-61dafb)](https://react.dev/)

## ✨ 기능

- **실제 서비스급 AI** — KataGo 최강 모델(`kata1-b18c384nbt`) GTP 연동
- **3가지 판 크기** — 9×9 / 13×13 / 19×19
- **3단계 난이도** — EASY / MEDIUM / HARD
- **한국식 계가** — 빈집(영역) + 잡은 돌 + 코미(6.5)
- **힌트 시스템** — 상위 추천수 5개 + 승률 표시 (`kata-analyze`)
- **실시간 승률 바** — 매 수마다 흑 승률 갱신
- **다크 테마 UI** — React + Tailwind CSS 기반

## 🛠️ 설치

### 사전 요구사항

| 구성 | 버전 |
|------|------|
| Java | 23 (Amazon Corretto 권장) |
| Node.js | 18 이상 (프론트엔드 빌드) |
| KataGo | v1.16.x (없으면 AI 가 자동 패스로 동작) |

### KataGo 설치 (macOS)

```bash
brew install katago
# 실행 파일, 모델, 설정이 /opt/homebrew/ 아래 자동 배치됨
```

다른 경로에 설치된 경우 `src/main/resources/application.properties` 의 아래 값을 조정하세요:

```properties
katago.executable=/opt/homebrew/bin/katago
katago.model=/opt/homebrew/share/katago/kata1-b18c384nbt-s9996604416-d4316597426.bin.gz
katago.config=/opt/homebrew/share/katago/configs/gtp_example.cfg
```

### 저장소 받기

```bash
git clone https://github.com/focuschange/aigo.git
cd aigo
```

## 🚀 실행

```bash
# 빌드 (프론트엔드 Vite 빌드 + Spring Boot bootJar)
./gradlew build -x test

# 서버 실행
./gradlew bootRun
```

브라우저에서 **http://localhost:22001** 접속 → 새 게임 시작.

### 테스트

```bash
./gradlew test   # 31개 테스트 (세션/레이트 리밋/엔진/동시성/CSRF/예외 매핑)
```

### 프론트엔드 개발 모드 (HMR)

```bash
cd frontend && npm run dev
# Vite dev server: http://localhost:5173
# /api 요청은 자동으로 localhost:22001 로 프록시
```

## 📁 구조

```
aigo/
├── src/main/java/com/aigo/      # Spring Boot 백엔드
│   ├── controller/              # REST 엔드포인트
│   ├── service/                 # 게임 세션 관리
│   ├── model/                   # 바둑 규칙 엔진 (Game, Board)
│   ├── ai/                      # KataGoEngine (GTP 통신)
│   ├── config/                  # 보안/세션/엔진/레이트 리밋 설정
│   └── ratelimit/               # IP 기반 토큰 버킷 필터
├── frontend/                    # React 19 + TypeScript + Tailwind
│   └── src/
│       ├── canvas/              # 바둑판 Canvas 렌더러 (순수 함수)
│       ├── components/          # UI 컴포넌트
│       ├── api/                 # REST 클라이언트 (CSRF 자동)
│       └── App.tsx
├── build.gradle                 # npm build 통합
└── CHANGELOG.md
```

## 🔗 더 보기

- **상세 문서·API 레퍼런스·아키텍처**: [`CLAUDE.md`](./CLAUDE.md)
- **변경 이력**: [`CHANGELOG.md`](./CHANGELOG.md)
- **최신 릴리스**: [v2026.04.20](https://github.com/focuschange/aigo/releases/tag/v2026.04.20)

## 📜 라이선스

아직 라이선스가 지정되지 않았습니다.
