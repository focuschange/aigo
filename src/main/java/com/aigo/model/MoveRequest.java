package com.aigo.model;

import jakarta.validation.constraints.Min;

/**
 * 착수 요청 DTO.
 *
 * <p>상한은 보드 크기에 따라 달라지므로 여기서는 음수만 차단하고,
 * 상한 체크는 {@link com.aigo.model.Board#isValidMove} 에서 수행한다.
 */
public class MoveRequest {

    @Min(value = 0, message = "invalid row")
    public int row;

    @Min(value = 0, message = "invalid col")
    public int col;
}
