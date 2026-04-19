// Web Audio API 기반 착수 효과음. 기존 game.js 의 playSound() 를 모듈화.

let audioCtx: AudioContext | null = null;

interface WebkitWindow extends Window {
  webkitAudioContext?: typeof AudioContext;
}

export function playStoneSound(): void {
  try {
    if (!audioCtx) {
      const w = window as WebkitWindow;
      const Ctor = window.AudioContext || w.webkitAudioContext;
      if (!Ctor) return;
      audioCtx = new Ctor();
    }
    const ctx = audioCtx;
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.connect(gain);
    gain.connect(ctx.destination);
    osc.type = 'sine';
    osc.frequency.setValueAtTime(900, ctx.currentTime);
    osc.frequency.exponentialRampToValueAtTime(600, ctx.currentTime + 0.05);
    gain.gain.setValueAtTime(0.15, ctx.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.12);
    osc.start(ctx.currentTime);
    osc.stop(ctx.currentTime + 0.12);
  } catch {
    /* audio unavailable */
  }
}
