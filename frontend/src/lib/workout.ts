import type { ActivityDetails, Exercise, WorkoutSet } from '../api/types'
import { isoToMinutes } from './duration'

export function setCount(details: ActivityDetails | null): number {
  return (details?.exerciseList ?? []).reduce((total, exercise) => total + (exercise.workoutSetList ?? []).length, 0)
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

function nonBlank(value: string | null | undefined): string | null {
  const trimmed = (value ?? '').trim()
  return trimmed === '' ? null : trimmed
}

export function formatTarget(exercise: Exercise): string | null {
  const sets =
    exercise.targetSets === null || exercise.targetSets === undefined
      ? null
      : `${exercise.targetSets} ${exercise.targetSets === 1 ? 'set' : 'sets'}`
  const repsText = nonBlank(exercise.targetReps)
  const reps = repsText === null ? null : `${repsText} reps`
  const rirText = nonBlank(exercise.repsInReserve)
  const rir = rirText === null ? null : `RIR ${rirText}`

  const volumePart = [sets, reps].filter((part): part is string => part !== null).join(' × ')
  const parts = [volumePart === '' ? null : volumePart, rir].filter((part): part is string => part !== null)
  if (parts.length === 0) return null
  return `Target: ${parts.join(' · ')}`
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
    value.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', hourCycle: 'h23', timeZone })
  return `${date} · ${time(start)} – ${time(end)}`
}
