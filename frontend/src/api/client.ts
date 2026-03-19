import axios from 'axios'
import router from '../router'

const apiClient = axios.create({
  baseURL: '/api/deployment-agent',
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true,
})

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      // Redirect to login on 401 (session expired or not authenticated)
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
  }
)

export default apiClient
