package com.aigo.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * CSRF 보호 + 기본 보안 헤더만 활성화한 Security 설정.
 *
 * <p>인증/인가는 아직 도입하지 않으므로 모든 요청을 permitAll 로 열어둔다.
 * 목적은 두 가지:
 * <ol>
 *   <li>상태 변경 요청(POST/PUT/DELETE)을 CSRF 토큰(double-submit cookie)으로 보호</li>
 *   <li>Spring Security 가 기본 제공하는 X-Content-Type-Options / X-Frame-Options / Cache-Control
 *       같은 응답 헤더를 자동 적용</li>
 * </ol>
 *
 * <h3>CSRF 토큰 전달 방식 (Spring Security 6)</h3>
 * <ul>
 *   <li>{@link CookieCsrfTokenRepository#withHttpOnlyFalse()} — {@code XSRF-TOKEN} 쿠키를 JS 가 읽을 수 있게 발급</li>
 *   <li>클라이언트는 POST/PUT/DELETE 시 동일한 값을 {@code X-XSRF-TOKEN} 헤더로 재전송</li>
 *   <li>Spring Security 6 은 BREACH 공격 완화를 위해 요청마다 토큰을 XOR 로 마스킹하므로,
 *       쿠키 값과 헤더 값이 매 요청마다 달라져도 서버가 검증 가능</li>
 * </ul>
 *
 * <p>Spring Security 6 의 CSRF 쿠키는 기본적으로 <em>lazy</em> 발급(실제 토큰을 요청 처리 중에 사용해야 쿠키가 내려감)
 * 되어 SPA 의 첫 POST 전에 쿠키가 비어 있는 상황이 생긴다.
 * 이를 회피하기 위해 모든 요청에서 토큰을 미리 resolve 하는 {@link OncePerRequestFilter}
 * (BREACH-safe 패턴, 공식 마이그레이션 가이드) 를 함께 등록한다.
 *
 * <p>세션 정책은 STATELESS — 서버측 HttpSession 을 만들지 않는다. 게임 세션은 Caffeine 캐시(GameService)
 * 가 별도로 관리한다.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository tokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        // 쿠키 경로를 명시적으로 "/" 로 고정해 모든 엔드포인트에서 공유
        tokenRepository.setCookiePath("/");

        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();
        // Spring Security 6 기본: BREACH-safe (XOR 마스킹). 명시 지정 없이도 동일.

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(tokenRepository)
                        .csrfTokenRequestHandler(csrfRequestHandler))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable())
                // CSRF 쿠키 강제 발급(lazy → eager): 첫 GET 요청에서도 토큰 쿠키를 내려준다.
                .addFilterAfter(csrfCookieMaterializingFilter(), org.springframework.security.web.csrf.CsrfFilter.class);

        return http.build();
    }

    /**
     * Spring Security 6 에서 CSRF 쿠키는 실제 토큰 값을 읽어야(resolve) 응답에 설정된다.
     * SPA 가 첫 상호작용에서 바로 POST 를 날리는 경우를 대비해, 모든 요청에서 토큰을
     * 명시적으로 resolve 하여 쿠키가 내려가도록 강제한다.
     *
     * <p>참고: <a href="https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html#csrf-integration-javascript-spa">
     * Spring Security CSRF SPA 통합 문서</a>
     */
    @Bean
    public OncePerRequestFilter csrfCookieMaterializingFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain filterChain) throws ServletException, IOException {
                CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
                if (token != null) {
                    // getToken() 호출로 lazy 발급 트리거 → CookieCsrfTokenRepository 가 쿠키 write
                    token.getToken();
                }
                filterChain.doFilter(request, response);
            }
        };
    }
}
