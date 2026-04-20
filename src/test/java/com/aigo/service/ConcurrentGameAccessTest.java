package com.aigo.service;

import com.aigo.ai.KataGoEngine;
import com.aigo.config.EngineProperties;
import com.aigo.config.SessionProperties;
import com.aigo.model.Game;
import com.aigo.model.GameState;
import com.aigo.model.NewGameRequest;
import com.aigo.model.Stone;
import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 동일 gameId 로 들어오는 동시 요청이 내부 Game 상태를 훼손하지 않고
 * 직렬화되는지, 서로 다른 gameId 는 독립적으로 실행되는지 검증한다.
 */
class ConcurrentGameAccessTest {

    /**
     * 같은 게임에 20 개 스레드가 동시에 각기 다른 좌표로 착수를 시도한다.
     * 착수는 board.isValidMove → makeMove 순서로 수행되며, 락이 없으면 중간 상태가
     * 파손되어 board / moveHistory / boardHistory 크기가 어긋날 수 있다.
     * 락이 제대로 걸렸다면 성공 카운트와 실제 보드 상태가 정확히 일치해야 한다.
     */
    @Test
    void concurrentMovesOnSameGame_serializeConsistently() throws Exception {
        KataGoEngine engine = mock(KataGoEngine.class);
        // AI 호출은 비활성화하여 순수 makeMove 경합만 측정
        when(engine.isAvailable()).thenReturn(false);

        GameService svc = newService(engine);

        NewGameRequest req = new NewGameRequest();
        req.boardSize = 19;
        req.playerColor = "BLACK";
        req.difficulty = "HARD";
        String gameId = svc.newGame(req).gameId;
        Game game = extractGame(svc, gameId);

        int threadCount = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger validMoveCount = new AtomicInteger();

        try {
            Future<?>[] tasks = new Future<?>[threadCount];
            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                tasks[i] = pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        // 고유 좌표: 0..19x0..19 중 threadIdx 별 서로 다른 포인트
                        int row = idx / 19;
                        int col = idx % 19;
                        GameState state = svc.playerMove(gameId, row, col);
                        // 메시지가 에러가 아닌 경우만 카운트 (AI not-available 이라 패스되는 등)
                        // 보드 내 실제 돌 수 기준으로 검증하므로 카운트는 참고용
                        if (state.message == null || !state.message.contains("유효하지 않은")) {
                            if (state.message == null || !state.message.contains("당신의 차례가 아닙니다")) {
                                validMoveCount.incrementAndGet();
                            }
                        }
                    } catch (Exception ignored) {
                        // race 발생 시 상태 검증에서 감지됨
                    }
                });
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> f : tasks) f.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdown();
        }

        // 검증 — KataGo 비활성 상태에서 applyAiMove 는 game.pass() 를 호출하므로
        // 사용자 착수 1회 = moveHistory 2건(흑 착수 + 백 패스). 락이 깨지면 이 관계가 어긋난다.
        int blackStonesOnBoard = 0;
        for (int r = 0; r < 19; r++) {
            for (int c = 0; c < 19; c++) {
                if (game.getBoard().getStone(r, c) == Stone.BLACK) blackStonesOnBoard++;
            }
        }

        long blackNonPassInHistory = game.getMoveHistory().stream()
                .filter(rec -> !rec.pass && rec.color == Stone.BLACK)
                .count();
        long whitePassInHistory = game.getMoveHistory().stream()
                .filter(rec -> rec.pass && rec.color == Stone.WHITE)
                .count();

        // 보드 위 흑돌 수 = 실제 착수한 흑 착수 수 (moveHistory 기준)
        assertThat(blackNonPassInHistory).isEqualTo(blackStonesOnBoard);
        // 흑 착수마다 AI 가 패스 → 흑 착수 수 == 백 패스 수
        assertThat(whitePassInHistory).isEqualTo(blackNonPassInHistory);
    }

    /**
     * 서로 다른 gameId 는 락이 독립적이어야 한다.
     * 두 스레드가 동시에 각자의 게임을 조작해도 데드락·블로킹 없이 빠르게 완료되어야 한다.
     */
    @Test
    void differentGameIds_doNotBlockEachOther() throws Exception {
        KataGoEngine engine = mock(KataGoEngine.class);
        when(engine.isAvailable()).thenReturn(false);

        GameService svc = newService(engine);

        NewGameRequest req1 = new NewGameRequest();
        req1.boardSize = 9;
        req1.playerColor = "BLACK";
        req1.difficulty = "EASY";
        NewGameRequest req2 = new NewGameRequest();
        req2.boardSize = 9;
        req2.playerColor = "BLACK";
        req2.difficulty = "EASY";

        String g1 = svc.newGame(req1).gameId;
        String g2 = svc.newGame(req2).gameId;

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        long deadline = System.currentTimeMillis() + 3000;
        Future<?> t1 = pool.submit(() -> {
            try { start.await(); } catch (InterruptedException ignored) { return; }
            int idx = 0;
            while (System.currentTimeMillis() < deadline) {
                int row = idx / 9, col = idx % 9;
                if (row >= 9) break;
                svc.playerMove(g1, row, col);
                idx++;
            }
        });
        Future<?> t2 = pool.submit(() -> {
            try { start.await(); } catch (InterruptedException ignored) { return; }
            int idx = 0;
            while (System.currentTimeMillis() < deadline) {
                int row = idx / 9, col = idx % 9;
                if (row >= 9) break;
                svc.playerMove(g2, row, col);
                idx++;
            }
        });

        start.countDown();
        t1.get(5, TimeUnit.SECONDS);
        t2.get(5, TimeUnit.SECONDS);
        pool.shutdown();

        // 양쪽 게임 모두 진전이 있어야 한다 (상호 블로킹 없음)
        Game game1 = extractGame(svc, g1);
        Game game2 = extractGame(svc, g2);
        assertThat(game1.getMoveHistory()).isNotEmpty();
        assertThat(game2.getMoveHistory()).isNotEmpty();
    }

    // ── helpers ──

    private static GameService newService(KataGoEngine engine) {
        SessionProperties sessionProps = new SessionProperties();
        EngineProperties engineProps = new EngineProperties();
        return new GameService(engine, sessionProps, engineProps, Ticker.systemTicker());
    }

    /** GameState 로는 Game 레퍼런스 접근이 불가하므로 서비스 재조회 후 가져온다. */
    private static Game extractGame(GameService svc, String gameId) throws Exception {
        // GameService 는 Game 을 직접 노출하지 않으므로 GameState 에서 보드 상태를 검사하면 된다.
        // 이 헬퍼는 내부 필드 접근이 필요하여 reflection 을 사용한다.
        var sessionsField = GameService.class.getDeclaredField("sessions");
        sessionsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var cache = (com.github.benmanes.caffeine.cache.Cache<String, GameSession>) sessionsField.get(svc);
        GameSession session = cache.getIfPresent(gameId);
        return session == null ? null : session.game();
    }
}
