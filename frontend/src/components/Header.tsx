export function Header() {
  return (
    <header className="flex items-center justify-between border-b border-[#2a2a4a] bg-gradient-to-r from-accent-2 to-panel px-7 py-3">
      <div className="flex items-center gap-3">
        <span className="h-5 w-5 rounded-full bg-gradient-to-br from-[#666] via-[#282828] to-[#050505] shadow-[inset_-2px_-2px_4px_rgba(0,0,0,0.4),inset_2px_2px_4px_rgba(255,255,255,0.2)]" />
        <h1 className="text-2xl font-bold tracking-widest text-white">AI 바둑</h1>
        <span className="h-5 w-5 rounded-full bg-gradient-to-br from-white via-[#eee] to-[#b8b8b8] shadow-[inset_-2px_-2px_4px_rgba(0,0,0,0.25),inset_2px_2px_4px_rgba(255,255,255,0.9)]" />
      </div>
      <span className="text-xs tracking-widest text-ink-dim">Powered by KataGo</span>
    </header>
  );
}
