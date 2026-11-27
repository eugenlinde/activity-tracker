import { describe, expect, it } from 'vitest'
import { addDays, daysBetween, formatMonthName, formatShortDate, formatWeekday, mondayOf } from './dates'

describe('formatMonthName', () => {
  it('writes the full English month name', () => {
    expect(formatMonthName('2026-10-21')).toBe('October')
    expect(formatMonthName('2026-01-01')).toBe('January')
  })
})

describe('formatWeekday', () => {
  it('writes the full English weekday', () => {
    expect(formatWeekday('2026-10-21')).toBe('Wednesday')
    expect(formatWeekday('2026-10-25')).toBe('Sunday')
  })
})

describe('addDays', () => {
  it('moves forward and back across a month end', () => {
    expect(addDays('2026-11-30', 1)).toBe('2026-12-01')
    expect(addDays('2026-03-01', -1)).toBe('2026-02-28')
  })

  it('counts a leap day', () => {
    expect(addDays('2028-02-28', 1)).toBe('2028-02-29')
  })

  it('is not moved by daylight saving time', () => {
    expect(addDays('2026-03-28', 2)).toBe('2026-03-30')
    expect(addDays('2026-10-24', 2)).toBe('2026-10-26')
  })
})

describe('daysBetween', () => {
  it('counts whole days and is negative backwards', () => {
    expect(daysBetween('2026-11-01', '2026-11-30')).toBe(29)
    expect(daysBetween('2026-11-30', '2026-11-01')).toBe(-29)
    expect(daysBetween('2026-11-27', '2026-11-27')).toBe(0)
  })
})

describe('formatShortDate', () => {
  it('writes day and short month without a year', () => {
    expect(formatShortDate('2026-11-26')).toBe('26 Nov')
    expect(formatShortDate('2026-09-03')).toBe('3 Sept')
  })
})

describe('mondayOf', () => {
  it('gives the Monday of the week, Monday itself included', () => {
    expect(mondayOf('2026-11-27')).toBe('2026-11-23')
    expect(mondayOf('2026-11-23')).toBe('2026-11-23')
    expect(mondayOf('2026-11-29')).toBe('2026-11-23')
  })
})
