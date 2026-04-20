import type { GameState } from '../types';

export function Scoreboard({ state }: { state: GameState }) {
  const blackActive = state.currentPlayer === 'BLACK' && !state.gameOver;
  const whiteActive = state.currentPlayer === 'WHITE' && !state.gameOver;

  return (
    <div className="flex items-center justify-between gap-3">
      <PlayerCard
        stone="black"
        captures={state.capturedByBlack}
        active={blackActive}
        isPlayer={state.playerColor === 'BLACK'}
      />
      <span className="shrink-0 text-xs font-bold tracking-widest text-ink-dim">VS</span>
      <PlayerCard
        stone="white"
        captures={state.capturedByWhite}
        active={whiteActive}
        isPlayer={state.playerColor === 'WHITE'}
      />
    </div>
  );
}

function PlayerCard({
  stone,
  captures,
  active,
  isPlayer,
}: {
  stone: 'black' | 'white';
  captures: number;
  active: boolean;
  isPlayer: boolean;
}) {
  const isBlack = stone === 'black';
  return (
    <div
      className={`flex flex-1 items-center gap-2 rounded border px-2 py-2 transition ${
        isPlayer && active
          ? 'border-accent bg-[#1a0c18]'
          : 'border-[#2a2a4a] bg-sidebar'
      }`}
    >
      <span
        className={`h-7 w-7 shrink-0 rounded-full ${
          isBlack
            ? 'bg-gradient-to-br from-[#666] via-[#282828] to-[#050505]'
            : 'bg-gradient-to-br from-white via-[#eee] to-[#b8b8b8]'
        }`}
      />
      <div className="flex flex-1 flex-col leading-tight">
        <span className="text-sm font-semibold text-white">{isBlack ? '흑' : '백'}</span>
        <span className="text-xs text-ink-dim">포획: {captures}</span>
      </div>
      <span
        className={`h-2 w-2 rounded-full transition ${active ? 'bg-accent shadow-[0_0_8px_rgba(233,69,96,0.8)]' : 'bg-transparent'}`}
      />
    </div>
  );
}
