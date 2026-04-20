package com.aigo.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 새 게임 생성 요청 DTO.
 *
 * <p>bean validation 은 최소 방어선으로만 사용한다:
 *   - {@code boardSize} 는 9/13/19 중 하나만 유효하지만, 기존 UX 호환을 위해
 *     {@link com.aigo.service.GameService#newGame} 에서 비규격 값은 조용히 19 로 치환한다.
 *     따라서 여기서는 대략적 범위(9~19) 만 강제한다.
 *   - {@code playerColor} / {@code difficulty} 는 Pattern 으로 엄격히 제한한다.
 */
public class NewGameRequest {

    @Min(value = 9, message = "invalid boardSize")
    @Max(value = 19, message = "invalid boardSize")
    public int boardSize = 19;

    @NotBlank
    @Pattern(regexp = "^(BLACK|WHITE)$", message = "invalid playerColor")
    public String playerColor = "BLACK";

    @NotBlank
    @Pattern(regexp = "^(EASY|MEDIUM|HARD)$", message = "invalid difficulty")
    public String difficulty = "HARD";
}
