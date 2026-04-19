import { useEffect, useState } from 'react';
import { Header } from './components/Header';
import { GoBoard } from './components/GoBoard';
import { SetupPanel } from './components/SetupPanel';
import { GamePanel } from './components/GamePanel';
import { ThinkingOverlay } from './components/ThinkingOverlay';
import {
  useGameState,
  useHints,
  useNewGame,
  usePass,
  usePlayerMove,
  useResign,
} from './api/hooks';
import { ApiError } from './api/client';
import { playStoneSound } from './sound';
import type { HintMove, NewGameRequest } from './types';

export default function App() {
  const [gameId, setGameId] = useState<string | null>(null);
  const [hints, setHints] = useState<HintMove[]>([]);
  const [showHints, setShowHints] = useState(false);
  const [rateLimitMsg, setRateLimitMsg] = useState<string | null>(null);

  const gameQuery = useGameState(gameId);
  const newGame = useNewGame((s) => {
    setGameId(s.gameId);
    setHints([]);
    setShowHints(false);
    playStoneSound();
  });
  const moveMut = usePlayerMove(gameId);
  const passMut = usePass(gameId);
  const resignMut = useResign(gameId);
  const hintsMut = useHints(gameId);

  // 새 게임 에러 → 레이트리밋/일반 오류 메시지 매핑
  useEffect(() => {
    if (!newGame.error) {
      setRateLimitMsg(null);
      return;
    }
    if (newGame.error instanceof ApiError && newGame.error.status === 429) {
      setRateLimitMsg(
        `요청이 너무 많습니다. ${newGame.error.retryAfter ?? 60}초 후 다시 시도해 주세요.`,
      );
    } else {
      setRateLimitMsg('게임 생성 중 오류가 발생했습니다.');
    }
  }, [newGame.error]);

  // 착수·패스 성공 시 효과음
  useEffect(() => {
    if (moveMut.isSuccess) playStoneSound();
  }, [moveMut.isSuccess, moveMut.data]);
  useEffect(() => {
    if (passMut.isSuccess) playStoneSound();
  }, [passMut.isSuccess, passMut.data]);

  // 착수·패스 진행 중이면 힌트 초기화
  useEffect(() => {
    if (moveMut.isPending || passMut.isPending) {
      setHints([]);
      setShowHints(false);
    }
  }, [moveMut.isPending, passMut.isPending]);

  const state = gameQuery.data;
  const aiThinking =
    (!!state && !state.gameOver && state.currentPlayer !== state.playerColor) ||
    moveMut.isPending ||
    passMut.isPending;

  const handleStart = (req: NewGameRequest) => newGame.mutate(req);

  const handleMove = (row: number, col: number) => moveMut.mutate({ row, col });

  const handleHint = async () => {
    try {
      const result = await hintsMut.mutateAsync();
      if (result.length > 0) {
        setHints(result);
        setShowHints(true);
      }
    } catch {
      /* 세션 만료/429 등은 다음 액션에서 처리 */
    }
  };

  const handleClearHints = () => {
    setHints([]);
    setShowHints(false);
  };

  const handleNewGame = () => {
    setGameId(null);
    setHints([]);
    setShowHints(false);
  };

  return (
    <div className="flex min-h-screen flex-col bg-bg font-sans text-ink">
      <Header />

      <main className="flex flex-1 overflow-hidden">
        <div className="relative flex flex-1 items-center justify-center p-6">
          {state ? (
            <GoBoard
              board={state.board}
              boardSize={state.boardSize}
              lastMove={state.lastMove}
              aiLastMove={state.aiLastMove}
              playerColor={state.playerColor}
              isMyTurn={!state.gameOver && state.currentPlayer === state.playerColor}
              gameOver={state.gameOver}
              hints={hints}
              showHints={showHints}
              onMove={handleMove}
              disabled={aiThinking || moveMut.isPending || hintsMut.isPending}
            />
          ) : (
            <div className="text-ink-dim">우측 패널에서 새 게임을 시작하세요</div>
          )}
          <ThinkingOverlay visible={aiThinking || newGame.isPending || hintsMut.isPending} />
        </div>

        <aside className="w-[320px] shrink-0 overflow-y-auto border-l border-[#2a2a4a] bg-sidebar p-5">
          {!state ? (
            <SetupPanel
              onStart={handleStart}
              loading={newGame.isPending}
              errorMessage={rateLimitMsg}
            />
          ) : (
            <GamePanel
              state={state}
              hintLoading={hintsMut.isPending}
              showHints={showHints}
              hints={hints}
              rateLimited={false}
              onHint={handleHint}
              onClearHints={handleClearHints}
              onPass={() => passMut.mutate()}
              onResign={() => resignMut.mutate()}
              onNewGame={handleNewGame}
            />
          )}
        </aside>
      </main>
    </div>
  );
}
