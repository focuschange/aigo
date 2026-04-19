package com.aigo.service;

import com.aigo.ai.KataGoEngine;
import com.aigo.ai.KataGoEngine.HintMove;
import com.aigo.ai.KataGoEngine.MoveResult;
import com.aigo.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GameService {

    private static final Logger log = LoggerFactory.getLogger(GameService.class);

    private final KataGoEngine kataGo;
    private final Map<String, Game> games = new ConcurrentHashMap<>();
    private final Map<String, String> playerColors = new ConcurrentHashMap<>();
    private final Map<String, String> difficulties  = new ConcurrentHashMap<>();

    @Autowired
    public GameService(KataGoEngine kataGo) {
        this.kataGo = kataGo;
    }

    /** Create a new game. If the human plays WHITE, AI plays first. */
    public GameState newGame(NewGameRequest req) {
        int size = req.boardSize;
        if (size != 9 && size != 13 && size != 19) size = 19;

        Game game = new Game(size);
        String gameId = game.getGameId();
        String playerColor = req.playerColor.toUpperCase();
        String difficulty  = req.difficulty.toUpperCase();

        games.put(gameId, game);
        playerColors.put(gameId, playerColor);
        difficulties.put(gameId, difficulty);

        // If human plays white, AI (black) moves first
        double blackWinRate = -1;
        if ("WHITE".equals(playerColor)) {
            var result = applyAiMove(game, difficulty);
            blackWinRate = result.blackWinRate();
        }

        GameState state = GameState.from(game, playerColor, difficulty);
        state.gameId = gameId;
        state.blackWinRate = blackWinRate;
        return state;
    }

    /** Human makes a move, then AI responds. */
    public GameState playerMove(String gameId, int row, int col) {
        Game game = getGame(gameId);
        String playerColor = playerColors.get(gameId);
        String difficulty  = difficulties.get(gameId);

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

        GameState state = GameState.from(game, playerColor, difficulty);
        state.gameId      = gameId;
        state.lastMove    = playerMove;   // 플레이어 착점
        state.aiLastMove  = aiMove;       // AI 착점
        state.blackWinRate = blackWinRate;
        return state;
    }

    /** Human passes, then AI responds. */
    public GameState playerPass(String gameId) {
        Game game = getGame(gameId);
        String playerColor = playerColors.get(gameId);
        String difficulty  = difficulties.get(gameId);

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

        GameState state = GameState.from(game, playerColor, difficulty);
        state.gameId     = gameId;
        state.aiLastMove = aiMove;  // AI 착점 (플레이어는 패스)
        state.blackWinRate = blackWinRate;
        return state;
    }

    /** Human resigns. */
    public GameState playerResign(String gameId) {
        Game game = getGame(gameId);
        String playerColor = playerColors.get(gameId);
        String difficulty  = difficulties.get(gameId);
        game.resign();
        GameState state = GameState.from(game, playerColor, difficulty);
        state.gameId = gameId;
        state.message = "기권했습니다.";
        return state;
    }

    public GameState getState(String gameId) {
        Game game = getGame(gameId);
        String playerColor = playerColors.getOrDefault(gameId, "BLACK");
        String difficulty  = difficulties.getOrDefault(gameId, "HARD");
        GameState state = GameState.from(game, playerColor, difficulty);
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
        Game game = getGame(gameId);
        if (game.isGameOver()) return List.of();
        return kataGo.getHints(
                game.getMoveHistory(),
                game.getBoardSize(),
                game.getCurrentPlayer(),
                maxMoves);
    }

    private Game getGame(String gameId) {
        Game game = games.get(gameId);
        if (game == null) throw new IllegalArgumentException("Game not found: " + gameId);
        return game;
    }

    private GameState error(Game game, String playerColor, String difficulty, String msg) {
        GameState s = GameState.from(game, playerColor, difficulty);
        s.message = msg;
        return s;
    }
}
