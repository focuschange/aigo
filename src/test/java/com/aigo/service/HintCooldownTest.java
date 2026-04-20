package com.aigo.service;

import com.aigo.ai.HintCooldownException;
import com.aigo.ai.KataGoEngine;
import com.aigo.ai.KataGoEngine.HintMove;
import com.aigo.config.EngineProperties;
import com.aigo.config.SessionProperties;
import com.aigo.model.NewGameRequest;
import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GameService.getHints 의 게임 단위 쿨다운 동작 검증.
 */
class HintCooldownTest {

    @Test
    void successfulHint_thenImmediateSecondCall_throwsCooldownException() {
        KataGoEngine engine = mock(KataGoEngine.class);
        when(engine.isAvailable()).thenReturn(true);
        when(engine.getHints(ArgumentMatchers.any(), ArgumentMatchers.anyInt(),
                ArgumentMatchers.any(), ArgumentMatchers.anyInt()))
                .thenReturn(List.of(new HintMove(3, 3, 0.5, 0)));

        GameService svc = newService(engine, 10_000 /* cooldown 10s */);
        String gameId = newGameId(svc);

        // 첫 번째 호출: 성공
        List<HintMove> first = svc.getHints(gameId, 5);
        assertThat(first).hasSize(1);

        // 두 번째 즉시 호출: 쿨다운 예외
        assertThatThrownBy(() -> svc.getHints(gameId, 5))
                .isInstanceOf(HintCooldownException.class);

        // KataGo 는 첫 번째 한 번만 호출되었어야 한다
        verify(engine, times(1)).getHints(ArgumentMatchers.any(), ArgumentMatchers.anyInt(),
                ArgumentMatchers.any(), ArgumentMatchers.anyInt());
    }

    @Test
    void cooldownIsPerGame_notGlobal() {
        KataGoEngine engine = mock(KataGoEngine.class);
        when(engine.isAvailable()).thenReturn(true);
        when(engine.getHints(ArgumentMatchers.any(), ArgumentMatchers.anyInt(),
                ArgumentMatchers.any(), ArgumentMatchers.anyInt()))
                .thenReturn(List.of());

        GameService svc = newService(engine, 10_000);
        String game1 = newGameId(svc);
        String game2 = newGameId(svc);

        svc.getHints(game1, 5);
        // 다른 게임은 쿨다운에 걸리지 않아야 한다
        svc.getHints(game2, 5);

        // game1 재호출은 여전히 쿨다운
        assertThatThrownBy(() -> svc.getHints(game1, 5))
                .isInstanceOf(HintCooldownException.class);
    }

    @Test
    void retryAfterSecIsAtLeastOne() {
        KataGoEngine engine = mock(KataGoEngine.class);
        when(engine.isAvailable()).thenReturn(true);
        when(engine.getHints(ArgumentMatchers.any(), ArgumentMatchers.anyInt(),
                ArgumentMatchers.any(), ArgumentMatchers.anyInt()))
                .thenReturn(List.of());

        GameService svc = newService(engine, 5000);
        String gameId = newGameId(svc);

        svc.getHints(gameId, 5);

        try {
            svc.getHints(gameId, 5);
        } catch (HintCooldownException e) {
            assertThat(e.getRetryAfterSec()).isGreaterThanOrEqualTo(1);
            return;
        }
        org.junit.jupiter.api.Assertions.fail("expected HintCooldownException");
    }

    // ── helpers ──

    private static GameService newService(KataGoEngine engine, long cooldownMs) {
        SessionProperties sessionProps = new SessionProperties();
        EngineProperties engineProps = new EngineProperties();
        engineProps.setHintCooldownMs(cooldownMs);
        return new GameService(engine, sessionProps, engineProps, Ticker.systemTicker());
    }

    private static String newGameId(GameService svc) {
        NewGameRequest req = new NewGameRequest();
        req.boardSize = 9;
        req.playerColor = "BLACK";
        req.difficulty = "HARD";
        return svc.newGame(req).gameId;
    }
}
