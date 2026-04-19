package com.aigo.model;

public enum Stone {
    EMPTY, BLACK, WHITE;

    public Stone opposite() {
        return switch (this) {
            case BLACK -> WHITE;
            case WHITE -> BLACK;
            case EMPTY -> EMPTY;
        };
    }

    public int toInt() {
        return switch (this) {
            case EMPTY -> 0;
            case BLACK -> 1;
            case WHITE -> 2;
        };
    }

    public static Stone fromInt(int v) {
        return switch (v) {
            case 1 -> BLACK;
            case 2 -> WHITE;
            default -> EMPTY;
        };
    }

    public String toGTP() {
        return switch (this) {
            case BLACK -> "black";
            case WHITE -> "white";
            default -> "";
        };
    }
}
