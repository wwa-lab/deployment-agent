/**
 * Platform-level configuration constants.
 *
 * FINBLOCK_URL: the URL users are sent to when they click "Return to FinBlock".
 * Override via VITE_FINBLOCK_URL environment variable in production.
 *
 * AI_ASSIST_PREVIEW_ENABLED: gates the visual-only "AI Assist (Preview)" placeholder
 * rendered in decision and task-edit dialogs. No real suggestions are produced yet —
 * this exists so the hook is wired before Phase-A AI advisor is implemented.
 * Disable via VITE_AI_ASSIST_PREVIEW=false.
 */
const env = (import.meta as unknown as { env: Record<string, string> }).env

export const FINBLOCK_URL: string = env.VITE_FINBLOCK_URL ?? '#'

export const AI_ASSIST_PREVIEW_ENABLED: boolean =
  (env.VITE_AI_ASSIST_PREVIEW ?? 'true').toLowerCase() !== 'false'
