package com.aigo.service;

import com.aigo.ai.KataGoEngine;
import com.aigo.config.SessionProperties;
import com.aigo.model.NewGameRequest;
import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * GameService 세션 캐시 동작 테스트 (DoS 방지용 LRU + TTL).
 *
 * 실제 시계 대신 {@link FakeTicker}로 Caffeine 내부 시간을 조작한다.
 */
class GameSessionEvictionTest {

    private KataGoEngine kataGo;
    private FakeTicker ticker;

    @BeforeEach
    void setUp() {
        kataGo = mock(KataGoEngine.class);
        // 테스트가 KataGo 호출에 의존하지 않도록 not-available 로 고정
        when(kataGo.isAvailable()).thenReturn(false);
        ticker = new FakeTicker();
    }

    private GameService buildService(int maxActive, long ttlActiveMin, long ttlEndedMin) {
        SessionProperties props = new SessionProperties();
        props.setMaxActive(maxActive);
        props.setTtlActiveMinutes(ttlActiveMin);
        props.setTtlEndedMinutes(ttlEndedMin);
        return new GameService(kataGo, props, ticker);
    }

    private NewGameRequest blackRequest() {
        NewGameRequest r = new NewGameRequest();
        r.boardSize = 9;
        r.playerColor = "BLACK"; // AI 선착수 회피
        r.difficulty = "HARD";
        return r;
    }

    @Test
    void maximumSize_evictsOldestWhenCapacityExceeded() {
        GameService svc = buildService(2, 60, 5);

        String id1 = svc.newGame(blackRequest()).gameId;
        String id2 = svc.newGame(blackRequest()).gameId;
        String id3 = svc.newGame(blackRequest()).gameId; // 용량 초과 → LRU evict

        // 용량 초과로 가장 오래된 세션이 제거되어야 한다
        assertThat(svc.activeSessionCount()).isLessThanOrEqualTo(2);
        assertThat(svc.hasSession(id3)).isTrue();
        // id1 또는 id2 중 최소 하나는 evict 되어야 한다
        assertThat(svc.hasSession(id1) && svc.hasSession(id2)).isFalse();
    }

    @Test
    void activeSession_expiresAfterIdleTtl_andSubsequentAccessThrows() {
        GameService svc = buildService(100, 10, 5); // active 10분 (마지막 access 이후)

        String gameId = svc.newGame(blackRequest()).gameId;
        assertThat(svc.hasSession(gameId)).isTrue();

        // 접근 후 TTL 초과 (마지막 access 로부터 activeTtl+여유)
        ticker.advance(Duration.ofMinutes(11));
        assertThatThrownBy(() -> svc.getState(gameId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Game not found");
    }

    @Test
    void activeSession_ttlIsRefreshedOnAccess() {
        GameService svc = buildService(100, 10, 5);

        String gameId = svc.newGame(blackRequest()).gameId;

        // TTL 직전 접근으로 활성 TTL 리셋
        ticker.advance(Duration.ofMinutes(9));
        assertThat(svc.hasSession(gameId)).isTrue();

        // 리셋 이후 다시 9분 경과: 마지막 access 로부터 9분 → 아직 만료 전
        ticker.advance(Duration.ofMinutes(9));
        assertThat(svc.hasSession(gameId)).isTrue();
    }

    @Test
    void endedSession_expiresAfterShorterTtl() {
        GameService svc = buildService(100, 60, 5); // active 60분, ended 5분

        String gameId = svc.newGame(blackRequest()).gameId;
        // 기권 → 게임 종료 상태로 전환. TTL 카테고리가 ended 로 전환된다.
        svc.playerResign(gameId);

        // ended TTL(5분) 이전에는 결과 조회 가능
        ticker.advance(Duration.ofMinutes(3));
        assertThat(svc.hasSession(gameId)).isTrue();

        // ended TTL 초과 → evict
        ticker.advance(Duration.ofMinutes(3));
        assertThatThrownBy(() -> svc.getState(gameId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Caffeine Ticker 의 테스트용 구현.
     * {@code System.nanoTime()} 대신 호출자가 명시적으로 전진시킨 nano-time 을 반환한다.
     */
    private static final class FakeTicker implements Ticker {
        private final AtomicLong nanos = new AtomicLong();

        @Override
        public long read() {
            return nanos.get();
        }

        void advance(Duration d) {
            nanos.addAndGet(d.toNanos());
        }
    }
}
