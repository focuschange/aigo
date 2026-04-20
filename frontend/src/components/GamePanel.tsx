import { Flag, HandMetal, Lightbulb } from 'lucide-react';
import type { GameState, HintMove } from '../types';
import { WinRateBar } from './WinRateBar';
import { Scoreboard } from './Scoreboard';
import { ScoreResult } from './ScoreResult';

interface Props {
  state: GameState;
  hintLoading: boolean;
  showHints: boolean;
  hints: HintMove[];
  rateLimited: boolean;
  onHint: () => void;
  onClearHints: () => void;
  onPass: () => void;
  onResign: () => void;
  onNewGame: () => void;
}

export function GamePanel({
  state,
  hintLoading,
  showHints,
  onHint,
  onClearHints,
  onPass,
  onResign,
  onNewGame,
}: Props) {
  const isMyTurn = !state.gameOver && state.currentPlayer === state.playerColor;
  const moveCount = (state.capturedByBlack + state.capturedByWhite) || 0;

  return (
    <section className="space-y-4 rounded bg-panel p-5 shadow-lg">
      <Scoreboard state={state} />

      <div className="rounded bg-sidebar px-3 py-2 text-center text-sm text-ink">
        {state.message || (isMyTurn ? '당신의 차례' : 'AI 생각 중...')}
      </div>

      <WinRateBar blackWinRate={state.blackWinRate} />

      <div className="text-right text-xs text-ink-dim">
        수: <span className="font-semibold text-ink">{moveCount}</span>
      </div>

      {!state.gameOver && (
        <div className="flex gap-2">
          <CtrlBtn
            active={showHints}
            disabled={!isMyTurn || hintLoading}
            onClick={showHints ? onClearHints : onHint}
            icon={<Lightbulb className="h-4 w-4" />}
          >
            {hintLoading ? '분석 중…' : showHints ? '힌트 끄기' : '힌트'}
          </CtrlBtn>
          <CtrlBtn
            disabled={!isMyTurn || hintLoading}
            onClick={onPass}
            icon={<HandMetal className="h-4 w-4" />}
          >
            패스
          </CtrlBtn>
          <CtrlBtn
            variant="danger"
            disabled={state.gameOver}
            onClick={() => {
              if (confirm('정말 기권하시겠습니까?')) onResign();
            }}
            icon={<Flag className="h-4 w-4" />}
          >
            기권
          </CtrlBtn>
        </div>
      )}

      {state.gameOver && <ScoreResult state={state} />}

      <button
        className="w-full rounded bg-accent-2 px-4 py-2.5 font-semibold tracking-wider text-white shadow transition hover:brightness-110"
        onClick={onNewGame}
      >
        새 게임
      </button>
    </section>
  );
}

function CtrlBtn({
  children,
  onClick,
  disabled,
  active,
  variant,
  icon,
}: {
  children: React.ReactNode;
  onClick: () => void;
  disabled?: boolean;
  active?: boolean;
  variant?: 'danger';
  icon?: React.ReactNode;
}) {
  const base =
    'flex-1 inline-flex items-center justify-center gap-1.5 rounded border px-3 py-2 text-sm font-medium transition disabled:cursor-not-allowed disabled:opacity-50';
  const cls =
    variant === 'danger'
      ? 'border-accent bg-[#2a0a14] text-accent hover:bg-accent hover:text-white'
      : active
        ? 'border-accent bg-accent text-white'
        : 'border-[#2a2a4a] bg-[#0f1724] text-ink hover:border-accent/60';
  return (
    <button className={`${base} ${cls}`} onClick={onClick} disabled={disabled}>
      {icon}
      {children}
    </button>
  );
}
