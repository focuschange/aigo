package com.aigo.controller;

import com.aigo.ai.EngineBusyException;
import com.aigo.ai.HintCooldownException;
import com.aigo.service.GameService;
import com.aigo.ratelimit.NewGameRateLimitFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link GameController} 의 예외 → HTTP 응답 매핑 검증.
 *
 * NewGameRateLimitFilter 는 WebMvcTest 가 자동 로드하지만 해당 테스트와 무관하므로 빈으로 제외한다.
 */
@WebMvcTest(controllers = GameController.class,
        excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = NewGameRateLimitFilter.class))
class GameControllerExceptionMappingTest {

    @Autowired MockMvc mvc;

    @MockBean GameService gameService;

    @Test
    void hintCooldown_returns429WithRetryAfter() throws Exception {
        doThrow(new HintCooldownException(4)).when(gameService).getHints(anyString(), anyInt());

        mvc.perform(get("/api/game/g1/hints"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "4"));
    }

    @Test
    void engineBusy_returns503WithRetryAfter() throws Exception {
        doThrow(new EngineBusyException(3)).when(gameService).getHints(anyString(), anyInt());

        mvc.perform(get("/api/game/g1/hints"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "3"));
    }

    @Test
    void missingGame_returns410Gone() throws Exception {
        doThrow(new IllegalArgumentException("Game not found: g1"))
                .when(gameService).getHints(anyString(), anyInt());

        mvc.perform(get("/api/game/g1/hints"))
                .andExpect(status().isGone());
    }
}
