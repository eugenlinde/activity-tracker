import { describe, expect, it } from 'vitest'
import type { DietEntry } from '../api/types'
import {
  buildMonthGrid,
  canGoNext,
  countMonth,
  dayAriaLabel,
  dayLabel,
  DIET_STATUSES,
  doneRequest,
  monthOf,
  monthRange,
  monthTitle,
  shiftMonth,
  withEntry,
  withoutEntry,
} from './dietCalendar'

const TODAY = '2026-11-10'

function entry(date: string, status: DietEntry['status'] = 'ON_PLAN', comment: string | null = null): DietEntry {
  return { date, status, comment }
}

function leadingBlanks(month: string): number {
  const cells = buildMonthGrid(month, TODAY)
  const firstDay = cells.findIndex((cell) => cell !== null)
  return firstDay
}

describe('buildMonthGrid', () => {
  it('has no leading blanks when the month starts on a Monday', () => {
    const cells = buildMonthGrid('2026-06', TODAY)
    expect(leadingBlanks('2026-06')).toBe(0)
    expect(cells[0]).toEqual({ date: '2026-06-01', day: 1, future: false })
    expect(cells).toHaveLength(35)
  })

  it('has six leading blanks when the month starts on a Sunday', () => {
    const cells = buildMonthGrid('2026-11', TODAY)
    expect(leadingBlanks('2026-11')).toBe(6)
    expect(cells[6]).toMatchObject({ date: '2026-11-01', day: 1 })
    expect(cells).toHaveLength(42)
    expect(cells[cells.length - 1]).toBeNull()
  })

  it('has 29 days in February of a leap year', () => {
    const cells = buildMonthGrid('2024-02', TODAY)
    expect(cells.filter((cell) => cell !== null)).toHaveLength(29)
    expect(leadingBlanks('2024-02')).toBe(3)
  })

  it('has 28 days in February of a common year', () => {
    const cells = buildMonthGrid('2026-02', TODAY)
    expect(cells.filter((cell) => cell !== null)).toHaveLength(28)
    expect(leadingBlanks('2026-02')).toBe(6)
  })

  it('fills exactly four rows for a 28-day February that starts on a Monday', () => {
    const cells = buildMonthGrid('2027-02', TODAY)
    expect(leadingBlanks('2027-02')).toBe(0)
    expect(cells).toHaveLength(28)
  })

  it('always returns a multiple of seven cells', () => {
    for (const month of ['2024-02', '2026-02', '2026-06', '2026-08', '2026-11', '2026-12', '2027-02']) {
      expect(buildMonthGrid(month, TODAY).length % 7).toBe(0)
    }
  })

  it('marks days after today as future and today as not future', () => {
    const cells = buildMonthGrid('2026-11', TODAY)
    const byDay = (day: number) => cells.find((cell) => cell?.day === day)
    expect(byDay(10)?.future).toBe(false)
    expect(byDay(9)?.future).toBe(false)
    expect(byDay(11)?.future).toBe(true)
    expect(byDay(30)?.future).toBe(true)
  })

  it('marks every day of a month after the current one as future', () => {
    const cells = buildMonthGrid('2026-12', TODAY)
    expect(cells.filter((cell) => cell !== null).every((cell) => cell?.future)).toBe(true)
  })
})

describe('countMonth', () => {
  it('counts every day without an entry as not logged in a past month', () => {
    const counts = countMonth(
      '2026-10',
      [entry('2026-10-01', 'ON_PLAN'), entry('2026-10-02', 'MORE'), entry('2026-10-03', 'OUTSIDE')],
      TODAY,
    )
    expect(counts).toEqual({ onPlan: 1, more: 1, less: 0, outside: 1, notLogged: 28 })
  })

  it('counts today but not future days as not logged in the current month', () => {
    const counts = countMonth('2026-11', [entry('2026-11-03', 'MORE'), entry('2026-11-10', 'LESS')], TODAY)
    expect(counts).toEqual({ onPlan: 0, more: 1, less: 1, outside: 0, notLogged: 8 })
  })

  it('counts today as not logged when it has no entry', () => {
    const counts = countMonth('2026-11', [entry('2026-11-09')], TODAY)
    expect(counts.notLogged).toBe(9)
  })

  it('handles an empty list', () => {
    expect(countMonth('2026-11', [], TODAY)).toEqual({
      onPlan: 0,
      more: 0,
      less: 0,
      outside: 0,
      notLogged: 10,
    })
  })

  it('ignores entries of other months', () => {
    const counts = countMonth('2026-11', [entry('2026-10-31', 'MORE'), entry('2026-12-01', 'LESS')], TODAY)
    expect(counts).toEqual({ onPlan: 0, more: 0, less: 0, outside: 0, notLogged: 10 })
  })

  it('has nothing not logged in a month after the current one', () => {
    expect(countMonth('2026-12', [], TODAY).notLogged).toBe(0)
  })
})

