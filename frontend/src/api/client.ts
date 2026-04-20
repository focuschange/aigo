// REST API 클라이언트. 개발 환경은 Vite 프록시, 프로덕션은 동일 오리진.
//
// CSRF 보호(Spring Security + CookieCsrfTokenRepository): 서버가 내려주는
// XSRF-TOKEN 쿠키 값을 POST/PUT/DELETE 시 X-XSRF-TOKEN 헤더로 되돌려준다.
// 쿠키는 첫 요청(주로 GET /api/game/{id})에서 발급되며, credentials: 'same-origin'
// 으로 동일 오리진에 한해 자동 전송된다.

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

/** document.cookie 에서 지정한 이름의 쿠키 값을 꺼낸다. 없으면 null. */
function readCookie(name: string): string | null {
  if (typeof document === 'undefined') return null;
  const prefix = `${name}=`;
  const parts = document.cookie.split(';');
  for (const raw of parts) {
    const c = raw.trim();
    if (c.startsWith(prefix)) {
      return decodeURIComponent(c.substring(prefix.length));
    }
  }
  return null;
}

/** 상태 변경 메서드만 CSRF 토큰이 필요하다. */
const UNSAFE_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

async function request<T>(path: string, init: RequestInit): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(init.headers as Record<string, string> | undefined),
  };

  if (UNSAFE_METHODS.has(method)) {
    let csrf = readCookie('XSRF-TOKEN');
    if (!csrf) {
      // SPA 첫 상호작용이 바로 POST 인 경우 쿠키가 아직 없을 수 있다.
      // 가벼운 priming GET 으로 XSRF-TOKEN 쿠키를 받아온 뒤 본 요청을 진행한다.
      await fetch('/api/csrf', { method: 'GET', credentials: 'same-origin' }).catch(() => {});
      csrf = readCookie('XSRF-TOKEN');
    }
    if (csrf) headers['X-XSRF-TOKEN'] = csrf;
  }

  const res = await fetch(path, {
    credentials: 'same-origin',
    ...init,
    headers,
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
