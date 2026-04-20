package com.aigo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * KataGo 엔진 접근 제어 설정.
 *
 * - {@code lockTimeoutMs}: KataGo 락 획득에 대기할 최대 시간.
 *   이 시간을 초과하면 {@code EngineBusyException} 이 발생하고 호출자는 503 응답을 받는다.
 *   Tomcat 요청 스레드가 무한 블로킹되는 것을 방지한다.
 *
 * - {@code hintCooldownMs}: 게임 단위 {@code /hints} 엔드포인트 쿨다운.
 *   힌트 분석은 최대 2.5초를 소비하므로 남용 방지를 위해 게임별로 간격을 강제한다.
 *   이 시간이 지나기 전 재호출 시 {@code HintCooldownException} 이 발생하고 429 응답이 반환된다.
 */
@ConfigurationProperties(prefix = "game.engine")
public class EngineProperties {

    private long lockTimeoutMs = 3000;
    private long hintCooldownMs = 5000;

    public long getLockTimeoutMs() {
        return lockTimeoutMs;
    }

    public void setLockTimeoutMs(long lockTimeoutMs) {
        this.lockTimeoutMs = lockTimeoutMs;
    }

    public long getHintCooldownMs() {
        return hintCooldownMs;
    }

    public void setHintCooldownMs(long hintCooldownMs) {
        this.hintCooldownMs = hintCooldownMs;
    }
}
