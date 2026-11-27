import type { ActivityDetails, Exercise, WorkoutSet } from '../api/types'
import { isoToMinutes } from './duration'

export function setCount(details: ActivityDetails | null): number {
  return (details?.exerciseList ?? []).reduce((sum, exercise) => sum + (exercise.workoutSetList ?? []).length, 0)
}

export function workoutVolume(details: ActivityDetails | null): number {
  let volume = 0
  for (const exercise of details?.exerciseList ?? []) {
    for (const set of exercise.workoutSetList ?? []) {
      if (set.weight !== null && set.reps !== null) volume += set.weight * set.reps
    }
  }
  return volume
}

export function formatVolume(volume: number): string {
  return Math.round(volume).toLocaleString('en-GB')
}

function present(text: string | null | undefined): string | null {
  const trimmed = text?.trim() ?? ''
  return trimmed === '' ? null : trimmed
}

export function formatTarget(exercise: Exercise): string | null {
  const sets = exercise.targetSets === null || exercise.targetSets === undefined ? null : exercise.targetSets
  const reps = present(exercise.targetReps)
  const rir = present(exercise.repsInReserve)

  const volumePart: string[] = []
  if (sets !== null) volumePart.push(sets === 1 ? '1 set' : `${sets} sets`)
  if (reps !== null) volumePart.push(`${reps} reps`)

  const parts: string[] = []
  if (volumePart.length > 0) parts.push(volumePart.join(' × '))
  if (rir !== null) parts.push(`RIR ${rir}`)

  return parts.length === 0 ? null : `Target: ${parts.join(' · ')}`
}

export function formatSetChip(set: WorkoutSet): string {
  const weight = set.weight === null ? '—' : String(set.weight)
  const reps = set.reps === null ? '—' : String(set.reps)
  return `${weight} × ${reps}`
}

export function formatExerciseIndex(index: number): string {
  return String(index + 1).padStart(2, '0')
}

export function formatWorkoutWhen(startedAt: string, duration: string | null, timeZone?: string): string {
  const start = new Date(startedAt)
  const weekday = start.toLocaleDateString('en-GB', { weekday: 'long', timeZone })
  const rest = start.toLocaleDateString('en-GB', { day: 'numeric', month: 'long', year: 'numeric', timeZone })
  const date = `${weekday}, ${rest}`
  if (duration === null) return date

  const end = new Date(start.getTime() + isoToMinutes(duration) * 60_000)
  const time = (value: Date) =>
    value.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', hour12: false, timeZone })
  return `${date} · ${time(start)} – ${time(end)}`
}
