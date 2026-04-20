package com.aigo.controller;

import com.aigo.ai.EngineBusyException;
import com.aigo.ai.HintCooldownException;
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

/**
 * 프론트엔드는 동일 오리진(Spring Boot + Vite 빌드 산출물)에서 서빙되므로
 * CORS 허용을 두지 않는다. 개발 환경은 Vite dev server 의 /api 프록시로 처리.
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

    /**
     * 게임 단위 힌트 쿨다운 초과 → 429 + Retry-After.
     * 악성 사용자의 연속 힌트 호출로 KataGo 가 점유되는 것을 방지한다.
     */
    @ExceptionHandler(HintCooldownException.class)
    public ResponseEntity<String> handleHintCooldown(HintCooldownException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(e.getRetryAfterSec()))
                .body("힌트는 " + e.getRetryAfterSec() + "초 후 다시 사용할 수 있습니다.");
    }

    /**
     * KataGo 엔진 락 획득 타임아웃 → 503 + Retry-After.
     * Tomcat 요청 스레드가 무한 블로킹되는 것을 방지하기 위한 안전장치.
     */
    @ExceptionHandler(EngineBusyException.class)
    public ResponseEntity<String> handleEngineBusy(EngineBusyException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", String.valueOf(e.getRetryAfterSec()))
                .body("AI 엔진이 혼잡합니다. 잠시 후 다시 시도해 주세요.");
    }
}
