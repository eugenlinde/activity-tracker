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

export interface WorkoutSet {
  weight: number | null
  reps: number | null
}

export interface Exercise {
  name: string
  targetReps: string | null
  repsInReserve: string | null
  comment: string | null
  videoUrl: string | null
  workoutSetList: WorkoutSet[] | null
}

export interface ActivityDetails {
  version: string
  exerciseList: Exercise[] | null
}

export interface ActivityRequest {
  activityType: ActivityType
  startedAt: string
  duration: string | null
  details: ActivityDetails | null
  notes: string | null
}

export interface ActivityResponse {
  id: number
  ownerId: number
  activityType: ActivityType
  startedAt: string
  duration: string | null
  details: ActivityDetails | null
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

export interface MediaResponse {
  id: number
}
