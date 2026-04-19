package com.aigo.model;

/** Immutable record of a single move in the game history. */
public class MoveRecord {
    public final Stone color;
    public final int row;    // -1 = pass
    public final int col;    // -1 = pass
    public final boolean pass;

    private MoveRecord(Stone color, int row, int col, boolean pass) {
        this.color = color;
        this.row = row;
        this.col = col;
        this.pass = pass;
    }

    public static MoveRecord of(Stone color, int row, int col) {
        return new MoveRecord(color, row, col, false);
    }

    public static MoveRecord pass(Stone color) {
        return new MoveRecord(color, -1, -1, true);
    }
}
