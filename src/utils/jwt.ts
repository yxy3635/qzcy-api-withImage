const EXPIRY_LEEWAY_MS = 5_000

export function tokenExpired(token: string): boolean {
  if (!token) return true
  const parts = token.split('.')
  const payloadPart = parts.length === 3 ? parts[1] : ''
  if (!payloadPart) return false
  try {
    const normalized = payloadPart.replace(/-/g, '+').replace(/_/g, '/')
    const padded = normalized + '='.repeat((4 - (normalized.length % 4)) % 4)
    const payload = JSON.parse(window.atob(padded))
    if (!payload || typeof payload.exp !== 'number') return false
    return payload.exp * 1000 <= Date.now() + EXPIRY_LEEWAY_MS
  } catch {
    return false
  }
}
