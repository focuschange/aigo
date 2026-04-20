import { useState } from 'react';
import type { Difficulty, NewGameRequest, Stone } from '../types';

interface Props {
  onStart: (req: NewGameRequest) => void;
  loading: boolean;
  errorMessage?: string | null;
}

const SIZES: ReadonlyArray<{ val: number; label: string }> = [
  { val: 9, label: '9×9' },
  { val: 19, label: '19×19' },
  { val: 13, label: '13×13' },
];

const COLORS: ReadonlyArray<{ val: Stone; label: string; stone: 'black' | 'white' }> = [
  { val: 'BLACK', label: '흑', stone: 'black' },
  { val: 'WHITE', label: '백', stone: 'white' },
];

const DIFFS: ReadonlyArray<{ val: Difficulty; label: string }> = [
  { val: 'EASY', label: '입문' },
  { val: 'MEDIUM', label: '중급' },
  { val: 'HARD', label: '고급' },
];

export function SetupPanel({ onStart, loading, errorMessage }: Props) {
  const [size, setSize] = useState<number>(19);
  const [color, setColor] = useState<Stone>('BLACK');
  const [diff, setDiff] = useState<Difficulty>('HARD');

  return (
    <section className="space-y-5 rounded bg-panel p-5 shadow-lg">
      <h2 className="text-lg font-semibold text-white">새 게임</h2>

      <Field label="바둑판 크기">
        {SIZES.map((s) => (
          <OptButton key={s.val} active={size === s.val} onClick={() => setSize(s.val)}>
            {s.label}
          </OptButton>
        ))}
      </Field>

      <Field label="돌 색깔">
        {COLORS.map((c) => (
          <OptButton key={c.val} active={color === c.val} onClick={() => setColor(c.val)}>
            <span
              className={`mr-1.5 inline-block h-3.5 w-3.5 rounded-full align-middle ${
                c.stone === 'black'
                  ? 'bg-gradient-to-br from-[#666] via-[#282828] to-[#050505]'
                  : 'bg-gradient-to-br from-white via-[#eee] to-[#b8b8b8]'
              }`}
            />
            {c.label}
          </OptButton>
        ))}
      </Field>

      <Field label="AI 난이도">
        {DIFFS.map((d) => (
          <OptButton key={d.val} active={diff === d.val} onClick={() => setDiff(d.val)}>
            {d.label}
          </OptButton>
        ))}
      </Field>

      {errorMessage && <p className="text-sm text-accent">{errorMessage}</p>}

      <button
        className="w-full rounded bg-accent px-4 py-2.5 font-semibold tracking-wider text-white shadow transition hover:brightness-110 disabled:cursor-not-allowed disabled:opacity-50"
        disabled={loading}
        onClick={() => onStart({ boardSize: size, playerColor: color, difficulty: diff })}
      >
        {loading ? '시작 중…' : '게임 시작'}
      </button>
    </section>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <label className="mb-2 block text-sm font-medium text-ink-dim">{label}</label>
      <div className="flex gap-2">{children}</div>
    </div>
  );
}

function OptButton({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      onClick={onClick}
      className={`flex-1 rounded border px-3 py-2 text-sm font-medium transition ${
        active
          ? 'border-accent bg-accent text-white shadow'
          : 'border-[#2a2a4a] bg-[#0f1724] text-ink hover:border-accent/60'
      }`}
    >
      {children}
    </button>
  );
}
