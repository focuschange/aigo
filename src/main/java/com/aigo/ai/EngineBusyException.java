package com.aigo.ai;

/**
 * KataGo 락 획득 타임아웃 시 발생.
 *
 * <p>컨트롤러에서 {@code 503 Service Unavailable} + {@code Retry-After} 응답으로 매핑된다.
 */
public class EngineBusyException extends RuntimeException {

    private final long retryAfterSec;

    public EngineBusyException(long retryAfterSec) {
        super("AI engine is busy, please retry in " + retryAfterSec + "s");
        this.retryAfterSec = retryAfterSec;
    }

    public long getRetryAfterSec() {
        return retryAfterSec;
    }
}
