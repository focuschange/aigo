// Backend DTO 미러링. GameState.java 의 필드 집합을 그대로 반영한다.

export const STONE = { EMPTY: 0, BLACK: 1, WHITE: 2 } as const;
export type StoneValue = (typeof STONE)[keyof typeof STONE];

export type Stone = 'BLACK' | 'WHITE';
export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD';

export interface NewGameRequest {
  boardSize: number;
  playerColor: Stone;
  difficulty: Difficulty;
}

export interface MoveRequest {
  row: number;
  col: number;
}

export interface GameState {
  gameId: string;
  board: number[][]; // 0/1/2
  currentPlayer: Stone;
  capturedByBlack: number;
  capturedByWhite: number;
  gameOver: boolean;
  winner: Stone | null;
  blackScore: number;
  whiteScore: number;
  boardSize: number;
  lastMove: [number, number] | null;
  aiLastMove: [number, number] | null;
  message: string;
  playerColor: Stone;
  difficulty: Difficulty;
  aiThinking: boolean;
  blackWinRate: number; // -1 이면 정보 없음
}

export interface HintMove {
  row: number;
  col: number;
  winRate: number; // 흑 관점
  order: number;
}
