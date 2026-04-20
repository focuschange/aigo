import type { GameState } from '../types';

export function ScoreResult({ state }: { state: GameState }) {
  const blackTerritory = Math.max(0, state.blackScore - state.capturedByBlack);
  const whiteTerritory = Math.max(0, state.whiteScore - state.capturedByWhite - 6.5);
  const winnerText = state.winner === state.playerColor ? '승리! 🎉' : '패배 😔';

  return (
    <div className="rounded border border-[#2a2a4a] bg-sidebar p-3">
      <h3 className="mb-2 flex items-baseline justify-between text-sm font-semibold text-white">
        최종 점수 <small className="text-xs text-ink-dim">(한국식 계가)</small>
      </h3>
      <div className="grid grid-cols-3 gap-x-3 gap-y-1 text-sm">
        <span />
        <span className="text-center font-medium text-ink-dim">흑</span>
        <span className="text-center font-medium text-ink-dim">백</span>

        <span className="text-ink-dim">빈집(영역)</span>
        <span className="text-center">{fmt(blackTerritory)}</span>
        <span className="text-center">{fmt(whiteTerritory)}</span>

        <span className="text-ink-dim">잡은 돌</span>
        <span className="text-center">{state.capturedByBlack}</span>
        <span className="text-center">{state.capturedByWhite}</span>

        <span className="text-ink-dim">코미</span>
        <span className="text-center">—</span>
        <span className="text-center">6.5</span>

        <span className="font-semibold text-white">합계</span>
        <span className="text-center font-semibold text-white">{fmt(state.blackScore)}</span>
        <span className="text-center font-semibold text-white">{fmt(state.whiteScore)}</span>
      </div>
      <p className="mt-3 text-center text-lg font-bold text-accent">{winnerText}</p>
    </div>
  );
}

function fmt(v: number): string {
  return v % 1 === 0 ? String(v) : v.toFixed(1);
}
