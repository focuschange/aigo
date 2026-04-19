// REST API 클라이언트. 개발 환경은 Vite 프록시, 프로덕션은 동일 오리진.

import type { GameState, HintMove, MoveRequest, NewGameRequest } from '../types';

export class ApiError extends Error {
  readonly status: number;
  readonly retryAfter?: number;

  constructor(status: number, message: string, retryAfter?: number) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.retryAfter = retryAfter;
  }
}

async function request<T>(path: string, init: RequestInit): Promise<T> {
  const res = await fetch(path, {
    headers: { 'Content-Type': 'application/json', ...(init.headers ?? {}) },
    ...init,
  });
  if (!res.ok) {
    const text = await res.text().catch(() => '');
    const retryAfter = res.headers.get('Retry-After');
    throw new ApiError(
      res.status,
      text || `HTTP ${res.status}`,
      retryAfter ? Number(retryAfter) : undefined,
    );
  }
  return (await res.json()) as T;
}

export const api = {
  newGame(req: NewGameRequest) {
    return request<GameState>('/api/game/new', {
      method: 'POST',
      body: JSON.stringify(req),
    });
  },
  move(gameId: string, req: MoveRequest) {
    return request<GameState>(`/api/game/${gameId}/move`, {
      method: 'POST',
      body: JSON.stringify(req),
    });
  },
  pass(gameId: string) {
    return request<GameState>(`/api/game/${gameId}/pass`, { method: 'POST' });
  },
  resign(gameId: string) {
    return request<GameState>(`/api/game/${gameId}/resign`, { method: 'POST' });
  },
  state(gameId: string) {
    return request<GameState>(`/api/game/${gameId}`, { method: 'GET' });
  },
  hints(gameId: string) {
    return request<HintMove[]>(`/api/game/${gameId}/hints`, { method: 'GET' });
  },
};
