import type { ActivityDetails, Exercise, WorkoutSet } from '../api/types'

export const DETAILS_VERSION = '1'
export const MAX_EXERCISES = 12
export const MAX_SETS = 12

export interface SetDraft {
  weight: string
  reps: string
}

export interface ExerciseDraft {
  name: string
  targetReps: string
  repsInReserve: string
  comment: string
  videoUrl: string
  sets: SetDraft[]
}

export function emptySet(): SetDraft {
  return { weight: '', reps: '' }
}

export function emptyExercise(): ExerciseDraft {
  return { name: '', targetReps: '', repsInReserve: '', comment: '', videoUrl: '', sets: [emptySet()] }
}

function textOrEmpty(value: string | null): string {
  return value ?? ''
}

function numberOrEmpty(value: number | null): string {
  return value === null ? '' : String(value)
}

function emptyToNull(value: string): string | null {
  const trimmed = value.trim()
  return trimmed === '' ? null : trimmed
}

function parseNumber(value: string): number | null {
  const trimmed = value.trim()
  return trimmed === '' ? null : Number(trimmed)
}

export function toDrafts(details: ActivityDetails | null): ExerciseDraft[] {
  return (details?.exerciseList ?? []).map((exercise) => ({
    name: exercise.name,
    targetReps: textOrEmpty(exercise.targetReps),
    repsInReserve: textOrEmpty(exercise.repsInReserve),
    comment: textOrEmpty(exercise.comment),
    videoUrl: textOrEmpty(exercise.videoUrl),
    sets: (exercise.workoutSetList ?? []).map((set) => ({
      weight: numberOrEmpty(set.weight),
      reps: numberOrEmpty(set.reps),
    })),
  }))
}

export function fromDrafts(drafts: ExerciseDraft[]): ActivityDetails | null {
  if (drafts.length === 0) return null
  const exerciseList: Exercise[] = drafts.map((draft) => ({
    name: draft.name.trim(),
    targetReps: emptyToNull(draft.targetReps),
    repsInReserve: emptyToNull(draft.repsInReserve),
    comment: emptyToNull(draft.comment),
    videoUrl: emptyToNull(draft.videoUrl),
    workoutSetList: draft.sets
      .map<WorkoutSet>((set) => ({ weight: parseNumber(set.weight), reps: parseNumber(set.reps) }))
      .filter((set) => set.weight !== null || set.reps !== null),
  }))
  return { version: DETAILS_VERSION, exerciseList }
}

export function formatSet(set: WorkoutSet): string {
  const weight = set.weight === null ? '—' : `${set.weight} kg`
  const reps = set.reps === null ? '—' : String(set.reps)
  return `${weight} × ${reps}`
}

export function exerciseCount(details: ActivityDetails | null): number {
  return details?.exerciseList?.length ?? 0
}
