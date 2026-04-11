import platformClient from './platformClient'

/**
 * Platform-level XLSX template download.
 *
 * Previously exposed from each agent's api.ts (deployment/testing/build) but
 * every implementation delegated to the same `/api/platform/upload/template`
 * endpoint. Living here makes the platform ownership obvious and removes the
 * hardcoded `agents/deployment/api` imports from shared dialogs.
 */
export async function downloadTemplate(): Promise<Blob> {
  const response = await platformClient.get('/upload/template', {
    responseType: 'blob',
  })
  return response.data
}
