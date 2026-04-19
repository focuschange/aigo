// TanStack Query 훅. 컴포넌트에서 서버 상태를 다룰 때 이 훅만 사용한다.

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './client';
import { STONE, type GameState, type HintMove, type MoveRequest, type NewGameRequest, type Stone } from '../types';

const gameKey = (gameId: string | null) => ['game', gameId] as const;

export function useGameState(gameId: string | null) {
  return useQuery<GameState>({
    queryKey: gameKey(gameId),
    queryFn: () => api.state(gameId!),
    enabled: !!gameId,
    staleTime: Infinity, // 서버 응답은 mutation 들이 직접 갱신한다
  });
}

export function useNewGame(onSuccess: (s: GameState) => void) {
  const qc = useQueryClient();
  return useMutation<GameState, Error, NewGameRequest>({
    mutationFn: api.newGame,
    onSuccess: (data) => {
      qc.setQueryData(gameKey(data.gameId), data);
      onSuccess(data);
    },
  });
}

export function usePlayerMove(gameId: string | null) {
  const qc = useQueryClient();
  return useMutation<GameState, Error, MoveRequest, { prev: GameState | undefined }>({
    mutationFn: (req) => api.move(gameId!, req),
    // 낙관적 업데이트: 서버 응답 전 플레이어 돌 즉시 표시
    onMutate: async ({ row, col }) => {
      if (!gameId) return { prev: undefined };
      await qc.cancelQueries({ queryKey: gameKey(gameId) });
      const prev = qc.getQueryData<GameState>(gameKey(gameId));
      if (prev) {
        const optimistic: GameState = {
          ...prev,
          board: prev.board.map((r) => [...r]),
          lastMove: [row, col],
          aiLastMove: null,
          aiThinking: true,
        };
        optimistic.board[row][col] = prev.playerColor === 'BLACK' ? STONE.BLACK : STONE.WHITE;
        qc.setQueryData(gameKey(gameId), optimistic);
      }
      return { prev };
    },
    onError: (_err, _vars, ctx) => {
      if (ctx?.prev && gameId) qc.setQueryData(gameKey(gameId), ctx.prev);
    },
    onSuccess: (data) => {
      qc.setQueryData(gameKey(data.gameId), data);
    },
  });
}

export function usePass(gameId: string | null) {
  const qc = useQueryClient();
  return useMutation<GameState, Error, void>({
    mutationFn: () => api.pass(gameId!),
    onSuccess: (data) => qc.setQueryData(gameKey(data.gameId), data),
  });
}

export function useResign(gameId: string | null) {
  const qc = useQueryClient();
  return useMutation<GameState, Error, void>({
    mutationFn: () => api.resign(gameId!),
    onSuccess: (data) => qc.setQueryData(gameKey(data.gameId), data),
  });
}

export function useHints(gameId: string | null) {
  return useMutation<HintMove[], Error, void>({
    mutationFn: () => api.hints(gameId!),
  });
}

// 도우미 타입 재export
export type { GameState, HintMove, Stone };
