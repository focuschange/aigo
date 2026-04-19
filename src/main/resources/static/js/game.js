/**
 * AI 바둑 – Frontend Game Logic
 * Communicates with Spring Boot backend via REST API.
 * Renders a realistic Go board on HTML5 Canvas.
 */
'use strict';

// ─── Constants ────────────────────────────────────────────────────────────────
const STONE = { EMPTY: 0, BLACK: 1, WHITE: 2 };

// Star-point (hoshi) positions per board size
const HOSHI = {
  9:  [[2,2],[2,6],[4,4],[6,2],[6,6]],
  13: [[3,3],[3,9],[6,6],[9,3],[9,9]],
  19: [[3,3],[3,9],[3,15],[9,3],[9,9],[9,15],[15,3],[15,9],[15,15]]
};

const COL_LABELS = 'ABCDEFGHJKLMNOPQRST'; // no 'I'

// ─── State ────────────────────────────────────────────────────────────────────
const state = {
  gameId:       null,
  board:        null,
  boardSize:    19,
  cellSize:     32,
  padding:      36,
  playerColor:  'BLACK',
  currentPlayer:'BLACK',
  isMyTurn:     false,
  gameOver:     false,
  lastMove:     null,
  aiLastMove:   null,
  hoverRow:     -1,
  hoverCol:     -1,
  selectedSize: 19,
  selectedColor:'BLACK',
  selectedDiff: 'HARD',
  moveCount:    0,
  capturedByBlack: 0,
  capturedByWhite: 0,
  hints:        [],     // [{row, col, winRate, order}, ...]
  showHints:    false,
  hintLoading:  false,
};

// ─── DOM ──────────────────────────────────────────────────────────────────────
const canvas          = document.getElementById('boardCanvas');
const ctx             = canvas.getContext('2d');
const thinkingOverlay = document.getElementById('thinkingOverlay');
const setupPanel      = document.getElementById('setupPanel');
const gamePanel       = document.getElementById('gamePanel');
const statusBar       = document.getElementById('statusBar');
const blackCaptures   = document.getElementById('blackCaptures');
const whiteCaptures   = document.getElementById('whiteCaptures');
const blackDot        = document.getElementById('blackDot');
const whiteDot        = document.getElementById('whiteDot');
const blackCard       = document.getElementById('blackCard');
const whiteCard       = document.getElementById('whiteCard');
const scoreResult      = document.getElementById('scoreResult');
const finalBlack       = document.getElementById('finalBlack');
const finalWhite       = document.getElementById('finalWhite');
const winnerLabel      = document.getElementById('winnerLabel');
const blackTerritoryEl = document.getElementById('blackTerritory');
const whiteTerritoryEl = document.getElementById('whiteTerritory');
const blackPrisonersEl = document.getElementById('blackPrisoners');
const whitePrisonersEl = document.getElementById('whitePrisoners');
const moveCountEl      = document.getElementById('moveCount');
const winrateSection   = document.getElementById('winrateSection');
const winrateBlack     = document.getElementById('winrateBlack');
const winrateWhite     = document.getElementById('winrateWhite');
const winrateBlackText = document.getElementById('winrateBlackText');
const winrateWhiteText = document.getElementById('winrateWhiteText');

// ─── Option-button helpers ─────────────────────────────────────────────────
function setupOptionGroup(groupId, stateKey) {
  const group = document.getElementById(groupId);
  group.querySelectorAll('.opt-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      group.querySelectorAll('.opt-btn').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      state[stateKey] = btn.dataset.val;
    });
  });
}
setupOptionGroup('sizeGroup',  'selectedSize');
setupOptionGroup('colorGroup', 'selectedColor');
setupOptionGroup('diffGroup',  'selectedDiff');

// ─── Start game ───────────────────────────────────────────────────────────────
document.getElementById('startBtn').addEventListener('click', startGame);
document.getElementById('newGameBtn').addEventListener('click', () => {
  gamePanel.classList.add('hidden');
  setupPanel.classList.remove('hidden');
  scoreResult.classList.add('hidden');
});
document.getElementById('passBtn').addEventListener('click', () => {
  if (!state.isMyTurn || state.gameOver || state.hintLoading) return;
  apiFetch(`/api/game/${state.gameId}/pass`, 'POST', null);
});
document.getElementById('resignBtn').addEventListener('click', () => {
  if (state.gameOver) return;
  if (!confirm('정말 기권하시겠습니까?')) return;
  apiFetch(`/api/game/${state.gameId}/resign`, 'POST', null);
});

