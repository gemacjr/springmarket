const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080').replace(/\/$/, '')
const ACCESS_TOKEN_KEY = 'springmarket.accessToken'
const REFRESH_TOKEN_KEY = 'springmarket.refreshToken'

let accessToken = localStorage.getItem(ACCESS_TOKEN_KEY)

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

export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  if (accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`)
  }

  const response = await fetch(`${API_BASE_URL}${path}`, { ...init, headers })

  if (response.status === 401) {
    clearAuthTokens()
    localStorage.removeItem('springmarket.session')
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
