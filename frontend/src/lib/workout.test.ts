import { describe, expect, it } from 'vitest'
import type { ActivityDetails, Exercise } from '../api/types'
import { ApiError } from '../api/client'
import {
  isNotFoundError,
  isWorkoutPageType,
  parseWorkoutId,
  formatExerciseIndex,
  formatSetChip,
  formatTarget,
  formatVolume,
  formatWorkoutWhen,
  setCount,
  workoutVolume,
} from './workout'

function exercise(patch: Partial<Exercise> = {}): Exercise {
  return {
    name: 'Squat',
    targetReps: null,
    repsInReserve: null,
    targetSets: null,
    comment: null,
    videoUrl: null,
    workoutSetList: [],
    ...patch,
  }
}

function details(...exercises: Exercise[]): ActivityDetails {
  return { version: '1', exerciseList: exercises }
}

describe('workoutVolume and setCount', () => {
  it('give 0 for null details, no exercises and null set lists', () => {
    expect(workoutVolume(null)).toBe(0)
    expect(setCount(null)).toBe(0)
    expect(workoutVolume({ version: '1', exerciseList: [] })).toBe(0)
    expect(setCount({ version: '1', exerciseList: [] })).toBe(0)
    expect(workoutVolume({ version: '1', exerciseList: null })).toBe(0)
    expect(workoutVolume(details(exercise({ workoutSetList: null })))).toBe(0)
    expect(setCount(details(exercise({ workoutSetList: null })))).toBe(0)
  })

  it('ignore sets without weight or reps for volume but count them as sets', () => {
    const d = details(
      exercise({
        workoutSetList: [
          { weight: 80, reps: 8 },
          { weight: null, reps: 8 },
          { weight: 80, reps: null },
        ],
      }),
    )
    expect(workoutVolume(d)).toBe(640)
    expect(setCount(d)).toBe(3)
  })

  it('add 0 for weight 0', () => {
    expect(workoutVolume(details(exercise({ workoutSetList: [{ weight: 0, reps: 10 }] })))).toBe(0)
  })

  it('multiply decimals', () => {
    expect(workoutVolume(details(exercise({ workoutSetList: [{ weight: 77.5, reps: 8 }] })))).toBe(620)
  })

  it('sum several exercises', () => {
    const d = details(
      exercise({ workoutSetList: [{ weight: 100, reps: 5 }] }),
      exercise({
        workoutSetList: [
          { weight: 50, reps: 10 },
          { weight: 50, reps: 10 },
        ],
      }),
    )
    expect(workoutVolume(d)).toBe(1500)
    expect(setCount(d)).toBe(3)
  })
})

describe('formatVolume', () => {
  it('rounds to a whole number with a thousands separator', () => {
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

  it('returns null when nothing is set', () => {
    expect(formatTarget(exercise())).toBeNull()
  })

  it('treats blank text as missing', () => {
    expect(formatTarget(exercise({ targetReps: '  ', repsInReserve: '' }))).toBeNull()
  })

  it('reads one set as singular', () => {
    expect(formatTarget(exercise({ targetSets: 1, targetReps: '8' }))).toBe('Target: 1 set × 8 reps')
  })

  it('keeps free text reps', () => {
    expect(formatTarget(exercise({ targetReps: '10-15' }))).toBe('Target: 10-15 reps')
  })
})

describe('formatSetChip', () => {
  it('shows weight and reps without a unit', () => {
    expect(formatSetChip({ weight: 80, reps: 8 })).toBe('80 × 8')
    expect(formatSetChip({ weight: 77.5, reps: 8 })).toBe('77.5 × 8')
  })

  it('shows a dash for a missing part', () => {
    expect(formatSetChip({ weight: null, reps: 8 })).toBe('— × 8')
    expect(formatSetChip({ weight: 80, reps: null })).toBe('80 × —')
  })
})

describe('parseWorkoutId', () => {
  it('parses digits only', () => {
    expect(parseWorkoutId('42')).toBe(42)
    expect(parseWorkoutId('abc')).toBeNull()
    expect(parseWorkoutId('4a')).toBeNull()
    expect(parseWorkoutId('-1')).toBeNull()
    expect(parseWorkoutId('')).toBeNull()
    expect(parseWorkoutId(undefined)).toBeNull()
  })
})

describe('isNotFoundError', () => {
  it('is true only for an ApiError with status 404', () => {
    expect(isNotFoundError(new ApiError(404, null))).toBe(true)
    expect(isNotFoundError(new ApiError(500, null))).toBe(false)
    expect(isNotFoundError(new Error('x'))).toBe(false)
    expect(isNotFoundError(null)).toBe(false)
  })
})

describe('isWorkoutPageType', () => {
  it('accepts only WEIGHTS', () => {
    expect(isWorkoutPageType('WEIGHTS')).toBe(true)
    expect(isWorkoutPageType('RUNNING')).toBe(false)
  })
})

describe('formatExerciseIndex', () => {
  it('is two digits and one-based', () => {
    expect(formatExerciseIndex(0)).toBe('01')
    expect(formatExerciseIndex(11)).toBe('12')
  })
})

describe('formatWorkoutWhen', () => {
  it('adds start and end time when there is a duration', () => {
    expect(formatWorkoutWhen('2026-10-22T18:05:00Z', 'PT1H2M', 'UTC')).toBe(
      'Thursday, 22 October 2026 · 18:05 – 19:07',
    )
  })

  it('shows only the date without a duration', () => {
    expect(formatWorkoutWhen('2026-10-22T18:05:00Z', null, 'UTC')).toBe('Thursday, 22 October 2026')
  })
})