const hintBtn = document.getElementById('hintBtn');
hintBtn.addEventListener('click', async () => {
  if (!state.isMyTurn || state.gameOver || !state.gameId || state.hintLoading) return;

  // 토글: 이미 힌트 표시 중이면 끄기
  if (state.showHints) {
    state.hints = [];
    state.showHints = false;
    hintBtn.classList.remove('active');
    render();
    return;
  }

  state.hintLoading = true;
  hintBtn.classList.add('active');
  hintBtn.textContent = '분석 중…';

  try {
    const hints = await apiFetch(`/api/game/${state.gameId}/hints`, 'GET', null);
    if (hints && hints.length > 0) {
      state.hints = hints;
      state.showHints = true;
      render();
    } else {
      state.hints = [];
      state.showHints = false;
      hintBtn.classList.remove('active');
    }
  } finally {
    state.hintLoading = false;
    hintBtn.textContent = '힌트';
  }
});

async function startGame() {
  const req = {
    boardSize:   parseInt(state.selectedSize),
    playerColor: state.selectedColor,
    difficulty:  state.selectedDiff,
  };
  setupPanel.classList.add('hidden');
  gamePanel.classList.remove('hidden');
  scoreResult.classList.add('hidden');
  showThinking(true);

  const data = await apiFetch('/api/game/new', 'POST', req);
  if (data) applyState(data, null);
}

// ─── Canvas interactions ───────────────────────────────────────────────────
canvas.addEventListener('mousemove', e => {
  if (!state.board || !state.isMyTurn || state.gameOver) return;
  const [row, col] = canvasToBoard(e);
  if (row !== state.hoverRow || col !== state.hoverCol) {
    state.hoverRow = row;
    state.hoverCol = col;
    render();
  }
});
canvas.addEventListener('mouseleave', () => {
  state.hoverRow = -1; state.hoverCol = -1; render();
});
canvas.addEventListener('click', async e => {
  if (!state.board || !state.isMyTurn || state.gameOver || state.hintLoading) return;
  const [row, col] = canvasToBoard(e);
  if (row < 0 || row >= state.boardSize || col < 0 || col >= state.boardSize) return;
  if (state.board[row][col] !== STONE.EMPTY) return;

  state.isMyTurn = false;
  state.hoverRow = -1;
  state.hoverCol = -1;

  // 힌트 초기화
  state.hints = [];
  state.showHints = false;
  hintBtn.classList.remove('active');

  // 즉시 플레이어 돌 표시
  state.board[row][col] = state.playerColor === 'BLACK' ? STONE.BLACK : STONE.WHITE;
  state.lastMove = [row, col];
  state.aiLastMove = null;
  render();
  playSound();
  showThinking(true);

  const prevAiMove = state.aiLastMove;
  const data = await apiFetch(`/api/game/${state.gameId}/move`, 'POST', { row, col });
  if (data) applyState(data, prevAiMove);
});

function canvasToBoard(e) {
  const rect = canvas.getBoundingClientRect();
  // e.clientX/Y, rect, state.padding, state.cellSize 모두 CSS 픽셀 기준이므로
  // DPR 스케일 없이 직접 계산한다 (canvas.width는 물리 픽셀이라 사용 불가).
  const x = e.clientX - rect.left;
  const y = e.clientY - rect.top;
  const col = Math.round((x - state.padding) / state.cellSize);
  const row = Math.round((y - state.padding) / state.cellSize);
  return [row, col];
}

// ─── API ───────────────────────────────────────────────────────────────────
async function apiFetch(url, method, body) {
  try {
    const opts = { method, headers: { 'Content-Type': 'application/json' } };
    if (body) opts.body = JSON.stringify(body);
    const res = await fetch(url, opts);
    if (!res.ok) {
      const text = await res.text();
      console.error('API error:', text);
      showThinking(false);
      return null;
    }
    return await res.json();
  } catch (err) {
    console.error('Fetch error:', err);
    showThinking(false);
    return null;
  }
}

