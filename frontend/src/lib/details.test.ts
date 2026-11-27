import { describe, expect, it } from 'vitest'
import type { ActivityDetails, Exercise } from '../api/types'
import { fromDrafts, toDrafts } from './details'

function exercise(targetSets?: number | null): Exercise {
  const base = {
    name: 'Squat',
    targetReps: null,
    repsInReserve: null,
    comment: null,
    videoUrl: null,
    workoutSetList: [],
  }
  return (targetSets === undefined ? base : { ...base, targetSets }) as Exercise
}

function details(...exercises: Exercise[]): ActivityDetails {
  return { version: '1', exerciseList: exercises }
}

describe('toDrafts', () => {
  it('gives an empty string for a missing or null targetSets', () => {
    expect(toDrafts(details(exercise()))[0].targetSets).toBe('')
    expect(toDrafts(details(exercise(null)))[0].targetSets).toBe('')
  })

  it('gives the number as text', () => {
    expect(toDrafts(details(exercise(4)))[0].targetSets).toBe('4')
  })
})

describe('fromDrafts', () => {
  it('gives null for an empty targetSets and the number otherwise', () => {
    const [empty, four] = toDrafts(details(exercise(null), exercise(4)))
    expect(fromDrafts([empty])?.exerciseList?.[0].targetSets).toBeNull()
    expect(fromDrafts([four])?.exerciseList?.[0].targetSets).toBe(4)
    expect(fromDrafts([{ ...four, targetSets: '4' }])?.exerciseList?.[0].targetSets).toBe(4)
  })
})
