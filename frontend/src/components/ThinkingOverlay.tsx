export function ThinkingOverlay({ visible }: { visible: boolean }) {
  if (!visible) return null;
  return (
    <div className="pointer-events-none absolute inset-0 flex items-center justify-center bg-black/30 backdrop-blur-[1px]">
      <div className="flex items-center gap-3 rounded bg-panel/90 px-4 py-3 shadow-xl">
        <div className="h-5 w-5 animate-spin rounded-full border-2 border-ink-dim border-t-accent" />
        <span className="text-sm font-medium text-ink">KataGo 생각 중…</span>
      </div>
    </div>
  );
}
