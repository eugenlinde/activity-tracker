import { describe, expect, it } from 'vitest'
import type { ActivityResponse, ActivityType } from '../api/types'
import {
  dayLabel,
  dayLine,
  dayTitle,
  groupByLocalDay,
  monthGrid,
  monthTitle,
  shiftMonth,
  workoutPath,
  type DaySummary,
} from './calendar'

function activity(
  id: number,
  startedAt: Date,
  activityType: ActivityType = 'RUNNING',
  duration: string | null = 'PT30M',
): ActivityResponse {
  return {
    id,
    ownerId: 1,
    activityType,
    startedAt: startedAt.toISOString(),
    duration,
    details: null,
    notes: null,
    createdAt: startedAt.toISOString(),
    updatedAt: startedAt.toISOString(),
  }
}

function summary(overrides: Partial<DaySummary> = {}): DaySummary {
  return { hasWorkout: false, hasAerobic: false, aerobicMinutes: 0, firstWorkoutId: null, ...overrides }
}

describe('monthGrid', () => {
  it('has no blanks for a month starting on Monday', () => {
    expect(monthGrid(2026, 6)).toEqual({ leadingBlanks: 0, daysInMonth: 30 })
  })

  it('has six blanks for a month starting on Sunday', () => {
    expect(monthGrid(2026, 11)).toEqual({ leadingBlanks: 6, daysInMonth: 30 })
  })

  it('knows a leap-year February', () => {
    expect(monthGrid(2028, 2)).toEqual({ leadingBlanks: 1, daysInMonth: 29 })
  })

  it('knows a common February', () => {
    expect(monthGrid(2027, 2)).toEqual({ leadingBlanks: 0, daysInMonth: 28 })
  })
})

describe('shiftMonth', () => {
  it('rolls December over to January of the next year', () => {
    expect(shiftMonth(2026, 12, 1)).toEqual({ year: 2027, month: 1 })
  })

  it('rolls January back to December of the previous year', () => {
    expect(shiftMonth(2026, 1, -1)).toEqual({ year: 2025, month: 12 })
  })

  it('moves inside a year', () => {
    expect(shiftMonth(2026, 5, 1)).toEqual({ year: 2026, month: 6 })
    expect(shiftMonth(2026, 5, -1)).toEqual({ year: 2026, month: 4 })
  })
})

describe('titles', () => {
  it('formats month and day titles', () => {
    expect(monthTitle(2026, 10)).toBe('October 2026')
    expect(dayTitle('2026-10-22')).toBe('22 October 2026')
  })
})

describe('groupByLocalDay', () => {
  it('returns nothing for no activities', () => {
    expect(groupByLocalDay([])).toEqual({})
  })

  it('flags both kinds on one day', () => {
    const result = groupByLocalDay([
      activity(1, new Date(2026, 9, 22, 8, 0), 'WEIGHTS', 'PT50M'),
      activity(2, new Date(2026, 9, 22, 18, 0), 'RUNNING', 'PT35M'),
    ])
    expect(result['2026-10-22']).toEqual(summary({ hasWorkout: true, hasAerobic: true, aerobicMinutes: 35, firstWorkoutId: 1 }))
  })

  it('adds up aerobic minutes', () => {
    const result = groupByLocalDay([
      activity(1, new Date(2026, 9, 22, 8, 0), 'RUNNING', 'PT35M'),
      activity(2, new Date(2026, 9, 22, 18, 0), 'WALKING', 'PT30M'),
    ])
    expect(result['2026-10-22'].aerobicMinutes).toBe(65)
  })

  it('splits days at local midnight', () => {
    const result = groupByLocalDay([
      activity(1, new Date(2026, 9, 22, 23, 59)),
      activity(2, new Date(2026, 9, 23, 0, 1)),
    ])
    expect(Object.keys(result).sort()).toEqual(['2026-10-22', '2026-10-23'])
  })

  it('does not count weights duration', () => {
    const result = groupByLocalDay([activity(1, new Date(2026, 9, 22, 8, 0), 'WEIGHTS', 'PT60M')])
    expect(result['2026-10-22'].aerobicMinutes).toBe(0)
    expect(result['2026-10-22'].hasAerobic).toBe(false)
  })

  it('marks aerobic without duration with zero minutes', () => {
    const result = groupByLocalDay([activity(1, new Date(2026, 9, 22, 8, 0), 'AEROBIC', null)])
    expect(result['2026-10-22']).toEqual(summary({ hasAerobic: true }))
  })

  it('picks the earliest workout regardless of input order', () => {
    const result = groupByLocalDay([
      activity(2, new Date(2026, 9, 22, 18, 0), 'WEIGHTS'),
      activity(1, new Date(2026, 9, 22, 7, 0), 'WEIGHTS'),
    ])
    expect(result['2026-10-22'].firstWorkoutId).toBe(1)
  })

  it('has no first workout on an aerobic-only day', () => {
    const result = groupByLocalDay([activity(1, new Date(2026, 9, 22, 8, 0), 'RUNNING')])
    expect(result['2026-10-22'].firstWorkoutId).toBeNull()
  })
})

describe('dayLine', () => {
  it('joins the parts that exist', () => {
    expect(dayLine(summary({ hasWorkout: true, hasAerobic: true, aerobicMinutes: 35 }))).toBe(
      'Gym workout · Aerobic · 35 min',
    )
    expect(dayLine(summary({ hasWorkout: true }))).toBe('Gym workout')
    expect(dayLine(summary({ hasAerobic: true, aerobicMinutes: 65 }))).toBe('Aerobic · 65 min')
    expect(dayLine(summary({ hasAerobic: true }))).toBe('Aerobic')
  })

  it('describes a rest day', () => {
    expect(dayLine(undefined)).toBe('Rest day — nothing logged')
  })
})

describe('dayLabel', () => {
  it('lists the parts that exist', () => {
    expect(dayLabel('2026-10-22', summary({ hasWorkout: true, hasAerobic: true, aerobicMinutes: 35 }))).toBe(
      '22 October, gym workout, aerobic 35 minutes',
    )
    expect(dayLabel('2026-10-22', summary({ hasWorkout: true }))).toBe('22 October, gym workout')
    expect(dayLabel('2026-10-22', summary({ hasAerobic: true }))).toBe('22 October, aerobic')
    expect(dayLabel('2026-10-22', undefined)).toBe('22 October')
  })
})

describe('workoutPath', () => {
  it('builds the activity path', () => {
    expect(workoutPath(7)).toBe('/activities/7')
  })
})
