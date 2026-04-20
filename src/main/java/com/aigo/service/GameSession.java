package com.aigo.service;

import com.aigo.model.Game;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 단일 게임 세션 엔트리.
 *
 * <p>Game 상태와 플레이어 설정(색상·난이도), 힌트 쿨다운 타임스탬프, 그리고
 * 게임별 직렬화를 위한 ReentrantLock 을 하나의 키로 묶는다.
 *
 * <p>record 필드(참조)는 final 이지만 {@link AtomicLong} / {@link ReentrantLock}
 * 자체가 동시성 지원 자료구조이므로 안전하게 공유·변경될 수 있다.
 *
 * <p>락 사용 규약: 동일 gameId 에 대한 상태 변경 경로({@code playerMove},
 * {@code playerPass}, {@code playerResign} 등) 는 반드시 이 락을 획득한 상태에서
 * 수행해야 한다. 락 순서는 항상 {@code GameSession → KataGoEngine} 방향만 허용하여
 * 데드락을 원천 차단한다.
 */
public record GameSession(
        Game game,
        String playerColor,
        String difficulty,
        AtomicLong lastHintAtNanos,
        ReentrantLock lock) {

    /** 새 세션 생성용 팩토리. 쿨다운 0, 비공정 ReentrantLock 으로 초기화. */
    public static GameSession create(Game game, String playerColor, String difficulty) {
        return new GameSession(game, playerColor, difficulty, new AtomicLong(0), new ReentrantLock());
    }
}
