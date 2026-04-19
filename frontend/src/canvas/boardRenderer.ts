// Canvas 렌더링 로직. 기존 game.js 의 render()/drawStone() 를 순수 함수로 포팅.
// React 에 직접 의존하지 않으며, props 객체만으로 완전한 프레임을 그린다.

import { HOSHI, COL_LABELS } from '../constants';
import { STONE, type StoneValue, type HintMove, type Stone } from '../types';

export interface BoardLayout {
  boardSize: number;
  cellSize: number;
  padding: number;
  boardPx: number; // CSS 픽셀 기준 한 변 길이
}

export function computeLayout(boardSize: number): BoardLayout {
  const avail = Math.min(window.innerWidth - 320, window.innerHeight - 100);
  const maxCell = Math.floor((avail - 80) / (boardSize - 1));
  const cellSize = Math.max(24, Math.min(52, maxCell));
  const padding = Math.round(cellSize * 1.2);
  const boardPx = (boardSize - 1) * cellSize + padding * 2;
  return { boardSize, cellSize, padding, boardPx };
}

/**
 * Canvas 엘리먼트를 주어진 레이아웃에 맞춰 크기 조정하고 DPR 스케일을 적용한다.
 * getContext 는 이전 transform 이 누적되지 않도록 매번 setTransform 으로 초기화한다.
 */
export function resizeCanvas(canvas: HTMLCanvasElement, layout: BoardLayout): void {
  const dpr = window.devicePixelRatio || 1;
  canvas.width = layout.boardPx * dpr;
  canvas.height = layout.boardPx * dpr;
  canvas.style.width = `${layout.boardPx}px`;
  canvas.style.height = `${layout.boardPx}px`;
  const ctx = canvas.getContext('2d');
  if (ctx) {
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  }
}

export function canvasToBoard(
  canvas: HTMLCanvasElement,
  clientX: number,
  clientY: number,
  layout: BoardLayout,
): [number, number] {
  const rect = canvas.getBoundingClientRect();
  const x = clientX - rect.left;
  const y = clientY - rect.top;
  const col = Math.round((x - layout.padding) / layout.cellSize);
  const row = Math.round((y - layout.padding) / layout.cellSize);
  return [row, col];
}

export interface BoardRenderProps {
  board: number[][];
  layout: BoardLayout;
  lastMove: [number, number] | null;
  aiLastMove: [number, number] | null;
  hoverCell: [number, number] | null; // -1,-1 → null 로 전달
  isMyTurn: boolean;
  playerColor: Stone;
  hints: HintMove[];
  showHints: boolean;
}

