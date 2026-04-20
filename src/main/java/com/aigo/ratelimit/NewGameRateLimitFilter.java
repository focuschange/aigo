package com.aigo.ratelimit;

import com.aigo.config.RateLimitProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code POST /api/game/new} 엔드포인트에 IP 단위 레이트 리밋을 적용한다.
 *
 * <p>Bucket4j 토큰 버킷 알고리즘 기반:
 *   - capacity = {@link RateLimitProperties#getBucketCapacity()} (번스트 한도)
 *   - refill   = {@link RateLimitProperties#getNewGamePerMinute()} tokens / 1 minute (greedy refill)
 *
 * <p>IP 추적 맵은 {@link RateLimitProperties#getMaxTrackedIps()}를 초과하지 않도록 단순 보호한다.
 * 한도 초과 시 새 IP는 일시적으로 버킷을 얻지 못하고 통과(fail-open)되며, 이는 의도된 절충이다
 * (맵 폭주 방지가 우선, 실제 악성 IP 반복 요청은 이미 등록된 버킷으로 차단됨).
 *
 * <p>초과 시 응답:
 *   - Status: {@code 429 Too Many Requests}
 *   - Header: {@code Retry-After: <seconds>}
 *   - Body:   간단 메시지
 */
public class NewGameRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(NewGameRateLimitFilter.class);

    static final String TARGET_PATH = "/api/game/new";

    private final RateLimitProperties props;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public NewGameRateLimitFilter(RateLimitProperties props) {
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        if (!props.isEnabled()
                || !"POST".equalsIgnoreCase(request.getMethod())
                || !TARGET_PATH.equals(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        String ip = resolveClientIp(request);
        Bucket bucket = obtainBucket(ip);
        if (bucket == null) {
            // fail-open: 추적 맵 포화 상태
            chain.doFilter(request, response);
            return;
        }

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            chain.doFilter(request, response);
        } else {
            long retryAfterSec = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L);
            log.warn("Rate limit exceeded for new-game from IP={} (retry after {}s)", ip, retryAfterSec);
            response.setStatus(429); // Too Many Requests (Servlet 6 에서 상수 제거됨)
            response.setHeader("Retry-After", String.valueOf(retryAfterSec));
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write("요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    private Bucket obtainBucket(String ip) {
        Bucket existing = buckets.get(ip);
        if (existing != null) return existing;

        if (buckets.size() >= props.getMaxTrackedIps()) {
            log.warn("Rate-limit tracking map reached capacity ({}), skipping new IP {}",
                    props.getMaxTrackedIps(), ip);
            return null;
        }
        return buckets.computeIfAbsent(ip, this::newBucket);
    }

    private Bucket newBucket(String ip) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(props.getBucketCapacity())
                .refillGreedy(props.getNewGamePerMinute(), Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    /**
     * 프록시 뒤에서도 안전하게 IP를 도출한다.
     * X-Forwarded-For 가 있으면 맨 앞 토큰을 사용, 없으면 remoteAddr.
     */
    static String resolveClientIp(HttpServletRequest request) {
        String fwd = request.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            int comma = fwd.indexOf(',');
            return (comma > 0 ? fwd.substring(0, comma) : fwd).trim();
        }
        String remote = request.getRemoteAddr();
        return remote != null ? remote : "unknown";
    }

    /** 테스트 훅: 추적 중인 IP 수. */
    public int trackedIpCount() {
        return buckets.size();
    }
}
