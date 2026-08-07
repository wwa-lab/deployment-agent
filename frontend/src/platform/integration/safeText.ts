const namedSecret = /(\b(?:authorization|access[_-]?token|api[_-]?token|refresh[_-]?token|id[_-]?token|token|password|passwd|secret|client[_-]?secret)\b\s*[:=]\s*)(?:Bearer\s+)?(?:"[^"\r\n]*"|'[^'\r\n]*'|[^\s,;]+)/gi
const bearerSecret = /(\bBearer\s+)[A-Za-z0-9._~+/=-]{8,}/gi
const jwtSecret = /\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b/g
const knownSecret = /\b(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|sk-[A-Za-z0-9_-]{16,}|AKIA[0-9A-Z]{16})\b/g
const privateKey = /-----BEGIN [A-Z ]*PRIVATE KEY-----[\s\S]*/gi

export function safeOperationalText(value?: string, maximumLength = 480): string {
  if (!value) return ''

  const redacted = value
    .replace(privateKey, '[REDACTED]')
    .replace(namedSecret, '$1[REDACTED]')
    .replace(bearerSecret, '$1[REDACTED]')
    .replace(jwtSecret, '[REDACTED]')
    .replace(knownSecret, '[REDACTED]')
    .replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/g, '')
    .trim()

  return redacted.length <= maximumLength
    ? redacted
    : `${redacted.slice(0, maximumLength)}…`
}
