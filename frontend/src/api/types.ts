export type ActivityType = 'RUNNING' | 'WEIGHTS' | 'WALKING' | 'AEROBIC'

export const ACTIVITY_TYPES: ActivityType[] = ['RUNNING', 'WEIGHTS', 'WALKING', 'AEROBIC']

export interface SignupRequest {
  email: string
  password: string
  firstName: string
  lastName: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface LoginResponse {
  token: string
}

export interface ActivityRequest {
  activityType: ActivityType
  startedAt: string
  duration: string
  notes: string | null
}

export interface ActivityResponse {
  id: number
  ownerId: number
  activityType: ActivityType
  startedAt: string
  duration: string
  notes: string | null
  createdAt: string
  updatedAt: string
}

export interface ShareRequest {
  email: string
}

export interface ShareResponse {
  id: number
  ownerId: number
  email: string
}

export type ValidationErrors = Record<string, string[]>
