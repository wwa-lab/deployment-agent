import axios from 'axios'
import router from '../router'
import { installCorrelationIdInterceptor } from './correlationId'

/**
 * Platform-level API client — used for capability endpoints shared across all agents:
 * auth, audit logs, access grants, configuration, template download.
 *
 * Introduced in BA-T16 to decouple these endpoints from the Deployment Agent prefix.
 */
const platformClient = axios.create({
  baseURL: '/api/platform',
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true,
})

installCorrelationIdInterceptor(platformClient)

platformClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      const currentPath = window.location.pathname
      if (currentPath !== '/login') {
        router.push('/login')
      }
    }
    const summary =
      error.response?.data?.message ||
      error.response?.data?.error ||
      error.message ||
      'An unexpected error occurred'
    const details =
      typeof error.response?.data?.details === 'string' && error.response.data.details.trim()
        ? error.response.data.details.trim()
        : ''
    const message = details && details !== summary ? `${summary} ${details}` : summary
    return Promise.reject(new Error(message))
  },
)

export default platformClient
