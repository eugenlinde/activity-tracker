import type { ImportConfirmRequest, ImportPreview } from '../api/types'

const NOTES_MAX = 255

export interface ImportSelection {
  workouts: { include: boolean; date: string }[]
  weights: boolean[]
  steps: boolean[]
}

export function initialSelection(preview: ImportPreview): ImportSelection {
  return {
    workouts: preview.workouts.map((workout) => ({ include: !workout.alreadyImported, date: '' })),
    weights: preview.weights.map((row) => row.existingWeightKg === null),
    steps: preview.steps.map((row) => row.existingSteps === null),
  }
}

const CLOCK_SKEW_MS = 60_000

export function isFutureDate(isoDate: string, today: string): boolean {
  return isoDate > today
}

export function dateToStartedAt(isoDate: string, now: Date = new Date()): string {
  const noon = new Date(`${isoDate}T12:00:00`)
  const latest = new Date(now.getTime() - CLOCK_SKEW_MS)
  return (noon.getTime() > latest.getTime() ? latest : noon).toISOString()
}

export function workoutNotes(label: string): string {
  return label.slice(0, NOTES_MAX)
}

export function selectedCounts(selection: ImportSelection) {
  const workouts = selection.workouts.filter((workout) => workout.include).length
  const weights = selection.weights.filter(Boolean).length
  const steps = selection.steps.filter(Boolean).length
  return { workouts, weights, steps, total: workouts + weights + steps }
}

export function workoutDateProblem(choice: { include: boolean; date: string }, today: string): 'missing' | 'future' | null {
  if (!choice.include) return null
  if (!choice.date) return 'missing'
  return isFutureDate(choice.date, today) ? 'future' : null
}

export function workoutsWithDateProblem(selection: ImportSelection, today: string): number {
  return selection.workouts.filter((workout) => workoutDateProblem(workout, today) !== null).length
}

export function toConfirmRequest(preview: ImportPreview, selection: ImportSelection, now: Date = new Date()): ImportConfirmRequest {
  return {
    workouts: preview.workouts
      .map((workout, index) => ({ workout, choice: selection.workouts[index] }))
      .filter(({ choice }) => choice.include)
      .map(({ workout, choice }) => ({
        startedAt: dateToStartedAt(choice.date, now),
        details: workout.details,
        notes: workoutNotes(workout.label),
      })),
    weights: preview.weights
      .filter((_, index) => selection.weights[index])
      .map((row) => ({ date: row.date, weightKg: row.weightKg })),
    steps: preview.steps
      .filter((_, index) => selection.steps[index])
      .map((row) => ({ date: row.date, steps: row.steps })),
  }
}
