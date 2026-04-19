package com.aigo.model;

public class GameState {
    public String gameId;
    public int[][] board;         // 0=empty, 1=black, 2=white
    public String currentPlayer;  // "BLACK" or "WHITE"
    public int capturedByBlack;
    public int capturedByWhite;
    public boolean gameOver;
    public String winner;         // "BLACK", "WHITE", or null
    public double blackScore;
    public double whiteScore;
    public int boardSize;
    public int[] lastMove;        // [row, col] or null
    public int[] aiLastMove;      // [row, col] or null
    public String message;
    public String playerColor;    // human player's color
    public String difficulty;
    public boolean aiThinking;
    public double blackWinRate = -1;  // 0.0~1.0, -1이면 정보 없음

    public static GameState from(Game game, String playerColor, String difficulty) {
        GameState s = new GameState();
        s.gameId = game.getGameId();
        s.boardSize = game.getBoardSize();
        s.currentPlayer = game.getCurrentPlayer().name();
        s.capturedByBlack = game.getCapturedByBlack();
        s.capturedByWhite = game.getCapturedByWhite();
        s.gameOver = game.isGameOver();
        s.blackScore = game.getBlackScore();
        s.whiteScore = game.getWhiteScore();
        s.lastMove = game.getLastMove();
        s.playerColor = playerColor;
        s.difficulty = difficulty;

        if (game.isGameOver()) {
            s.winner = game.getWinner() == Stone.EMPTY ? null : game.getWinner().name();
            if (game.getWinner() != Stone.EMPTY) {
                // 한국식 계가 결과 메시지
                double diff = Math.abs(game.getBlackScore() - game.getWhiteScore());
                String winnerName = game.getWinner() == Stone.BLACK ? "흑" : "백";
                String scoreDetail = String.format("흑 %s집 / 백 %s집 (코미 6.5 포함)",
                    formatScore(game.getBlackScore()), formatScore(game.getWhiteScore()));
                if (game.getWinner().name().equals(playerColor)) {
                    s.message = "승리! " + winnerName + " " + formatScore(diff) + "집 승 ― " + scoreDetail;
                } else {
                    s.message = "패배. " + winnerName + " " + formatScore(diff) + "집 승 ― " + scoreDetail;
                }
            } else {
                s.message = "게임 종료";
            }
        } else {
            boolean myTurn = game.getCurrentPlayer().name().equals(playerColor);
            s.message = myTurn ? "당신의 차례" : "AI 생각 중...";
        }

        // Build board array
        Stone[][] grid = game.getBoard().getGrid();
        s.board = new int[game.getBoardSize()][game.getBoardSize()];
        for (int r = 0; r < game.getBoardSize(); r++)
            for (int c = 0; c < game.getBoardSize(); c++)
                s.board[r][c] = grid[r][c].toInt();

        return s;
    }

    /** 점수를 정수면 정수로, 반집이면 X.5 형식으로 표시 */
    private static String formatScore(double score) {
        if (score == Math.floor(score)) return String.valueOf((int) score);
        return String.format("%.1f", score);
    }
}
