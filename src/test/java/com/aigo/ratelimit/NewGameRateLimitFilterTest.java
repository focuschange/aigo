package com.aigo.ratelimit;

import com.aigo.config.RateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link NewGameRateLimitFilter} 단위 테스트.
 */
class NewGameRateLimitFilterTest {

    private RateLimitProperties props;
    private NewGameRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        props = new RateLimitProperties();
        props.setEnabled(true);
        props.setNewGamePerMinute(3);
        props.setBucketCapacity(3);
        props.setMaxTrackedIps(10_000);
        filter = new NewGameRateLimitFilter(props);
    }

    @Test
    void allowsRequestsWithinCapacity_thenReturns429() throws Exception {
        // 같은 IP 에서 capacity(3) 만큼 요청 → 통과, 4번째부터 429
        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse resp = invoke("1.1.1.1");
            assertThat(resp.getStatus()).as("요청 %d 은 통과해야 함", i + 1).isEqualTo(200);
        }
        MockHttpServletResponse denied = invoke("1.1.1.1");
        assertThat(denied.getStatus()).isEqualTo(429);
        assertThat(denied.getHeader("Retry-After"))
                .as("429 응답에는 Retry-After 헤더가 있어야 함")
                .isNotNull();
    }

    @Test
    void differentIpsHaveIndependentBuckets() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(invoke("1.1.1.1").getStatus()).isEqualTo(200);
        }
        // 다른 IP 는 여전히 가용
        MockHttpServletResponse resp = invoke("2.2.2.2");
        assertThat(resp.getStatus()).isEqualTo(200);
    }

    @Test
    void nonTargetPath_bypassesFilterEntirely() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/game/abc/move");
        req.setRemoteAddr("1.1.1.1");
        CountingFilterChain chain = new CountingFilterChain();

        for (int i = 0; i < 50; i++) {
            MockHttpServletResponse resp = new MockHttpServletResponse();
            filter.doFilter(req, resp, chain);
            assertThat(resp.getStatus()).isEqualTo(200);
        }
        assertThat(chain.count).isEqualTo(50);
        // 추적 IP 수가 증가하지 않아야 함 (버킷 생성 자체가 없어야 함)
        assertThat(filter.trackedIpCount()).isZero();
    }

    @Test
    void getMethodOnTargetPath_isNotLimited() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/game/new");
        req.setRemoteAddr("1.1.1.1");
        CountingFilterChain chain = new CountingFilterChain();

        for (int i = 0; i < 10; i++) {
            MockHttpServletResponse resp = new MockHttpServletResponse();
            filter.doFilter(req, resp, chain);
            assertThat(resp.getStatus()).isEqualTo(200);
        }
        assertThat(chain.count).isEqualTo(10);
    }

    @Test
    void disabledFilter_allowsUnlimitedRequests() throws Exception {
        props.setEnabled(false);
        for (int i = 0; i < 100; i++) {
            assertThat(invoke("1.1.1.1").getStatus()).isEqualTo(200);
        }
    }

    @Test
    void xForwardedFor_usedForClientIpResolution() throws Exception {
        // 동일 remoteAddr 이라도 X-Forwarded-For 가 다르면 별도 버킷
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest req = buildRequest();
            req.setRemoteAddr("10.0.0.1"); // 프록시
            req.addHeader("X-Forwarded-For", "203.0.113.1, 10.0.0.1");
            MockHttpServletResponse resp = new MockHttpServletResponse();
            filter.doFilter(req, resp, new CountingFilterChain());
            assertThat(resp.getStatus()).isEqualTo(200);
        }
        // 네 번째는 같은 실 IP (X-Forwarded-For 첫 토큰) 로 차단
        MockHttpServletRequest req = buildRequest();
        req.setRemoteAddr("10.0.0.1");
        req.addHeader("X-Forwarded-For", "203.0.113.1");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(req, resp, new CountingFilterChain());
        assertThat(resp.getStatus()).isEqualTo(429);
    }

    // ── helpers ──

    private MockHttpServletResponse invoke(String remoteIp) throws ServletException, IOException {
        MockHttpServletRequest req = buildRequest();
        req.setRemoteAddr(remoteIp);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(req, resp, new CountingFilterChain());
        return resp;
    }

    private MockHttpServletRequest buildRequest() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/game/new");
        req.setContentType("application/json");
        return req;
    }

    /** 필터 체인 통과 횟수를 세는 간단한 구현. */
    private static final class CountingFilterChain implements FilterChain {
        int count = 0;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request,
                             jakarta.servlet.ServletResponse response) {
            count++;
            // 통과한 경우 200으로 간주
            if (response instanceof MockHttpServletResponse mock && mock.getStatus() == 0) {
                mock.setStatus(200);
            }
        }
    }
}
