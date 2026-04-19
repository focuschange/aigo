package com.aigo.controller;

import com.aigo.ai.KataGoEngine.HintMove;
import com.aigo.model.GameState;
import com.aigo.model.MoveRequest;
import com.aigo.model.NewGameRequest;
import com.aigo.service.GameService;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/game")
@CrossOrigin(origins = "*")
public class GameController {

    private final GameService gameService;

    @Autowired
    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    /** Create a new game. */
    @PostMapping("/new")
    public ResponseEntity<GameState> newGame(@RequestBody NewGameRequest req) {
        return ResponseEntity.ok(gameService.newGame(req));
    }

    /** Player makes a move (AI responds automatically). */
    @PostMapping("/{gameId}/move")
    public ResponseEntity<GameState> move(
            @PathVariable String gameId,
            @RequestBody MoveRequest req) {
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

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleError(IllegalArgumentException e) {
        // 세션 만료/미존재를 410 Gone 으로 구분
        String msg = e.getMessage() == null ? "" : e.getMessage();
        if (msg.startsWith("Game not found")) {
            return ResponseEntity.status(HttpStatus.GONE)
                    .body("게임 세션이 만료되었거나 존재하지 않습니다. 새 게임을 시작해 주세요.");
        }
        // 그 외 잘못된 요청: 고정 문구로 응답하여 내부 정보 노출 방지
        return ResponseEntity.badRequest().body("잘못된 요청입니다.");
    }
}
