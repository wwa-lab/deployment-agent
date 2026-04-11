import axios from 'axios'
import router from '../router'

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

platformClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      const currentPath = window.location.pathname
      if (currentPath !== '/login') {
        router.push('/login')
      }
    }
    const message =
      error.response?.data?.message ||
      error.response?.data?.error ||
      error.message ||
      'An unexpected error occurred'
    return Promise.reject(new Error(message))
  },
)

export default platformClient
