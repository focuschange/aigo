package com.aigo.service;

import com.aigo.model.Game;

/**
 * 단일 게임 세션 엔트리.
 *
 * Game 상태와 플레이어 설정(색상·난이도)을 하나의 불변 키로 묶어
 * Caffeine 캐시의 evict 시 부분 상태가 남지 않도록 한다.
 *
 * Game 인스턴스 자체는 내부에서 가변이지만, 세션 엔트리 교체는
 * {@code cache.asMap().compute()}로 원자적으로 수행한다.
 */
public record GameSession(Game game, String playerColor, String difficulty) {
}