// ─── Apply server state ────────────────────────────────────────────────────
function applyState(data, prevAiMove) {
  showThinking(false);

  state.gameId        = data.gameId;
  state.board         = data.board;
  state.boardSize     = data.boardSize;
  state.playerColor   = data.playerColor;
  state.currentPlayer = data.currentPlayer;
  state.gameOver      = data.gameOver;
  state.lastMove      = data.lastMove;
  state.aiLastMove    = data.aiLastMove;
  state.capturedByBlack = data.capturedByBlack;
  state.capturedByWhite = data.capturedByWhite;
  state.isMyTurn      = !data.gameOver && data.currentPlayer === data.playerColor;
  state.moveCount     = (data.capturedByBlack + data.capturedByWhite) || 0;

  // Resize canvas if board size changed
  resizeCanvas();

  // Update UI
  statusBar.textContent = data.message || '';
  blackCaptures.textContent = `포획: ${data.capturedByBlack}`;
  whiteCaptures.textContent = `포획: ${data.capturedByWhite}`;
  moveCountEl.textContent = state.moveCount;

  // Win rate bar
  updateWinRate(data.blackWinRate);

  // Active player indicators
  const blackActive = data.currentPlayer === 'BLACK' && !data.gameOver;
  const whiteActive = data.currentPlayer === 'WHITE' && !data.gameOver;
  blackDot.classList.toggle('active', blackActive);
  whiteDot.classList.toggle('active', whiteActive);
  blackCard.classList.toggle('active', blackActive && data.playerColor === 'BLACK');
  whiteCard.classList.toggle('active', whiteActive && data.playerColor === 'WHITE');

  // Game over – 한국식 계가 세부 표시
  if (data.gameOver) {
    scoreResult.classList.remove('hidden');
    // 잡은 돌 (포로) 수
    blackPrisonersEl.textContent = data.capturedByBlack;
    whitePrisonersEl.textContent = data.capturedByWhite;
    // 빈집 = 최종점수 - 잡은돌 (백은 코미 빼기)
    const blackTerritory = Math.max(0, data.blackScore - data.capturedByBlack);
    const whiteTerritory = Math.max(0, data.whiteScore - data.capturedByWhite - 6.5);
    blackTerritoryEl.textContent = blackTerritory % 1 === 0 ? blackTerritory : blackTerritory.toFixed(1);
    whiteTerritoryEl.textContent = whiteTerritory % 1 === 0 ? whiteTerritory : whiteTerritory.toFixed(1);
    // 합계
    finalBlack.textContent = data.blackScore % 1 === 0 ? data.blackScore : data.blackScore.toFixed(1);
    finalWhite.textContent = data.whiteScore % 1 === 0 ? data.whiteScore : data.whiteScore.toFixed(1);
    winnerLabel.textContent = data.winner === data.playerColor ? '승리! 🎉' : '패배 😔';
    canvas.style.cursor = 'default';
  } else {
    canvas.style.cursor = state.isMyTurn ? 'crosshair' : 'not-allowed';
  }

  render();
  playSound();
}

// ─── Win rate display ───────────────────────────────────────────────────────
function updateWinRate(blackWinRate) {
  if (blackWinRate < 0) return; // 정보 없음
  winrateSection.classList.remove('hidden');

  const bPct = Math.round(blackWinRate * 100);
  const wPct = 100 - bPct;

  winrateBlack.style.width = Math.max(bPct, 2) + '%';
  winrateWhite.style.width = Math.max(wPct, 2) + '%';
  winrateBlackText.textContent = bPct >= 10 ? bPct + '%' : '';
  winrateWhiteText.textContent = wPct >= 10 ? wPct + '%' : '';
}

// ─── Canvas resize ─────────────────────────────────────────────────────────
function resizeCanvas() {
  const size  = state.boardSize;
  const avail = Math.min(
    window.innerWidth  - 320,   // sidebar ~ 280px + gap
    window.innerHeight - 100    // header
  );
  const maxCell = Math.floor((avail - 80) / (size - 1));
  state.cellSize = Math.max(24, Math.min(52, maxCell));
  state.padding  = Math.round(state.cellSize * 1.2);

  const boardPx = (size - 1) * state.cellSize + state.padding * 2;
  const dpr = window.devicePixelRatio || 1;
  canvas.width  = boardPx * dpr;
  canvas.height = boardPx * dpr;
  canvas.style.width  = boardPx + 'px';
  canvas.style.height = boardPx + 'px';
  ctx.scale(dpr, dpr);
}

