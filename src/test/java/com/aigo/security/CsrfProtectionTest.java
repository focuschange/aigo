package com.aigo.security;

import com.aigo.ai.KataGoEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.servlet.http.Cookie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CSRF 보호가 실제로 동작하는지 통합 레벨에서 검증한다.
 *
 * <p>검증 항목:
 * <ol>
 *   <li>GET 응답에 XSRF-TOKEN 쿠키가 포함된다 (CookieCsrfTokenRepository + materializing filter)</li>
 *   <li>CSRF 토큰 없이 상태 변경 POST 를 보내면 403 으로 거부된다</li>
 *   <li>쿠키 + 동일 값 X-XSRF-TOKEN 헤더를 함께 보내면 CSRF 는 통과한다
 *       (후속 검증은 @Valid / 비즈니스 로직이 담당)</li>
 *   <li>GET 은 CSRF 토큰이 없어도 정상 동작한다</li>
 * </ol>
 *
 * <p>KataGo 서브프로세스 실행을 피하기 위해 KataGoEngine 은 @MockBean 으로 대체한다.
 * 레이트 리밋 필터는 CSRF 검증과 무관하므로 별도 비활성화하지 않고, 테스트마다 IP 를 분리한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        // 실제 KataGo 경로를 사용하지 않도록 더미 값으로 오버라이드 (모킹되므로 실행 안 됨)
        "katago.executable=/tmp/dummy",
        "katago.model=/tmp/dummy",
        "katago.config=/tmp/dummy",
        // 테스트에서는 레이트 리밋 영향 배제
        "game.ratelimit.enabled=false"
})
class CsrfProtectionTest {

    @Autowired MockMvc mvc;

    @MockBean KataGoEngine kataGoEngine;

    @Test
    void getResponse_setsXsrfTokenCookie() throws Exception {
        mvc.perform(get("/api/csrf"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }

    @Test
    void postWithoutCsrfToken_isForbidden() throws Exception {
        mvc.perform(post("/api/game/new")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boardSize\":9,\"playerColor\":\"BLACK\",\"difficulty\":\"EASY\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void postWithCsrfToken_passesCsrfCheck() throws Exception {
        when(kataGoEngine.isAvailable()).thenReturn(false);

        // 1) 쿠키 프라이밍
        MvcResult primed = mvc.perform(get("/api/csrf"))
                .andExpect(status().isNoContent())
                .andReturn();
        Cookie xsrf = primed.getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrf).as("XSRF-TOKEN 쿠키가 발급되어야 함").isNotNull();

        // 2) 동일 토큰을 헤더와 쿠키로 함께 전송
        MockHttpServletResponse resp = mvc.perform(post("/api/game/new")
                        .cookie(xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boardSize\":9,\"playerColor\":\"BLACK\",\"difficulty\":\"EASY\"}"))
                .andReturn()
                .getResponse();

        // CSRF 관문을 통과했음을 확인: 403 이 아니면 된다 (200/201/5xx 등은 비즈니스 레이어 책임)
        assertThat(resp.getStatus())
                .as("CSRF 통과 후에는 403 이 아닌 응답이어야 함 (status=%s, body=%s)",
                        resp.getStatus(), resp.getContentAsString())
                .isNotEqualTo(403);
    }

    @Test
    void postWithMismatchedToken_isForbidden() throws Exception {
        MvcResult primed = mvc.perform(get("/api/csrf")).andReturn();
        Cookie xsrf = primed.getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrf).isNotNull();

        mvc.perform(post("/api/game/new")
                        .cookie(xsrf)
                        .header("X-XSRF-TOKEN", "bogus-value-that-does-not-match")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boardSize\":9,\"playerColor\":\"BLACK\",\"difficulty\":\"EASY\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_doesNotRequireCsrfToken() throws Exception {
        // 존재하지 않는 gameId 로 GET 요청 — CSRF 는 GET 에 요구되지 않는다.
        // 410 (Game not found) 혹은 기타 비 403 응답이면 CSRF 관문은 문제없음.
        MockHttpServletResponse resp = mvc.perform(get("/api/game/unknown-id"))
                .andReturn()
                .getResponse();

        assertThat(resp.getStatus())
                .as("GET 은 CSRF 검증 대상이 아니다")
                .isNotEqualTo(403);
    }
}
