package com.aigo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * /api/game/new 엔드포인트 레이트 리밋 설정 (IP 단위 토큰 버킷).
 *
 * - {@code enabled}: 필터 활성화 여부 (테스트에서 끌 수 있음).
 * - {@code newGamePerMinute}: 1분당 허용 토큰 수.
 * - {@code bucketCapacity}: 버킷 용량(초기 번스트 허용치). 미지정 시 분당 값과 동일.
 * - {@code maxTrackedIps}: 내부 맵이 추적하는 최대 IP 수 (메모리 보호).
 */
@ConfigurationProperties(prefix = "game.ratelimit")
public class RateLimitProperties {

    private boolean enabled = true;
    private int newGamePerMinute = 10;
    private int bucketCapacity = 0; // 0 이면 newGamePerMinute 과 동일
    private int maxTrackedIps = 100_000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getNewGamePerMinute() {
        return newGamePerMinute;
    }

    public void setNewGamePerMinute(int newGamePerMinute) {
        this.newGamePerMinute = newGamePerMinute;
    }

    public int getBucketCapacity() {
        return bucketCapacity > 0 ? bucketCapacity : newGamePerMinute;
    }

    public void setBucketCapacity(int bucketCapacity) {
        this.bucketCapacity = bucketCapacity;
    }

    public int getMaxTrackedIps() {
        return maxTrackedIps;
    }

    public void setMaxTrackedIps(int maxTrackedIps) {
        this.maxTrackedIps = maxTrackedIps;
    }
}