describe('month arithmetic', () => {
  it('takes the month of a date', () => {
    expect(monthOf('2026-11-27')).toBe('2026-11')
  })

  it('shifts across a year boundary in both directions', () => {
    expect(shiftMonth('2026-12', 1)).toBe('2027-01')
    expect(shiftMonth('2027-01', -1)).toBe('2026-12')
    expect(shiftMonth('2026-11', -11)).toBe('2025-12')
    expect(shiftMonth('2026-11', 0)).toBe('2026-11')
  })

  it('gives the first and last day of a month', () => {
    expect(monthRange('2024-02')).toEqual({ from: '2024-02-01', to: '2024-02-29' })
    expect(monthRange('2026-02')).toEqual({ from: '2026-02-01', to: '2026-02-28' })
    expect(monthRange('2026-11')).toEqual({ from: '2026-11-01', to: '2026-11-30' })
  })

  it('does not go past the current month', () => {
    expect(canGoNext('2026-11', TODAY)).toBe(false)
    expect(canGoNext('2026-10', TODAY)).toBe(true)
    expect(canGoNext('2025-12', TODAY)).toBe(true)
  })

  it('titles a month with its name and year', () => {
    expect(monthTitle('2026-10')).toBe('October 2026')
  })
})

describe('labels', () => {
  it('labels a day, adding Today for today', () => {
    expect(dayLabel('2026-10-22', TODAY)).toBe('22 October')
    expect(dayLabel('2026-11-10', TODAY)).toBe('Today · 10 November')
  })

  it('labels a day for assistive technology with its status', () => {
    expect(dayAriaLabel('2026-10-22', undefined)).toBe('22 October')
    expect(dayAriaLabel('2026-10-22', 'ON_PLAN')).toBe('22 October, On plan')
    expect(dayAriaLabel('2026-10-22', 'OUTSIDE')).toBe('22 October, Outside')
  })

  it('describes the four statuses', () => {
    expect(DIET_STATUSES.ON_PLAN).toMatchObject({ letter: 'P', color: '#A8B9A3', buttonLabel: 'Ate by the plan' })
    expect(DIET_STATUSES.MORE).toMatchObject({ letter: 'M', color: '#C9B08A', legendLabel: 'Ate more' })
    expect(DIET_STATUSES.LESS).toMatchObject({ letter: 'L', color: '#8DAACB', counterLabel: 'Less' })
    expect(DIET_STATUSES.OUTSIDE).toMatchObject({ letter: 'O', color: '#C58F94', legendLabel: 'Outside plan' })
  })
})

describe('doneRequest', () => {
  it('sends nothing when the text is unchanged', () => {
    expect(doneRequest('MORE', 'Pizza', 'Pizza')).toBeNull()
    expect(doneRequest(null, null, '')).toBeNull()
  })

  it('saves the day as on plan when there is text but no status', () => {
    expect(doneRequest(null, null, 'Skipped lunch')).toEqual({ status: 'ON_PLAN', comment: 'Skipped lunch' })
  })

  it('keeps the saved status when there is text', () => {
    expect(doneRequest('LESS', null, 'Light day')).toEqual({ status: 'LESS', comment: 'Light day' })
    expect(doneRequest('MORE', 'Pizza', 'Pizza and cake')).toEqual({ status: 'MORE', comment: 'Pizza and cake' })
  })

  it('turns blank text into no comment on a day with a saved comment', () => {
    expect(doneRequest('MORE', 'Pizza', '   ')).toEqual({ status: 'MORE', comment: null })
    expect(doneRequest('MORE', 'Pizza', '')).toEqual({ status: 'MORE', comment: null })
  })

  it('sends nothing for blank text on an empty day', () => {
    expect(doneRequest(null, null, '   ')).toBeNull()
  })

  it('sends nothing for blank text on a day with a status and no comment', () => {
    expect(doneRequest('MORE', null, '  ')).toBeNull()
  })
})

describe('entry list updates', () => {
  it('adds a new entry in date order and replaces an existing one', () => {
    const list = [entry('2026-11-01'), entry('2026-11-05')]
    expect(withEntry(list, entry('2026-11-03', 'MORE')).map((item) => item.date)).toEqual([
      '2026-11-01',
      '2026-11-03',
      '2026-11-05',
    ])
    expect(withEntry(list, entry('2026-11-05', 'LESS'))).toEqual([entry('2026-11-01'), entry('2026-11-05', 'LESS')])
  })

  it('removes an entry without touching the others', () => {
    const list = [entry('2026-11-01'), entry('2026-11-05')]
    expect(withoutEntry(list, '2026-11-05')).toEqual([entry('2026-11-01')])
    expect(withoutEntry(list, '2026-11-09')).toEqual(list)
  })
})
