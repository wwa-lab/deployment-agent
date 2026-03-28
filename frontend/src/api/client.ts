import axios from 'axios'
import router from '../router'
import { useUserStore } from '../stores/user'

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
      // Clear stale auth state so the router guard sends the user to login
      const userStore = useUserStore()
      userStore.isAuthenticated = false
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