export function renderBoard(ctx: CanvasRenderingContext2D, props: BoardRenderProps): void {
  const { layout, board, lastMove, aiLastMove, hoverCell, isMyTurn, playerColor, hints, showHints } = props;
  const { boardSize: n, cellSize: cs, padding: pad, boardPx } = layout;

  // ── Background (wooden) ──
  const bgGrad = ctx.createLinearGradient(0, 0, boardPx, boardPx);
  bgGrad.addColorStop(0, '#e8c068');
  bgGrad.addColorStop(0.4, '#d4a840');
  bgGrad.addColorStop(1, '#b88820');
  ctx.fillStyle = bgGrad;
  ctx.fillRect(0, 0, boardPx, boardPx);

  // Wood grain
  ctx.save();
  ctx.globalAlpha = 0.04;
  ctx.strokeStyle = '#000';
  ctx.lineWidth = 0.8;
  for (let y = 0; y < boardPx; y += 4) {
    ctx.beginPath();
    ctx.moveTo(0, y + Math.sin(y * 0.08) * 1.5);
    ctx.lineTo(boardPx, y + Math.sin(y * 0.08 + 2) * 1.5);
    ctx.stroke();
  }
  ctx.restore();

  // Board border shadow
  ctx.save();
  ctx.shadowColor = 'rgba(0,0,0,0.5)';
  ctx.shadowBlur = 16;
  ctx.shadowOffsetX = 4;
  ctx.shadowOffsetY = 4;
  ctx.strokeStyle = '#8a6010';
  ctx.lineWidth = 2;
  ctx.strokeRect(pad, pad, (n - 1) * cs, (n - 1) * cs);
  ctx.restore();

  // ── Grid lines ──
  ctx.strokeStyle = 'rgba(90, 60, 10, 0.85)';
  ctx.lineWidth = 0.9;
  for (let i = 0; i < n; i++) {
    const x = pad + i * cs;
    const y = pad + i * cs;
    ctx.beginPath();
    ctx.moveTo(x, pad);
    ctx.lineTo(x, pad + (n - 1) * cs);
    ctx.stroke();
    ctx.beginPath();
    ctx.moveTo(pad, y);
    ctx.lineTo(pad + (n - 1) * cs, y);
    ctx.stroke();
  }

  // ── Star points ──
  const stars = HOSHI[n] || [];
  ctx.fillStyle = '#6a4808';
  for (const [r, c] of stars) {
    ctx.beginPath();
    ctx.arc(pad + c * cs, pad + r * cs, cs * 0.1, 0, Math.PI * 2);
    ctx.fill();
  }

  // ── Coordinates ──
  ctx.save();
  ctx.fillStyle = '#5d3a00';
  ctx.font = `bold ${Math.round(cs * 0.38)}px serif`;
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  for (let i = 0; i < n; i++) {
    const x = pad + i * cs;
    const y = pad + i * cs;
    const col = COL_LABELS[i];
    const row = String(n - i);
    ctx.fillText(col, x, pad / 2);
    ctx.fillText(col, x, boardPx - pad / 2);
    ctx.fillText(row, pad / 2, y);
    ctx.fillText(row, boardPx - pad / 2, y);
  }
  ctx.restore();

  // ── Stones ──
  for (let r = 0; r < n; r++) {
    for (let c = 0; c < n; c++) {
      const s = board[r]?.[c] ?? 0;
      if (s !== STONE.EMPTY) {
        const isLast = !!lastMove && lastMove[0] === r && lastMove[1] === c;
        const isAiLast = !!aiLastMove && aiLastMove[0] === r && aiLastMove[1] === c;
        drawStone(ctx, pad + c * cs, pad + r * cs, cs * 0.46, s as StoneValue, isLast || isAiLast);
      }
    }
  }

  // ── Hint stones ──
  if (showHints && hints.length > 0) {
    const playerStone: StoneValue = playerColor === 'BLACK' ? STONE.BLACK : STONE.WHITE;
    const alphas = [0.75, 0.6, 0.48, 0.38, 0.3];
    for (let i = 0; i < hints.length; i++) {
      const h = hints[i];
      if ((board[h.row]?.[h.col] ?? 0) !== STONE.EMPTY) continue;
      const hx = pad + h.col * cs;
      const hy = pad + h.row * cs;
      const alpha = alphas[Math.min(i, alphas.length - 1)];

      ctx.save();
      ctx.globalAlpha = alpha;
      drawStone(ctx, hx, hy, cs * 0.46, playerStone, false);
      ctx.restore();

      const playerWin = playerColor === 'BLACK' ? h.winRate : 1.0 - h.winRate;
      const pctText = `${Math.round(playerWin * 100)}%`;
      const fontSize = Math.max(10, Math.round(cs * 0.34));
      ctx.save();
      ctx.font = `bold ${fontSize}px sans-serif`;
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      const tw = ctx.measureText(pctText).width + 6;
      const th = fontSize + 4;
      ctx.fillStyle = 'rgba(0,0,0,0.65)';
      ctx.fillRect(hx - tw / 2, hy - th / 2, tw, th);
      ctx.fillStyle = i === 0 ? '#4ade80' : '#fff';
      ctx.fillText(pctText, hx, hy);
      ctx.restore();

      // Rank badge
      ctx.save();
      const numSize = Math.max(8, Math.round(cs * 0.26));
      ctx.font = `bold ${numSize}px sans-serif`;
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      const nr = numSize * 0.6;
      const nx = hx - cs * 0.32;
      const ny = hy - cs * 0.32;
      ctx.fillStyle = i === 0 ? '#16a34a' : '#6366f1';
      ctx.beginPath();
      ctx.arc(nx, ny, nr, 0, Math.PI * 2);
      ctx.fill();
      ctx.fillStyle = '#fff';
      ctx.fillText(String(i + 1), nx, ny);
      ctx.restore();
    }
  }

  // ── Hover preview ──
  if (
    isMyTurn &&
    hoverCell &&
    hoverCell[0] >= 0 &&
    hoverCell[0] < n &&
    hoverCell[1] >= 0 &&
    hoverCell[1] < n &&
    (board[hoverCell[0]]?.[hoverCell[1]] ?? 0) === STONE.EMPTY
  ) {
    const hx = pad + hoverCell[1] * cs;
    const hy = pad + hoverCell[0] * cs;
    const color: StoneValue = playerColor === 'BLACK' ? STONE.BLACK : STONE.WHITE;
    ctx.save();
    ctx.globalAlpha = 0.5;
    drawStone(ctx, hx, hy, cs * 0.46, color, false);
    ctx.restore();
  }
}

function drawStone(
  ctx: CanvasRenderingContext2D,
  x: number,
  y: number,
  r: number,
  color: StoneValue,
  isLast: boolean,
): void {
  const isBlack = color === STONE.BLACK;

  ctx.save();
  ctx.shadowColor = 'rgba(0,0,0,0.55)';
  ctx.shadowBlur = r * 0.6;
  ctx.shadowOffsetX = r * 0.15;
  ctx.shadowOffsetY = r * 0.2;

  const grad = ctx.createRadialGradient(x - r * 0.28, y - r * 0.32, r * 0.04, x, y, r);
  if (isBlack) {
    grad.addColorStop(0, '#666666');
    grad.addColorStop(0.35, '#282828');
    grad.addColorStop(1, '#050505');
  } else {
    grad.addColorStop(0, '#ffffff');
    grad.addColorStop(0.35, '#eeeeee');
    grad.addColorStop(1, '#b8b8b8');
  }

  ctx.beginPath();
  ctx.arc(x, y, r, 0, Math.PI * 2);
  ctx.fillStyle = grad;
  ctx.fill();
  ctx.restore();

  // Shine highlight
  const shine = ctx.createRadialGradient(x - r * 0.28, y - r * 0.35, 0, x - r * 0.1, y - r * 0.15, r * 0.6);
  if (isBlack) {
    shine.addColorStop(0, 'rgba(255,255,255,0.28)');
    shine.addColorStop(0.5, 'rgba(255,255,255,0.06)');
    shine.addColorStop(1, 'rgba(255,255,255,0)');
  } else {
    shine.addColorStop(0, 'rgba(255,255,255,0.90)');
    shine.addColorStop(0.5, 'rgba(255,255,255,0.30)');
    shine.addColorStop(1, 'rgba(255,255,255,0)');
  }
  ctx.beginPath();
  ctx.arc(x, y, r, 0, Math.PI * 2);
  ctx.fillStyle = shine;
  ctx.fill();

  if (isLast) {
    ctx.beginPath();
    ctx.arc(x, y, r * 0.28, 0, Math.PI * 2);
    ctx.fillStyle = isBlack ? 'rgba(255,255,255,0.7)' : 'rgba(0,0,0,0.55)';
    ctx.fill();
  }
}
