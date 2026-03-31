import axios from 'axios'
import router from '../router'

const testingAgentClient = axios.create({
  baseURL: '/api/testing-agent',
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true,
})

testingAgentClient.interceptors.response.use(
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
  }
)

export default testingAgentClient
