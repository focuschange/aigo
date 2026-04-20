package com.aigo.config;

import com.aigo.ratelimit.NewGameRateLimitFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 레이트 리밋 필터 등록.
 *
 * URL 패턴은 {@code /api/game/new}로 한정하여 다른 엔드포인트(착수·패스·기권·힌트)에는
 * 오버헤드가 없도록 한다.
 */
@Configuration
public class RateLimitConfig {

    @Bean
    public NewGameRateLimitFilter newGameRateLimitFilter(RateLimitProperties props) {
        return new NewGameRateLimitFilter(props);
    }

    @Bean
    public FilterRegistrationBean<NewGameRateLimitFilter> newGameRateLimitFilterRegistration(
            NewGameRateLimitFilter filter) {

        FilterRegistrationBean<NewGameRateLimitFilter> reg = new FilterRegistrationBean<>(filter);
        reg.addUrlPatterns("/api/game/new");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        reg.setName("newGameRateLimitFilter");
        return reg;
    }
}
