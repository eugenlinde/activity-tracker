export type ActivityType =
  | 'RUNNING'
  | 'WEIGHTS'
  | 'WALKING'
  | 'AEROBIC'
  | 'CYCLING'
  | 'SWIMMING'
  | 'ROWING'
  | 'TREADMILL'

export const ACTIVITY_TYPES: ActivityType[] = [
  'RUNNING',
  'WEIGHTS',
  'WALKING',
  'AEROBIC',
  'CYCLING',
  'SWIMMING',
  'ROWING',
  'TREADMILL',
]

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

export interface WeightEntry {
  date: string
  weightKg: number
}

export interface StepsGoal {
  goal: number
}

export interface StepsEntry {
  date: string
  steps: number
}

export type ImportKind = 'WORKOUT_GRID' | 'TABLE' | 'UNKNOWN'

export interface SkippedEntry {
  row: number
  workout: string
  exercise: string | null
}

export interface WorkoutPreview {
  label: string
  details: ActivityDetails
  alreadyImported: boolean
}

export interface WeightPreview {
  date: string
  weightKg: number
  existingWeightKg: number | null
}

export interface StepsPreview {
  date: string
  steps: number
  existingSteps: number | null
}

export interface ImportPreview {
  kind: ImportKind
  workouts: WorkoutPreview[]
  weights: WeightPreview[]
  steps: StepsPreview[]
  unchangedWeights: number
  unchangedSteps: number
  unreadableRows: number
  skippedEntries: SkippedEntry[]
  assumedYear: number | null
}

export interface ImportConfirmRequest {
  workouts: { startedAt: string; details: ActivityDetails; notes: string | null }[]
  weights: { date: string; weightKg: number }[]
  steps: { date: string; steps: number }[]
}

export interface ImportResult {
  workouts: number
  weights: number
  steps: number
}
