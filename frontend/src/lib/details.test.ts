import { describe, expect, it } from 'vitest'
import type { ActivityDetails, Exercise } from '../api/types'
import { emptyExercise, fromDrafts, toDrafts } from './details'

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

describe('toDrafts', () => {
  it('gives an empty string for a missing or null targetSets', () => {
    const legacy = { name: 'Squat' } as Exercise
    const details: ActivityDetails = { version: '1', exerciseList: [legacy, exercise({ targetSets: null })] }
    expect(toDrafts(details).map((draft) => draft.targetSets)).toEqual(['', ''])
  })

  it('gives the number as text', () => {
    expect(toDrafts({ version: '1', exerciseList: [exercise({ targetSets: 4 })] })[0].targetSets).toBe('4')
  })
})

describe('fromDrafts', () => {
  it('gives null for an empty targetSets', () => {
    const result = fromDrafts([{ ...emptyExercise(), name: 'Squat' }])
    expect(result?.exerciseList?.[0].targetSets).toBeNull()
  })

  it('gives the number for a filled targetSets', () => {
    const result = fromDrafts([{ ...emptyExercise(), name: 'Squat', targetSets: '4' }])
    expect(result?.exerciseList?.[0].targetSets).toBe(4)
  })
})
