interface Props {
  blackWinRate: number; // -1 이면 숨김
}

export function WinRateBar({ blackWinRate }: Props) {
  if (blackWinRate < 0) return null;

  const bPct = Math.round(blackWinRate * 100);
  const wPct = 100 - bPct;

  return (
    <div>
      <label className="mb-1 block text-xs font-medium text-ink-dim">승률</label>
      <div className="flex h-6 overflow-hidden rounded border border-[#2a2a4a]">
        <div
          className="flex items-center justify-center bg-gradient-to-r from-[#111] to-[#333] text-xs font-semibold text-white transition-[width]"
          style={{ width: `${Math.max(bPct, 2)}%` }}
        >
          {bPct >= 10 ? `${bPct}%` : ''}
        </div>
        <div
          className="flex items-center justify-center bg-gradient-to-l from-[#ddd] to-[#fff] text-xs font-semibold text-gray-800 transition-[width]"
          style={{ width: `${Math.max(wPct, 2)}%` }}
        >
          {wPct >= 10 ? `${wPct}%` : ''}
        </div>
      </div>
      <div className="mt-1 flex justify-between text-xs text-ink-dim">
        <span>흑</span>
        <span>백</span>
      </div>
    </div>
  );
}
