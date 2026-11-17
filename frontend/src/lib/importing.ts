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

export function dateToStartedAt(isoDate: string, now: Date = new Date()): string {
  const noon = new Date(`${isoDate}T12:00:00`)
  return (noon.getTime() > now.getTime() ? now : noon).toISOString()
}

export function workoutNotes(label: string, notes: string | null): string {
  return [label, notes].filter(Boolean).join(' — ').slice(0, NOTES_MAX)
}

export function selectedCounts(selection: ImportSelection) {
  const workouts = selection.workouts.filter((workout) => workout.include).length
  const weights = selection.weights.filter(Boolean).length
  const steps = selection.steps.filter(Boolean).length
  return { workouts, weights, steps, total: workouts + weights + steps }
}

export function workoutsMissingDate(selection: ImportSelection): number {
  return selection.workouts.filter((workout) => workout.include && !workout.date).length
}

export function toConfirmRequest(preview: ImportPreview, selection: ImportSelection, now: Date = new Date()): ImportConfirmRequest {
  return {
    workouts: preview.workouts
      .map((workout, index) => ({ workout, choice: selection.workouts[index] }))
      .filter(({ choice }) => choice.include)
      .map(({ workout, choice }) => ({
        startedAt: dateToStartedAt(choice.date, now),
        details: workout.details,
        notes: workoutNotes(workout.label, workout.notes),
      })),
    weights: preview.weights
      .filter((_, index) => selection.weights[index])
      .map((row) => ({ date: row.date, weightKg: row.weightKg })),
    steps: preview.steps
      .filter((_, index) => selection.steps[index])
      .map((row) => ({ date: row.date, steps: row.steps })),
  }
}
