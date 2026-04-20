package com.aigo.controller;

import com.aigo.ai.EngineBusyException;
import com.aigo.ai.HintCooldownException;
import com.aigo.config.SecurityConfig;
import com.aigo.service.GameService;
import com.aigo.ratelimit.NewGameRateLimitFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link GameController} / {@link GlobalExceptionHandler} 의 예외 → HTTP 응답 매핑 검증.
 *
 * NewGameRateLimitFilter 는 WebMvcTest 가 자동 로드하지만 해당 테스트와 무관하므로 빈으로 제외한다.
 */
@WebMvcTest(controllers = GameController.class,
        excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = NewGameRateLimitFilter.class))
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class GameControllerExceptionMappingTest {

    @Autowired MockMvc mvc;

    @MockBean GameService gameService;

    // ── 엔진/세션 예외 매핑 ──

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

    // ── 입력 검증 실패 (400) ──

    @Test
    void newGame_withInvalidPlayerColor_returns400() throws Exception {
        mvc.perform(post("/api/game/new").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boardSize\":9,\"playerColor\":\"PURPLE\",\"difficulty\":\"EASY\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void newGame_withInvalidDifficulty_returns400() throws Exception {
        mvc.perform(post("/api/game/new").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boardSize\":9,\"playerColor\":\"BLACK\",\"difficulty\":\"IMPOSSIBLE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void newGame_withOutOfRangeBoardSize_returns400() throws Exception {
        mvc.perform(post("/api/game/new").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boardSize\":25,\"playerColor\":\"BLACK\",\"difficulty\":\"EASY\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void move_withNegativeCoord_returns400() throws Exception {
        mvc.perform(post("/api/game/g1/move").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"row\":-1,\"col\":3}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedJson_returns400() throws Exception {
        mvc.perform(post("/api/game/new").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void emptyBody_returns400() throws Exception {
        mvc.perform(post("/api/game/new").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postWithoutCsrfToken_isForbidden() throws Exception {
        // SecurityConfig 가 적용된 상태에서 POST 는 CSRF 토큰이 없으면 403
        mvc.perform(post("/api/game/new")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boardSize\":9,\"playerColor\":\"BLACK\",\"difficulty\":\"EASY\"}"))
                .andExpect(status().isForbidden());
    }
}
