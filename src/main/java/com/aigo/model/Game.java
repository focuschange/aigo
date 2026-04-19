package com.aigo.model;

import java.util.*;

public class Game {
    private final String gameId;
    private final int boardSize;
    private Board board;
    private Stone currentPlayer;
    private int capturedByBlack;  // white stones captured by black
    private int capturedByWhite;  // black stones captured by white
    private final List<String> boardHistory;
    private final List<MoveRecord> moveHistory;
    private int consecutivePasses;
    private boolean gameOver;
    private Stone winner;
    private double blackScore;
    private double whiteScore;
    private Point koPoint;
    private int[] lastMove;    // null = pass
    private static final double KOMI = 6.5;

    public Game(int boardSize) {
        this.gameId = UUID.randomUUID().toString();
        this.boardSize = boardSize;
        this.board = new Board(boardSize);
        this.currentPlayer = Stone.BLACK;
        this.boardHistory = new ArrayList<>();
        this.moveHistory = new ArrayList<>();
        this.boardHistory.add(board.getHash());
    }

    public Game(Game other) {
        this.gameId = other.gameId;
        this.boardSize = other.boardSize;
        this.board = other.board.copy();
        this.currentPlayer = other.currentPlayer;
        this.capturedByBlack = other.capturedByBlack;
        this.capturedByWhite = other.capturedByWhite;
        this.boardHistory = new ArrayList<>(other.boardHistory);
        this.moveHistory = new ArrayList<>(other.moveHistory);
        this.consecutivePasses = other.consecutivePasses;
        this.gameOver = other.gameOver;
        this.winner = other.winner;
        this.blackScore = other.blackScore;
        this.whiteScore = other.whiteScore;
        this.koPoint = other.koPoint;
        this.lastMove = other.lastMove != null ? Arrays.copyOf(other.lastMove, 2) : null;
    }

    public boolean makeMove(int row, int col) {
        if (gameOver) return false;
        if (!board.isValidMove(row, col, currentPlayer, koPoint)) return false;

        // Superko check
        Board temp = board.copy();
        temp.placeStone(row, col, currentPlayer);
        String newHash = temp.getHash();
        if (boardHistory.contains(newHash)) return false;

        int captured = board.placeStone(row, col, currentPlayer);
        if (currentPlayer == Stone.BLACK) capturedByBlack += captured;
        else capturedByWhite += captured;

        // Ko detection
        koPoint = null;
        if (captured == 1) {
            Set<Point> grp = board.getGroup(row, col);
            Set<Point> libs = board.getLiberties(grp);
            if (grp.size() == 1 && libs.size() == 1) {
                koPoint = libs.iterator().next();
            }
        }

        boardHistory.add(board.getHash());
        moveHistory.add(MoveRecord.of(currentPlayer, row, col));
        lastMove = new int[]{row, col};
        consecutivePasses = 0;
        currentPlayer = currentPlayer.opposite();
        return true;
    }

    public boolean pass() {
        if (gameOver) return false;
        moveHistory.add(MoveRecord.pass(currentPlayer));
        lastMove = null;
        koPoint = null;
        currentPlayer = currentPlayer.opposite();
        consecutivePasses++;
        if (consecutivePasses >= 2) endGame();
        return true;
    }

    public void resign() {
        gameOver = true;
        winner = currentPlayer.opposite();
    }

    private void endGame() {
        gameOver = true;
        // 한국식 계가: 빈집 + 잡은 돌 + 코미
        double[] scores = board.score(KOMI, capturedByBlack, capturedByWhite);
        blackScore = scores[0];
        whiteScore = scores[1];
        winner = blackScore > whiteScore ? Stone.BLACK : Stone.WHITE;
    }

    // Getters
    public String getGameId() { return gameId; }
    public int getBoardSize() { return boardSize; }
    public Board getBoard() { return board; }
    public Stone getCurrentPlayer() { return currentPlayer; }
    public int getCapturedByBlack() { return capturedByBlack; }
    public int getCapturedByWhite() { return capturedByWhite; }
    public boolean isGameOver() { return gameOver; }
    public Stone getWinner() { return winner; }
    public double getBlackScore() { return blackScore; }
    public double getWhiteScore() { return whiteScore; }
    public Point getKoPoint() { return koPoint; }
    public int[] getLastMove() { return lastMove; }
    public List<MoveRecord> getMoveHistory() { return Collections.unmodifiableList(moveHistory); }
    public int getConsecutivePasses() { return consecutivePasses; }
    public Game copy() { return new Game(this); }
}
