import { describe, expect, it } from 'vitest'
import { isNotFoundError, isWeightsWorkout, parseWorkoutId } from './workout'

describe('parseWorkoutId', () => {
  it('parses a numeric id', () => {
    expect(parseWorkoutId('42')).toBe(42)
  })

  it('rejects a missing id', () => {
    expect(parseWorkoutId(undefined)).toBeNull()
  })

  it('rejects non-numeric ids', () => {
    expect(parseWorkoutId('abc')).toBeNull()
    expect(parseWorkoutId('12abc')).toBeNull()
    expect(parseWorkoutId('-3')).toBeNull()
    expect(parseWorkoutId('1.5')).toBeNull()
    expect(parseWorkoutId('')).toBeNull()
  })

  it('rejects ids beyond the safe integer range', () => {
    expect(parseWorkoutId('99999999999999999999')).toBeNull()
  })
})

describe('isWeightsWorkout', () => {
  it('accepts WEIGHTS only', () => {
    expect(isWeightsWorkout({ activityType: 'WEIGHTS' })).toBe(true)
    expect(isWeightsWorkout({ activityType: 'RUNNING' })).toBe(false)
  })
})

describe('isNotFoundError', () => {
  it('is true for a 404 status', () => {
    expect(isNotFoundError({ status: 404 })).toBe(true)
  })

  it('is false for other statuses and non-objects', () => {
    expect(isNotFoundError({ status: 500 })).toBe(false)
    expect(isNotFoundError(new Error('x'))).toBe(false)
    expect(isNotFoundError(null)).toBe(false)
    expect(isNotFoundError('404')).toBe(false)
  })
})
