package com.aigo.controller;

import com.aigo.ai.EngineBusyException;
import com.aigo.ai.HintCooldownException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 전역 예외 → HTTP 응답 매핑.
 *
 * <p>입력 검증/세션/엔진 예외를 모두 한 곳에서 처리하여 컨트롤러 코드를 비즈니스 로직에 집중시킨다.
 * 응답 본문은 항상 고정 문구로 작성하여 내부 상세(스택/필드명 등)가 유출되지 않는다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** {@code @Valid} 검증 실패 → 400. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> handleValidation(MethodArgumentNotValidException e) {
        log.debug("Validation failed: {}", e.getMessage());
        return ResponseEntity.badRequest().body("잘못된 요청입니다.");
    }

    /** JSON 파싱 실패·비어 있는 body → 400. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<String> handleUnreadable(HttpMessageNotReadableException e) {
        log.debug("Request body not readable: {}", e.getMessage());
        return ResponseEntity.badRequest().body("잘못된 요청입니다.");
    }

    /**
     * 세션 미존재/만료는 410, 그 외 {@link IllegalArgumentException} 은 400 으로 매핑.
     * 사용자에게는 구체적인 메시지 대신 고정 문구를 반환한다.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        if (msg.startsWith("Game not found")) {
            return ResponseEntity.status(HttpStatus.GONE)
                    .body("게임 세션이 만료되었거나 존재하지 않습니다. 새 게임을 시작해 주세요.");
        }
        return ResponseEntity.badRequest().body("잘못된 요청입니다.");
    }

    /** 게임 단위 힌트 쿨다운 → 429 + Retry-After. */
    @ExceptionHandler(HintCooldownException.class)
    public ResponseEntity<String> handleHintCooldown(HintCooldownException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(e.getRetryAfterSec()))
                .body("힌트는 " + e.getRetryAfterSec() + "초 후 다시 사용할 수 있습니다.");
    }

    /** KataGo 락 획득 타임아웃 → 503 + Retry-After. */
    @ExceptionHandler(EngineBusyException.class)
    public ResponseEntity<String> handleEngineBusy(EngineBusyException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", String.valueOf(e.getRetryAfterSec()))
                .body("AI 엔진이 혼잡합니다. 잠시 후 다시 시도해 주세요.");
    }
}
