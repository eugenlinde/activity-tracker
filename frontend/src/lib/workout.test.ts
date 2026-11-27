import { describe, expect, it } from 'vitest'
import type { ActivityDetails, Exercise, WorkoutSet } from '../api/types'
import {
  formatExerciseIndex,
  formatSetChip,
  formatTarget,
  formatVolume,
  formatWorkoutWhen,
  setCount,
  workoutVolume,
} from './workout'

function exercise(overrides: Partial<Exercise> = {}): Exercise {
  return {
    name: 'Squat',
    targetReps: null,
    repsInReserve: null,
    targetSets: null,
    comment: null,
    videoUrl: null,
    workoutSetList: [],
    ...overrides,
  }
}

function details(...exercises: Exercise[]): ActivityDetails {
  return { version: '1', exerciseList: exercises }
}

function sets(...pairs: [number | null, number | null][]): WorkoutSet[] {
  return pairs.map(([weight, reps]) => ({ weight, reps }))
}

describe('workoutVolume and setCount', () => {
  it('give 0 for null details, empty list and null set list', () => {
    expect(workoutVolume(null)).toBe(0)
    expect(setCount(null)).toBe(0)
    expect(workoutVolume({ version: '1', exerciseList: [] })).toBe(0)
    expect(setCount({ version: '1', exerciseList: [] })).toBe(0)
    expect(workoutVolume({ version: '1', exerciseList: null })).toBe(0)
    expect(workoutVolume(details(exercise({ workoutSetList: null })))).toBe(0)
    expect(setCount(details(exercise({ workoutSetList: null })))).toBe(0)
  })

  it('counts sets missing weight or reps but adds nothing to volume', () => {
    const d = details(exercise({ workoutSetList: sets([80, 8], [null, 8], [80, null], [null, null]) }))
    expect(setCount(d)).toBe(4)
    expect(workoutVolume(d)).toBe(640)
  })

  it('adds 0 for weight 0', () => {
    expect(workoutVolume(details(exercise({ workoutSetList: sets([0, 10]) })))).toBe(0)
  })

  it('handles decimal weights', () => {
    expect(workoutVolume(details(exercise({ workoutSetList: sets([77.5, 8]) })))).toBe(620)
  })

  it('sums several exercises', () => {
    const d = details(
      exercise({ workoutSetList: sets([100, 5], [100, 5]) }),
      exercise({ workoutSetList: sets([50, 10]) }),
    )
    expect(workoutVolume(d)).toBe(1500)
    expect(setCount(d)).toBe(3)
  })
})

describe('formatVolume', () => {
  it('rounds and separates thousands', () => {
    expect(formatVolume(0)).toBe('0')
    expect(formatVolume(9420)).toBe('9,420')
    expect(formatVolume(1234.5)).toBe('1,235')
    expect(formatVolume(620)).toBe('620')
  })
})

describe('formatTarget', () => {
  it('shows all three parts', () => {
    expect(formatTarget(exercise({ targetSets: 4, targetReps: '8', repsInReserve: '2' }))).toBe(
      'Target: 4 sets × 8 reps · RIR 2',
    )
  })

  it('shows each part alone', () => {
    expect(formatTarget(exercise({ targetSets: 4 }))).toBe('Target: 4 sets')
    expect(formatTarget(exercise({ targetReps: '8' }))).toBe('Target: 8 reps')
    expect(formatTarget(exercise({ repsInReserve: '2' }))).toBe('Target: RIR 2')
  })

  it('shows each pair', () => {
    expect(formatTarget(exercise({ targetSets: 4, targetReps: '8' }))).toBe('Target: 4 sets × 8 reps')
    expect(formatTarget(exercise({ targetSets: 4, repsInReserve: '2' }))).toBe('Target: 4 sets · RIR 2')
    expect(formatTarget(exercise({ targetReps: '8', repsInReserve: '2' }))).toBe('Target: 8 reps · RIR 2')
  })

  it('returns null when nothing exists or values are blank', () => {
    expect(formatTarget(exercise())).toBeNull()
    expect(formatTarget(exercise({ targetReps: '  ', repsInReserve: '' }))).toBeNull()
  })

  it('reads one set as "1 set"', () => {
    expect(formatTarget(exercise({ targetSets: 1, targetReps: '8' }))).toBe('Target: 1 set × 8 reps')
  })

  it('keeps free text reps', () => {
    expect(formatTarget(exercise({ targetReps: '10-15' }))).toBe('Target: 10-15 reps')
  })
})

describe('formatSetChip', () => {
  it('formats weight and reps', () => {
    expect(formatSetChip({ weight: 80, reps: 8 })).toBe('80 × 8')
    expect(formatSetChip({ weight: 77.5, reps: 8 })).toBe('77.5 × 8')
  })

  it('shows a dash for a missing part', () => {
    expect(formatSetChip({ weight: null, reps: 8 })).toBe('— × 8')
    expect(formatSetChip({ weight: 80, reps: null })).toBe('80 × —')
  })
})

describe('formatExerciseIndex', () => {
  it('is zero-based in and two digits out', () => {
    expect(formatExerciseIndex(0)).toBe('01')
    expect(formatExerciseIndex(11)).toBe('12')
  })
})

describe('formatWorkoutWhen', () => {
  it('shows date with start and end time', () => {
    expect(formatWorkoutWhen('2026-10-22T18:05:00Z', 'PT1H2M', 'UTC')).toBe(
      'Thursday, 22 October 2026 · 18:05 – 19:07',
    )
  })

  it('shows only the date without duration', () => {
    expect(formatWorkoutWhen('2026-10-22T18:05:00Z', null, 'UTC')).toBe('Thursday, 22 October 2026')
  })
})
