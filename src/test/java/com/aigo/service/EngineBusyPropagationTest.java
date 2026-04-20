package com.aigo.service;

import com.aigo.ai.EngineBusyException;
import com.aigo.ai.KataGoEngine;
import com.aigo.config.EngineProperties;
import com.aigo.config.SessionProperties;
import com.aigo.model.NewGameRequest;
import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * KataGoEngine 이 {@link EngineBusyException} 을 던지면 GameService 가 이를
 * 포장·변환하지 않고 그대로 전파하는지 검증한다. (컨트롤러가 503 매핑을 수행)
 */
class EngineBusyPropagationTest {

    @Test
    void engineBusyException_propagatesThroughGetHints() {
        KataGoEngine engine = mock(KataGoEngine.class);
        when(engine.isAvailable()).thenReturn(true);
        when(engine.getHints(ArgumentMatchers.any(), ArgumentMatchers.anyInt(),
                ArgumentMatchers.any(), ArgumentMatchers.anyInt()))
                .thenThrow(new EngineBusyException(3));

        GameService svc = new GameService(engine, new SessionProperties(),
                new EngineProperties(), Ticker.systemTicker());

        NewGameRequest req = new NewGameRequest();
        req.boardSize = 9;
        req.playerColor = "BLACK";
        req.difficulty = "HARD";
        String gameId = svc.newGame(req).gameId;

        assertThatThrownBy(() -> svc.getHints(gameId, 5))
                .isInstanceOf(EngineBusyException.class);
    }
}
