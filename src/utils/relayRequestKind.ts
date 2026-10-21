/** Use the recorded task classification, with an endpoint fallback for older logs. */
export function isCompactionRequest(endpoint?: string | null, contextCompaction?: boolean | null): boolean {
  if (contextCompaction === true) return true
  const path = (endpoint || '').trim().split(/[?#]/, 1)[0] || ''
  return /(?:^|\/)responses\/compact\/?$/.test(path)
}
