/** JWT storage key; legacy `architect_token` is read once and migrated on login. */
export const AUTH_TOKEN_KEY = 'zerqis_token'
const LEGACY_AUTH_TOKEN_KEY = 'architect_token'

export function getAuthToken(): string | null {
  const current = localStorage.getItem(AUTH_TOKEN_KEY)
  if (current) return current
  return localStorage.getItem(LEGACY_AUTH_TOKEN_KEY)
}

export function setAuthToken(token: string): void {
  localStorage.setItem(AUTH_TOKEN_KEY, token)
  localStorage.removeItem(LEGACY_AUTH_TOKEN_KEY)
}

export function clearAuthToken(): void {
  localStorage.removeItem(AUTH_TOKEN_KEY)
  localStorage.removeItem(LEGACY_AUTH_TOKEN_KEY)
}
