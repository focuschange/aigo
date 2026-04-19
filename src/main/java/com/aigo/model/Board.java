package com.aigo.model;

import java.util.*;

public class Board {
    private final int size;
    private final Stone[][] grid;

    public Board(int size) {
        this.size = size;
        this.grid = new Stone[size][size];
        for (Stone[] row : grid) Arrays.fill(row, Stone.EMPTY);
    }

    private Board(Board other) {
        this.size = other.size;
        this.grid = new Stone[size][size];
        for (int i = 0; i < size; i++) {
            this.grid[i] = Arrays.copyOf(other.grid[i], size);
        }
    }

    public Board copy() { return new Board(this); }
    public int getSize() { return size; }

    public Stone getStone(int row, int col) {
        return grid[row][col];
    }

    public void setStone(int row, int col, Stone stone) {
        grid[row][col] = stone;
    }

    public List<Point> getAdjacent(int row, int col) {
        List<Point> adj = new ArrayList<>(4);
        if (row > 0)        adj.add(new Point(row - 1, col));
        if (row < size - 1) adj.add(new Point(row + 1, col));
        if (col > 0)        adj.add(new Point(row, col - 1));
        if (col < size - 1) adj.add(new Point(row, col + 1));
        return adj;
    }

    public Set<Point> getGroup(int row, int col) {
        Stone color = grid[row][col];
        if (color == Stone.EMPTY) return Collections.emptySet();

        Set<Point> group = new HashSet<>();
        Queue<Point> queue = new ArrayDeque<>();
        Point start = new Point(row, col);
        queue.add(start);
        group.add(start);

        while (!queue.isEmpty()) {
            Point p = queue.poll();
            for (Point adj : getAdjacent(p.row, p.col)) {
                if (!group.contains(adj) && grid[adj.row][adj.col] == color) {
                    group.add(adj);
                    queue.add(adj);
                }
            }
        }
        return group;
    }

    public Set<Point> getLiberties(Set<Point> group) {
        Set<Point> libs = new HashSet<>();
        for (Point p : group) {
            for (Point adj : getAdjacent(p.row, p.col)) {
                if (grid[adj.row][adj.col] == Stone.EMPTY) libs.add(adj);
            }
        }
        return libs;
    }

    /** Place stone, capture opponent groups with no liberties. Returns captured count. */
    public int placeStone(int row, int col, Stone stone) {
        grid[row][col] = stone;
        int captured = 0;
        Stone opp = stone.opposite();
        for (Point adj : getAdjacent(row, col)) {
            if (grid[adj.row][adj.col] == opp) {
                Set<Point> grp = getGroup(adj.row, adj.col);
                if (getLiberties(grp).isEmpty()) {
                    captured += grp.size();
                    for (Point p : grp) grid[p.row][p.col] = Stone.EMPTY;
                }
            }
        }
        return captured;
    }

    public boolean isValidMove(int row, int col, Stone stone, Point koPoint) {
        if (row < 0 || row >= size || col < 0 || col >= size) return false;
        if (grid[row][col] != Stone.EMPTY) return false;
        if (koPoint != null && koPoint.row == row && koPoint.col == col) return false;

        // Check suicide / capture
        Stone opp = stone.opposite();
        // Temporarily place
        grid[row][col] = stone;
        boolean ok = false;
        // Has liberty directly?
        for (Point adj : getAdjacent(row, col)) {
            if (grid[adj.row][adj.col] == Stone.EMPTY) { ok = true; break; }
        }
        if (!ok) {
            // Captures opponent?
            for (Point adj : getAdjacent(row, col)) {
                if (grid[adj.row][adj.col] == opp) {
                    Set<Point> grp = getGroup(adj.row, adj.col);
                    if (getLiberties(grp).isEmpty()) { ok = true; break; }
                }
            }
        }
        if (!ok) {
            // Extends own group with liberties?
            Set<Point> grp = getGroup(row, col);
            if (!getLiberties(grp).isEmpty()) ok = true;
        }
        grid[row][col] = Stone.EMPTY;
        return ok;
    }

    public List<Point> getValidMoves(Stone stone, Point koPoint) {
        List<Point> moves = new ArrayList<>();
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (isValidMove(r, c, stone, koPoint)) moves.add(new Point(r, c));
            }
        }
        return moves;
    }

    /**
     * 한국식 계가 (Korean / Japanese rules):
     *   흑 점수 = 흑 영역(빈집) + 백 잡은 돌
     *   백 점수 = 백 영역(빈집) + 흑 잡은 돌 + 코미
     *
     * @param komi           코미 (백에게 주는 덤, 통상 6.5집)
     * @param capturedByBlack 흑이 잡은 백돌 수
     * @param capturedByWhite 백이 잡은 흑돌 수
     * @return [blackScore, whiteScore]
     */
    public double[] score(double komi, int capturedByBlack, int capturedByWhite) {
        boolean[][] visited = new boolean[size][size];
        // 시작점: 잡은 돌 (포로) + 코미
        double black = capturedByBlack;
        double white = capturedByWhite + komi;

        // 빈집(영역) 계산 – flood fill
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (grid[i][j] == Stone.EMPTY && !visited[i][j]) {
                    Set<Point> region = new HashSet<>();
                    Queue<Point> q = new ArrayDeque<>();
                    q.add(new Point(i, j));
                    region.add(new Point(i, j));
                    boolean touchBlack = false, touchWhite = false;

                    while (!q.isEmpty()) {
                        Point p = q.poll();
                        visited[p.row][p.col] = true;
                        for (Point adj : getAdjacent(p.row, p.col)) {
                            Stone s = grid[adj.row][adj.col];
                            if (s == Stone.EMPTY && !region.contains(adj)) {
                                region.add(adj); q.add(adj);
                            } else if (s == Stone.BLACK) touchBlack = true;
                            else if (s == Stone.WHITE) touchWhite = true;
                        }
                    }

                    // 한 색깔 돌에만 둘러싸인 빈집만 집으로 인정
                    if (touchBlack && !touchWhite) black += region.size();
                    else if (touchWhite && !touchBlack) white += region.size();
                    // 양쪽에 인접한 빈점(dame)은 점수 없음
                }
            }
        }
        return new double[]{black, white};
    }

    public String getHash() {
        StringBuilder sb = new StringBuilder(size * size);
        for (int i = 0; i < size; i++)
            for (int j = 0; j < size; j++)
                sb.append(grid[i][j].toInt());
        return sb.toString();
    }

    public Stone[][] getGrid() { return grid; }
}
