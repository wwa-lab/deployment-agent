import apiClient from './client'
import type { AuthResponse } from '../types'

export async function login(employeeId: string, password: string): Promise<AuthResponse> {
  const response = await apiClient.post('/auth/login', { employeeId, password })
  return response.data
}

export async function logout(): Promise<void> {
  await apiClient.post('/auth/logout')
}

export async function checkSession(): Promise<AuthResponse> {
  const response = await apiClient.get('/auth/me')
  return response.data
}
