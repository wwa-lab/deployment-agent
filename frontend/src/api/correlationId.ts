import type { AxiosInstance, AxiosResponse, InternalAxiosRequestConfig } from 'axios'

/**
 * Frontend correlation-ID interceptor.
 *
 * Stamps every outbound request with an `X-Correlation-Id` header that
 * matches the value the backend's `CorrelationIdFilter` expects. The backend
 * echoes the header back on every response; this module reads it off both
 * successful and failed responses so it can be surfaced to the user (for
 * example, in an error toast: "something went wrong — reference abc123").
 *
 * Intentionally kept small and dependency-free:
 *   - IDs are short URL-safe hex strings (16 chars, ~64 bits of entropy),
 *     the same shape the backend generates when no client ID is supplied.
 *   - IDs are per-request, not per-session. Each request carries a fresh ID
 *     so log correlation aligns with one user action rather than one login.
 *   - The last-observed correlation ID from a server response is cached in
 *     `lastSeenCorrelationId()` so UI error handlers can display it without
 *     needing to plumb it through every call site.
 */

const HEADER = 'X-Correlation-Id'

let lastSeen: string | null = null

function randomHex(length: number): string {
  const cryptoObj = globalThis.crypto
  if (cryptoObj?.getRandomValues) {
    const bytes = new Uint8Array(Math.ceil(length / 2))
    cryptoObj.getRandomValues(bytes)
    return Array.from(bytes, (b) => b.toString(16).padStart(2, '0'))
      .join('')
      .slice(0, length)
  }
  // Fallback for older runtimes: Math.random is NOT cryptographically
  // secure, but correlation IDs are not a security boundary — the backend
  // still sanitizes incoming IDs and generates its own when missing.
  let out = ''
  while (out.length < length) {
    out += Math.random().toString(16).slice(2)
  }
  return out.slice(0, length)
}

function newCorrelationId(): string {
  return randomHex(16)
}

function rememberFromResponse(response: AxiosResponse | undefined): void {
  const headerValue = response?.headers?.[HEADER.toLowerCase()]
  if (typeof headerValue === 'string' && headerValue.length > 0) {
    lastSeen = headerValue
  }
}

/**
 * Installs the correlation interceptor on the given axios instance.
 * Call this once per client at creation time.
 */
export function installCorrelationIdInterceptor(client: AxiosInstance): void {
  client.interceptors.request.use((config: InternalAxiosRequestConfig) => {
    if (!config.headers) {
      return config
    }
    if (!config.headers[HEADER]) {
      config.headers[HEADER] = newCorrelationId()
    }
    return config
  })

  client.interceptors.response.use(
    (response) => {
      rememberFromResponse(response)
      return response
    },
    (error) => {
      rememberFromResponse(error?.response)
      return Promise.reject(error)
    },
  )
}

/**
 * Returns the most recently observed correlation ID from the server, or
 * null if no request has completed yet. Useful in global error toasts.
 */
export function lastSeenCorrelationId(): string | null {
  return lastSeen
}
