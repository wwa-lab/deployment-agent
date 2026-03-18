import axios from 'axios'
import { useUserStore } from '../stores/user'

const apiClient = axios.create({
  baseURL: '/api/deployment-agent',
  headers: {
    'Content-Type': 'application/json',
  },
})

apiClient.interceptors.request.use((config) => {
  // Use try/catch because store may not be initialized yet during app bootstrap
  try {
    const userStore = useUserStore()
    config.headers['X-User-Id'] = userStore.userId
    config.headers['X-User-Role'] = userStore.role
  } catch {
    config.headers['X-User-Id'] = 'dev-user'
    config.headers['X-User-Role'] = 'TL'
  }
  return config
})

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const message =
      error.response?.data?.message ||
      error.response?.data?.error ||
      error.message ||
      'An unexpected error occurred'
    return Promise.reject(new Error(message))
  }
)

export default apiClient
