// Canvas 상수. 기존 game.js 값 동일.

export const COL_LABELS = 'ABCDEFGHJKLMNOPQRST'; // no 'I'

export const HOSHI: Record<number, ReadonlyArray<readonly [number, number]>> = {
  9: [
    [2, 2],
    [2, 6],
    [4, 4],
    [6, 2],
    [6, 6],
  ],
  13: [
    [3, 3],
    [3, 9],
    [6, 6],
    [9, 3],
    [9, 9],
  ],
  19: [
    [3, 3],
    [3, 9],
    [3, 15],
    [9, 3],
    [9, 9],
    [9, 15],
    [15, 3],
    [15, 9],
    [15, 15],
  ],
};
