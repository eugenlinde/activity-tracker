import { http } from './client'
import type { LoginRequest, LoginResponse, SignupRequest } from './types'

export const authApi = {
  signup: (body: SignupRequest) => http.post<void>('/api/auth/signup', body),
  login: (body: LoginRequest) => http.post<LoginResponse>('/api/auth/login', body),
}