// ─── Render ────────────────────────────────────────────────────────────────
function render() {
  if (!state.board) return;
  const { boardSize: n, cellSize: cs, padding: pad } = state;
  const boardPx = (n - 1) * cs + pad * 2;

  // ── Background (wooden) ──
  const bgGrad = ctx.createLinearGradient(0, 0, boardPx, boardPx);
  bgGrad.addColorStop(0,   '#e8c068');
  bgGrad.addColorStop(0.4, '#d4a840');
  bgGrad.addColorStop(1,   '#b88820');
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
  ctx.shadowBlur  = 16;
  ctx.shadowOffsetX = ctx.shadowOffsetY = 4;
  ctx.strokeStyle = '#8a6010';
  ctx.lineWidth   = 2;
  ctx.strokeRect(pad, pad, (n-1)*cs, (n-1)*cs);
  ctx.restore();

  // ── Grid lines ──
  ctx.strokeStyle = 'rgba(90, 60, 10, 0.85)';
  ctx.lineWidth   = 0.9;
  for (let i = 0; i < n; i++) {
    const x = pad + i * cs;
    const y = pad + i * cs;
    // vertical
    ctx.beginPath(); ctx.moveTo(x, pad); ctx.lineTo(x, pad + (n-1)*cs); ctx.stroke();
    // horizontal
    ctx.beginPath(); ctx.moveTo(pad, y); ctx.lineTo(pad + (n-1)*cs, y); ctx.stroke();
  }

  // ── Star points ──
  const stars = HOSHI[n] || [];
  ctx.fillStyle = '#6a4808';
  for (const [r, c] of stars) {
    ctx.beginPath();
    ctx.arc(pad + c*cs, pad + r*cs, cs * 0.1, 0, Math.PI*2);
    ctx.fill();
  }

  // ── Coordinates ──
  ctx.save();
  ctx.fillStyle  = '#5d3a00';
  ctx.font       = `bold ${Math.round(cs * 0.38)}px serif`;
  ctx.textAlign  = 'center';
  ctx.textBaseline = 'middle';
  for (let i = 0; i < n; i++) {
    const x = pad + i * cs;
    const y = pad + i * cs;
    const col = COL_LABELS[i];
    const row = String(n - i);
    // column labels (top & bottom)
    ctx.fillText(col, x, pad / 2);
    ctx.fillText(col, x, boardPx - pad / 2);
    // row labels (left & right)
    ctx.fillText(row, pad / 2, y);
    ctx.fillText(row, boardPx - pad / 2, y);
  }
  ctx.restore();

  // ── Stones ──
  for (let r = 0; r < n; r++) {
    for (let c = 0; c < n; c++) {
      const s = state.board[r][c];
      if (s !== STONE.EMPTY) {
        const isLast = state.lastMove && state.lastMove[0] === r && state.lastMove[1] === c;
        const isAiLast = state.aiLastMove && state.aiLastMove[0] === r && state.aiLastMove[1] === c;
        drawStone(pad + c*cs, pad + r*cs, cs*0.46, s, isLast || isAiLast);
      }
    }
  }

  // ── Hint stones ──
  if (state.showHints && state.hints.length > 0) {
    const playerStone = state.playerColor === 'BLACK' ? STONE.BLACK : STONE.WHITE;
    // 힌트용 색상 (순위별 투명도)
    const alphas = [0.75, 0.60, 0.48, 0.38, 0.30];
    for (let i = 0; i < state.hints.length; i++) {
      const h = state.hints[i];
      if (state.board[h.row][h.col] !== STONE.EMPTY) continue;
      const hx = pad + h.col * cs;
      const hy = pad + h.row * cs;
      const alpha = alphas[Math.min(i, alphas.length - 1)];

      // 반투명 돌
      ctx.save();
      ctx.globalAlpha = alpha;
      drawStone(hx, hy, cs * 0.46, playerStone, false);
      ctx.restore();

      // 승률 텍스트 (흑 관점 → 플레이어 관점 변환)
      const playerWin = state.playerColor === 'BLACK' ? h.winRate : 1.0 - h.winRate;
      const pctText = Math.round(playerWin * 100) + '%';
      const fontSize = Math.max(10, Math.round(cs * 0.34));
      ctx.save();
      ctx.font = `bold ${fontSize}px sans-serif`;
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      // 텍스트 배경
      const tw = ctx.measureText(pctText).width + 6;
      const th = fontSize + 4;
      ctx.fillStyle = 'rgba(0,0,0,0.65)';
      ctx.fillRect(hx - tw/2, hy - th/2, tw, th);
      // 텍스트
      ctx.fillStyle = i === 0 ? '#4ade80' : '#fff';
      ctx.fillText(pctText, hx, hy);
      ctx.restore();

      // 순위 번호 (좌상단)
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
  if (state.isMyTurn && state.hoverRow >= 0 && state.hoverRow < n &&
      state.hoverCol >= 0 && state.hoverCol < n &&
      state.board[state.hoverRow][state.hoverCol] === STONE.EMPTY) {
    const hx = pad + state.hoverCol * cs;
    const hy = pad + state.hoverRow * cs;
    const color = state.playerColor === 'BLACK' ? STONE.BLACK : STONE.WHITE;
    ctx.save();
    ctx.globalAlpha = 0.5;
    drawStone(hx, hy, cs*0.46, color, false);
    ctx.restore();
  }
}

// ─── Draw a single stone ───────────────────────────────────────────────────
function drawStone(x, y, r, color, isLast) {
  const isBlack = color === STONE.BLACK;

  // Drop shadow
  ctx.save();
  ctx.shadowColor   = 'rgba(0,0,0,0.55)';
  ctx.shadowBlur    = r * 0.6;
  ctx.shadowOffsetX = r * 0.15;
  ctx.shadowOffsetY = r * 0.2;

  // Stone body gradient
  const grad = ctx.createRadialGradient(
    x - r*0.28, y - r*0.32, r*0.04,
    x,          y,          r
  );
  if (isBlack) {
    grad.addColorStop(0, '#666666');
    grad.addColorStop(0.35, '#282828');
    grad.addColorStop(1,  '#050505');
  } else {
    grad.addColorStop(0, '#ffffff');
    grad.addColorStop(0.35, '#eeeeee');
    grad.addColorStop(1,  '#b8b8b8');
  }

  ctx.beginPath();
  ctx.arc(x, y, r, 0, Math.PI*2);
  ctx.fillStyle = grad;
  ctx.fill();
  ctx.restore();

  // Shine highlight
  const shine = ctx.createRadialGradient(
    x - r*0.28, y - r*0.35, 0,
    x - r*0.1,  y - r*0.15, r*0.6
  );
  if (isBlack) {
    shine.addColorStop(0, 'rgba(255,255,255,0.28)');
    shine.addColorStop(0.5,'rgba(255,255,255,0.06)');
    shine.addColorStop(1, 'rgba(255,255,255,0)');
  } else {
    shine.addColorStop(0, 'rgba(255,255,255,0.90)');
    shine.addColorStop(0.5,'rgba(255,255,255,0.30)');
    shine.addColorStop(1, 'rgba(255,255,255,0)');
  }
  ctx.beginPath();
  ctx.arc(x, y, r, 0, Math.PI*2);
  ctx.fillStyle = shine;
  ctx.fill();

  // Last-move marker
  if (isLast) {
    ctx.beginPath();
    ctx.arc(x, y, r * 0.28, 0, Math.PI*2);
    ctx.fillStyle = isBlack ? 'rgba(255,255,255,0.7)' : 'rgba(0,0,0,0.55)';
    ctx.fill();
  }
}

// ─── Sound (Web Audio API) ─────────────────────────────────────────────────
let audioCtx = null;
function playSound() {
  try {
    if (!audioCtx) audioCtx = new (window.AudioContext || window.webkitAudioContext)();
    const osc  = audioCtx.createOscillator();
    const gain = audioCtx.createGain();
    osc.connect(gain);
    gain.connect(audioCtx.destination);
    osc.type = 'sine';
    osc.frequency.setValueAtTime(900, audioCtx.currentTime);
    osc.frequency.exponentialRampToValueAtTime(600, audioCtx.currentTime + 0.05);
    gain.gain.setValueAtTime(0.15, audioCtx.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.001, audioCtx.currentTime + 0.12);
    osc.start(audioCtx.currentTime);
    osc.stop(audioCtx.currentTime + 0.12);
  } catch (e) { /* audio not available */ }
}

// ─── Thinking overlay ──────────────────────────────────────────────────────
function showThinking(show) {
  thinkingOverlay.classList.toggle('hidden', !show);
}

// ─── Init ──────────────────────────────────────────────────────────────────
window.addEventListener('resize', () => { if (state.board) { resizeCanvas(); render(); } });
