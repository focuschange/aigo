import { useCallback, useEffect, useRef, useState } from 'react';
import type { HintMove, Stone } from '../types';
import {
  canvasToBoard,
  computeLayout,
  renderBoard,
  resizeCanvas,
  type BoardLayout,
} from '../canvas/boardRenderer';

interface Props {
  board: number[][];
  boardSize: number;
  lastMove: [number, number] | null;
  aiLastMove: [number, number] | null;
  playerColor: Stone;
  isMyTurn: boolean;
  gameOver: boolean;
  hints: HintMove[];
  showHints: boolean;
  onMove: (row: number, col: number) => void;
  disabled: boolean; // AI 사고중·힌트 로딩 등으로 입력 차단
}

export function GoBoard(props: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [layout, setLayout] = useState<BoardLayout>(() => computeLayout(props.boardSize));
  const [hoverCell, setHoverCell] = useState<[number, number] | null>(null);

  // 보드 크기 변경·윈도우 리사이즈 시 레이아웃 재계산
  useEffect(() => {
    const refresh = () => setLayout(computeLayout(props.boardSize));
    refresh();
    window.addEventListener('resize', refresh);
    return () => window.removeEventListener('resize', refresh);
  }, [props.boardSize]);

  // Canvas 크기 맞추기
  useEffect(() => {
    if (canvasRef.current) resizeCanvas(canvasRef.current, layout);
  }, [layout]);

  // 실제 렌더링
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    renderBoard(ctx, {
      board: props.board,
      layout,
      lastMove: props.lastMove,
      aiLastMove: props.aiLastMove,
      hoverCell,
      isMyTurn: props.isMyTurn && !props.disabled,
      playerColor: props.playerColor,
      hints: props.hints,
      showHints: props.showHints,
    });
  }, [
    props.board,
    props.lastMove,
    props.aiLastMove,
    props.isMyTurn,
    props.playerColor,
    props.hints,
    props.showHints,
    props.disabled,
    layout,
    hoverCell,
  ]);

  const handleClick = useCallback(
    (e: React.MouseEvent<HTMLCanvasElement>) => {
      if (!props.isMyTurn || props.gameOver || props.disabled) return;
      const canvas = canvasRef.current;
      if (!canvas) return;
      const [row, col] = canvasToBoard(canvas, e.clientX, e.clientY, layout);
      if (row < 0 || row >= props.boardSize || col < 0 || col >= props.boardSize) return;
      if (props.board[row]?.[col] !== 0) return;
      setHoverCell(null);
      props.onMove(row, col);
    },
    [layout, props],
  );

  const handleMove = useCallback(
    (e: React.MouseEvent<HTMLCanvasElement>) => {
      if (!props.isMyTurn || props.gameOver || props.disabled) return;
      const canvas = canvasRef.current;
      if (!canvas) return;
      const [row, col] = canvasToBoard(canvas, e.clientX, e.clientY, layout);
      if (hoverCell?.[0] !== row || hoverCell?.[1] !== col) setHoverCell([row, col]);
    },
    [layout, hoverCell, props.isMyTurn, props.gameOver, props.disabled],
  );

  const handleLeave = useCallback(() => setHoverCell(null), []);

  const cursor = props.gameOver
    ? 'cursor-default'
    : props.isMyTurn && !props.disabled
      ? 'cursor-crosshair'
      : 'cursor-not-allowed';

  return (
    <canvas
      ref={canvasRef}
      onClick={handleClick}
      onMouseMove={handleMove}
      onMouseLeave={handleLeave}
      className={`block ${cursor}`}
    />
  );
}
