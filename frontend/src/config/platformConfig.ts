/**
 * Platform-level configuration constants.
 *
 * FINBLOCK_URL: the URL users are sent to when they click "Return to FinBlock".
 * Override via VITE_FINBLOCK_URL environment variable in production.
 */
export const FINBLOCK_URL: string =
  (import.meta as unknown as { env: Record<string, string> }).env.VITE_FINBLOCK_URL ?? '#'
