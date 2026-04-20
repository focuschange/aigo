package com.aigo.ai;

/**
 * 게임 단위 힌트 쿨다운 중 추가 호출 시 발생.
 *
 * <p>컨트롤러에서 {@code 429 Too Many Requests} + {@code Retry-After} 응답으로 매핑된다.
 */
public class HintCooldownException extends RuntimeException {

    private final long retryAfterSec;

    public HintCooldownException(long retryAfterSec) {
        super("Hint endpoint on cooldown, retry in " + retryAfterSec + "s");
        this.retryAfterSec = retryAfterSec;
    }

    public long getRetryAfterSec() {
        return retryAfterSec;
    }
}
