import platformClient from './platformClient'
import type { AuthResponse } from '../types'

export async function login(employeeId: string, password: string): Promise<AuthResponse> {
  const response = await platformClient.post('/auth/login', { employeeId, password })
  return response.data
}

export async function logout(): Promise<void> {
  await platformClient.post('/auth/logout')
}

export async function checkSession(): Promise<AuthResponse> {
  const response = await platformClient.get('/auth/me')
  return response.data
}
