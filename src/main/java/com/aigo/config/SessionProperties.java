package com.aigo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 게임 세션 캐시 설정.
 *
 * - {@code maxActive}: 최대 동시 게임 수. 초과 시 LRU evict.
 * - {@code ttlActiveMinutes}: 진행 중인 게임이 마지막 접근 후 유지되는 시간.
 * - {@code ttlEndedMinutes}: 종료된 게임(결과 조회용)이 유지되는 시간.
 */
@ConfigurationProperties(prefix = "game.session")
public class SessionProperties {

    private int maxActive = 5000;
    private long ttlActiveMinutes = 60;
    private long ttlEndedMinutes = 5;

    public int getMaxActive() {
        return maxActive;
    }

    public void setMaxActive(int maxActive) {
        this.maxActive = maxActive;
    }

    public long getTtlActiveMinutes() {
        return ttlActiveMinutes;
    }

    public void setTtlActiveMinutes(long ttlActiveMinutes) {
        this.ttlActiveMinutes = ttlActiveMinutes;
    }

    public long getTtlEndedMinutes() {
        return ttlEndedMinutes;
    }

    public void setTtlEndedMinutes(long ttlEndedMinutes) {
        this.ttlEndedMinutes = ttlEndedMinutes;
    }
}
