package com.aigo.service;

import com.aigo.ai.KataGoEngine;
import com.aigo.ai.KataGoEngine.HintMove;
import com.aigo.ai.KataGoEngine.MoveResult;
import com.aigo.config.SessionProperties;
import com.aigo.model.*;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.Ticker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class GameService {

    private static final Logger log = LoggerFactory.getLogger(GameService.class);

    private final KataGoEngine kataGo;
    private final SessionProperties sessionProps;

    /**
     * 게임 세션 캐시.
     *   - 최대 크기(LRU evict): sessionProps.maxActive
     *   - 진행 중: expireAfterAccess  = ttlActiveMinutes
     *   - 종료 후: expireAfterWrite   = ttlEndedMinutes
     *
     * Per-entry 만료는 Caffeine Expiry 로 구현한다.
     */
    private final Cache<String, GameSession> sessions;

    @Autowired
    public GameService(KataGoEngine kataGo, SessionProperties sessionProps) {
        this(kataGo, sessionProps, Ticker.systemTicker());
    }

    /** 테스트 훅: 가짜 Ticker 주입용. */
    GameService(KataGoEngine kataGo, SessionProperties sessionProps, Ticker ticker) {
        this.kataGo = kataGo;
        this.sessionProps = sessionProps;

        Duration activeTtl = Duration.ofMinutes(sessionProps.getTtlActiveMinutes());
        Duration endedTtl  = Duration.ofMinutes(sessionProps.getTtlEndedMinutes());

        this.sessions = Caffeine.newBuilder()
                .maximumSize(sessionProps.getMaxActive())
                .ticker(ticker)
                .expireAfter(new Expiry<String, GameSession>() {
                    @Override
                    public long expireAfterCreate(String key, GameSession session, long currentTime) {
                        return (session.game().isGameOver() ? endedTtl : activeTtl).toNanos();
                    }

                    @Override
                    public long expireAfterUpdate(String key, GameSession session, long currentTime, long currentDuration) {
                        return (session.game().isGameOver() ? endedTtl : activeTtl).toNanos();
                    }

                    @Override
                    public long expireAfterRead(String key, GameSession session, long currentTime, long currentDuration) {
                        // 종료 게임은 접근해도 TTL 연장 금지 (expireAfterWrite 효과)
                        if (session.game().isGameOver()) return currentDuration;
                        // 진행 중 게임은 접근 시 TTL 리셋 (expireAfterAccess 효과)
                        return activeTtl.toNanos();
                    }
                })
                .recordStats()
                .build();

        log.info("Game session cache initialized: maxActive={}, activeTtl={}m, endedTtl={}m",
                sessionProps.getMaxActive(),
                sessionProps.getTtlActiveMinutes(),
                sessionProps.getTtlEndedMinutes());
    }

    /** Create a new game. If the human plays WHITE, AI plays first. */
    public GameState newGame(NewGameRequest req) {
        int size = req.boardSize;
        if (size != 9 && size != 13 && size != 19) size = 19;

        Game game = new Game(size);
        String gameId = game.getGameId();
        String playerColor = req.playerColor == null ? "BLACK" : req.playerColor.toUpperCase();
        String difficulty  = req.difficulty  == null ? "HARD"  : req.difficulty.toUpperCase();

        sessions.put(gameId, new GameSession(game, playerColor, difficulty));

        // If human plays white, AI (black) moves first
        double blackWinRate = -1;
        if ("WHITE".equals(playerColor)) {
            var result = applyAiMove(game, difficulty);
            blackWinRate = result.blackWinRate();
            // AI가 둔 뒤 상태 변화 반영 (endGame 가능성은 낮지만 TTL 재평가 목적)
            refreshSession(gameId);
        }

        GameState state = GameState.from(game, playerColor, difficulty);
        state.gameId = gameId;
        state.blackWinRate = blackWinRate;
        return state;
    }

    /** Human makes a move, then AI responds. */
    public GameState playerMove(String gameId, int row, int col) {
        GameSession session = getSession(gameId);
        Game game = session.game();
        String playerColor = session.playerColor();
        String difficulty  = session.difficulty();

        if (game.isGameOver())
            return error(game, playerColor, difficulty, "게임이 이미 종료되었습니다.");

        if (!game.getCurrentPlayer().name().equals(playerColor))
            return error(game, playerColor, difficulty, "당신의 차례가 아닙니다.");

        if (!game.makeMove(row, col))
            return error(game, playerColor, difficulty, "유효하지 않은 수입니다.");

        int[] playerMove = new int[]{row, col};

        // AI responds
        int[] aiMove = null;
        double blackWinRate = -1;
        if (!game.isGameOver()) {
            var result = applyAiMove(game, difficulty);
            aiMove = result.move();
            blackWinRate = result.blackWinRate();
        } else {
            // 게임 종료 시에도 승률 조회
            blackWinRate = kataGo.queryBlackWinRate(game.getMoveHistory(), game.getBoardSize());
        }

        refreshSession(gameId);

        GameState state = GameState.from(game, playerColor, difficulty);
        state.gameId      = gameId;
        state.lastMove    = playerMove;   // 플레이어 착점
        state.aiLastMove  = aiMove;       // AI 착점
        state.blackWinRate = blackWinRate;
        return state;
    }

    /** Human passes, then AI responds. */
    public GameState playerPass(String gameId) {
        GameSession session = getSession(gameId);
        Game game = session.game();
        String playerColor = session.playerColor();
        String difficulty  = session.difficulty();

        if (game.isGameOver())
            return error(game, playerColor, difficulty, "게임이 이미 종료되었습니다.");

        game.pass();

        int[] aiMove = null;
        double blackWinRate = -1;
        if (!game.isGameOver()) {
            var result = applyAiMove(game, difficulty);
            aiMove = result.move();
            blackWinRate = result.blackWinRate();
        } else {
            blackWinRate = kataGo.queryBlackWinRate(game.getMoveHistory(), game.getBoardSize());
        }

        refreshSession(gameId);

        GameState state = GameState.from(game, playerColor, difficulty);
        state.gameId     = gameId;
        state.aiLastMove = aiMove;  // AI 착점 (플레이어는 패스)
        state.blackWinRate = blackWinRate;
        return state;
    }

    /** Human resigns. */
    public GameState playerResign(String gameId) {
        GameSession session = getSession(gameId);
        Game game = session.game();
        String playerColor = session.playerColor();
        String difficulty  = session.difficulty();

        game.resign();
        refreshSession(gameId);

        GameState state = GameState.from(game, playerColor, difficulty);
        state.gameId = gameId;
        state.message = "기권했습니다.";
        return state;
    }

    public GameState getState(String gameId) {
        GameSession session = getSession(gameId);
        GameState state = GameState.from(session.game(), session.playerColor(), session.difficulty());
        state.gameId = gameId;
        return state;
    }

    /**
     * KataGo가 수를 생성해 게임에 적용한다.
     * @return MoveResult (AI가 둔 좌표 + 흑 승률)
     */
    private MoveResult applyAiMove(Game game, String difficulty) {
        if (!kataGo.isAvailable()) {
            log.warn("KataGo not available – AI passes.");
            game.pass();
            return new MoveResult(null, -1);
        }
        MoveResult result = kataGo.generateMove(
                game.getMoveHistory(),
                game.getBoardSize(),
                game.getCurrentPlayer(),
                difficulty);

        if (result.move() == null) {
            log.info("KataGo chose to pass.");
            game.pass();
            return result;
        } else {
            log.info("KataGo plays [{}, {}]", result.move()[0], result.move()[1]);
            if (!game.makeMove(result.move()[0], result.move()[1])) {
                log.warn("KataGo returned invalid move – passing instead.");
                game.pass();
                return new MoveResult(null, result.blackWinRate());
            }
            return result;
        }
    }

    /** 힌트: 현재 포지션에서 최대 maxMoves개의 추천수를 반환한다. */
    public List<HintMove> getHints(String gameId, int maxMoves) {
        GameSession session = getSession(gameId);
        Game game = session.game();
        if (game.isGameOver()) return List.of();
        return kataGo.getHints(
                game.getMoveHistory(),
                game.getBoardSize(),
                game.getCurrentPlayer(),
                maxMoves);
    }

    /**
     * 현재 세션을 재삽입하여 Expiry 평가를 다시 수행한다.
     * (isGameOver 상태가 바뀌었을 수 있으므로 TTL 카테고리 갱신 목적)
     */
    private void refreshSession(String gameId) {
        sessions.asMap().computeIfPresent(gameId, (id, s) -> s);
    }

    private GameSession getSession(String gameId) {
        GameSession session = sessions.getIfPresent(gameId);
        if (session == null) throw new IllegalArgumentException("Game not found: " + gameId);
        return session;
    }

    /** 테스트 훅: 현재 보관 중인 세션 수를 반환. */
    public long activeSessionCount() {
        sessions.cleanUp();
        return sessions.estimatedSize();
    }

    /** 테스트 훅: 특정 세션이 아직 캐시에 남아있는지 조회. */
    public boolean hasSession(String gameId) {
        return sessions.getIfPresent(gameId) != null;
    }

    private GameState error(Game game, String playerColor, String difficulty, String msg) {
        GameState s = GameState.from(game, playerColor, difficulty);
        s.message = msg;
        return s;
    }
}
