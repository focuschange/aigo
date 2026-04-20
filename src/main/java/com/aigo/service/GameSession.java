package com.aigo.service;

import com.aigo.model.Game;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 단일 게임 세션 엔트리.
 *
 * Game 상태와 플레이어 설정(색상·난이도)을 하나의 키로 묶어
 * Caffeine 캐시의 evict 시 부분 상태가 남지 않도록 한다.
 *
 * {@code lastHintAtNanos} 는 게임 단위 힌트 쿨다운을 위해 유지한다.
 * AtomicLong 참조는 record 필드로서 final 이지만 내부 값은 변경 가능하다.
 */
public record GameSession(
        Game game,
        String playerColor,
        String difficulty,
        AtomicLong lastHintAtNanos) {

    /** 새 세션 생성용 팩토리. lastHintAtNanos 는 0(쿨다운 없음) 으로 초기화. */
    public static GameSession create(Game game, String playerColor, String difficulty) {
        return new GameSession(game, playerColor, difficulty, new AtomicLong(0));
    }
}
