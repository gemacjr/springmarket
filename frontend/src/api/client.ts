const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080').replace(/\/$/, '')
const ACCESS_TOKEN_KEY = 'springmarket.accessToken'
const REFRESH_TOKEN_KEY = 'springmarket.refreshToken'
const SESSION_KEY = 'springmarket.session'
export const AUTH_UNAUTHORIZED_EVENT = 'springmarket:unauthorized'

let accessToken = localStorage.getItem(ACCESS_TOKEN_KEY)
let refreshRequest: Promise<boolean> | null = null

export interface ApiProblem {
  detail?: string
  title?: string
  status?: number
}

export class ApiError extends Error {
  readonly status: number

  constructor(
    message: string,
    status: number,
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

export function setAuthTokens(nextAccessToken: string, refreshToken: string) {
  accessToken = nextAccessToken
  localStorage.setItem(ACCESS_TOKEN_KEY, nextAccessToken)
  localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
}

export function getRefreshToken() {
  return localStorage.getItem(REFRESH_TOKEN_KEY)
}

export function clearAuthTokens() {
  accessToken = null
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
}

async function refreshAuthTokens(): Promise<boolean> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) return false

  const response = await fetch(`${API_BASE_URL}/api/v1/auth/refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken }),
  })
  if (!response.ok) return false

  const session = (await response.json()) as {
    accessToken: string
    refreshToken: string
    [key: string]: unknown
  }
  setAuthTokens(session.accessToken, session.refreshToken)
  localStorage.setItem(SESSION_KEY, JSON.stringify(session))
  return true
}

async function tryRefreshAuthTokens(): Promise<boolean> {
  refreshRequest ??= refreshAuthTokens().finally(() => {
    refreshRequest = null
  })
  return refreshRequest
}

export async function apiRequest<T>(
  path: string,
  init: RequestInit = {},
  allowRefresh = true,
): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  if (accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`)
  }

  const response = await fetch(`${API_BASE_URL}${path}`, { ...init, headers })

  if (
    response.status === 401
    && allowRefresh
    && !path.startsWith('/api/v1/auth/')
    && await tryRefreshAuthTokens()
  ) {
    return apiRequest<T>(path, init, false)
  }

  if (response.status === 401) {
    clearAuthTokens()
    localStorage.removeItem(SESSION_KEY)
    window.dispatchEvent(new Event(AUTH_UNAUTHORIZED_EVENT))
    if (window.location.pathname !== '/login') {
      window.location.assign('/login')
    }
  }

  if (!response.ok) {
    let problem: ApiProblem | undefined
    try {
      problem = (await response.json()) as ApiProblem
    } catch {
      // The fallback below covers empty and non-JSON error responses.
    }
    throw new ApiError(
      problem?.detail ?? problem?.title ?? `Request failed with status ${response.status}`,
      response.status,
    )
  }

  if (response.status === 204) {
    return undefined as T
  }

  return response.json() as Promise<T>
}
