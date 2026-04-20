package com.aigo.controller;

import com.aigo.ai.KataGoEngine.HintMove;
import com.aigo.model.GameState;
import com.aigo.model.MoveRequest;
import com.aigo.model.NewGameRequest;
import com.aigo.service.GameService;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 프론트엔드는 동일 오리진(Spring Boot + Vite 빌드 산출물)에서 서빙되므로
 * CORS 허용을 두지 않는다. 개발 환경은 Vite dev server 의 /api 프록시로 처리.
 *
 * <p>예외 → HTTP 매핑은 {@link GlobalExceptionHandler} 에 집중되어 있다.
 */
@RestController
@RequestMapping("/api/game")
public class GameController {

    private final GameService gameService;

    @Autowired
    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    /** Create a new game. */
    @PostMapping("/new")
    public ResponseEntity<GameState> newGame(@Valid @RequestBody NewGameRequest req) {
        return ResponseEntity.ok(gameService.newGame(req));
    }

    /** Player makes a move (AI responds automatically). */
    @PostMapping("/{gameId}/move")
    public ResponseEntity<GameState> move(
            @PathVariable String gameId,
            @Valid @RequestBody MoveRequest req) {
        return ResponseEntity.ok(gameService.playerMove(gameId, req.row, req.col));
    }

    /** Player passes (AI responds automatically). */
    @PostMapping("/{gameId}/pass")
    public ResponseEntity<GameState> pass(@PathVariable String gameId) {
        return ResponseEntity.ok(gameService.playerPass(gameId));
    }

    /** Player resigns. */
    @PostMapping("/{gameId}/resign")
    public ResponseEntity<GameState> resign(@PathVariable String gameId) {
        return ResponseEntity.ok(gameService.playerResign(gameId));
    }

    /** Get current game state. */
    @GetMapping("/{gameId}")
    public ResponseEntity<GameState> state(@PathVariable String gameId) {
        return ResponseEntity.ok(gameService.getState(gameId));
    }

    /** Get hint moves (up to 5 recommended moves with win rates). */
    @GetMapping("/{gameId}/hints")
    public ResponseEntity<List<HintMove>> hints(@PathVariable String gameId) {
        return ResponseEntity.ok(gameService.getHints(gameId, 5));
    }
}
