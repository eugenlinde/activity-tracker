import { describe, expect, it } from 'vitest'
import type { ActivityResponse, ActivityType } from '../api/types'
import {
  AEROBIC_CHART,
  AEROBIC_KINDS,
  aerobicStartedAt,
  aerobicSummary,
  buildAerobicChart,
  isAerobic,
  toAerobicEntries,
  weeklyTotals,
  type AerobicEntry,
} from './aerobic'

function activity(
  id: number,
  startedAt: Date,
  activityType: ActivityType = 'RUNNING',
  duration: string | null = 'PT30M',
  notes: string | null = null,
): ActivityResponse {
  return {
    id,
    ownerId: 1,
    activityType,
    startedAt: startedAt.toISOString(),
    duration,
    details: null,
    notes,
    createdAt: startedAt.toISOString(),
    updatedAt: startedAt.toISOString(),
  }
}

function entry(date: string, minutes: number, id = 1): AerobicEntry {
  return { id, date, kind: 'RUNNING', minutes, note: null }
}

describe('isAerobic', () => {
  it('is false for weights and true for everything else', () => {
    expect(isAerobic('WEIGHTS')).toBe(false)
    expect(isAerobic('AEROBIC')).toBe(true)
    for (const kind of AEROBIC_KINDS) expect(isAerobic(kind)).toBe(true)
  })
})

describe('toAerobicEntries', () => {
  it('drops weights', () => {
    const list = [activity(1, new Date(2026, 9, 21, 12), 'WEIGHTS'), activity(2, new Date(2026, 9, 20, 12), 'CYCLING')]
    expect(toAerobicEntries(list).map((item) => item.id)).toEqual([2])
  })

  it('counts a missing duration as 0 minutes', () => {
    const [item] = toAerobicEntries([activity(1, new Date(2026, 9, 21, 12), 'RUNNING', null)])
    expect(item.minutes).toBe(0)
  })

  it('reads hours and minutes', () => {
    const [item] = toAerobicEntries([activity(1, new Date(2026, 9, 21, 12), 'ROWING', 'PT1H30M', 'note')])
    expect(item.minutes).toBe(90)
    expect(item.kind).toBe('ROWING')
    expect(item.note).toBe('note')
    expect(item.date).toBe('2026-10-21')
  })

  it('sorts newest first', () => {
    const list = [
      activity(1, new Date(2026, 9, 18, 12)),
      activity(2, new Date(2026, 9, 21, 12)),
      activity(3, new Date(2026, 9, 19, 12)),
    ]
    expect(toAerobicEntries(list).map((item) => item.id)).toEqual([2, 3, 1])
  })

  it('gives an empty list for no input', () => {
    expect(toAerobicEntries([])).toEqual([])
  })
})

describe('weeklyTotals', () => {
  it('gives eight consecutive Mondays with the last one current', () => {
    const weeks = weeklyTotals([], '2026-10-21')
    expect(weeks).toHaveLength(8)
    expect(weeks[0].monday).toBe('2026-08-31')
    expect(weeks[7].monday).toBe('2026-10-19')
    expect(weeks.map((week) => week.current)).toEqual([false, false, false, false, false, false, false, true])
    expect(weeks.every((week) => week.minutes === 0)).toBe(true)
  })

  it('puts a Sunday in the week of the Monday before and the next Monday in the next week', () => {
    const weeks = weeklyTotals([entry('2026-10-18', 40), entry('2026-10-19', 25)], '2026-10-21')
    expect(weeks[6].minutes).toBe(40)
    expect(weeks[7].minutes).toBe(25)
  })

  it('sums a week that crosses a month end', () => {
    const weeks = weeklyTotals([entry('2026-09-30', 20), entry('2026-10-01', 30)], '2026-10-02')
    expect(weeks[7].monday).toBe('2026-09-28')
    expect(weeks[7].minutes).toBe(50)
  })

  it('sums a week that crosses a year end', () => {
    const weeks = weeklyTotals([entry('2026-12-30', 10), entry('2027-01-01', 20)], '2027-01-02')
    expect(weeks[7].monday).toBe('2026-12-28')
    expect(weeks[7].minutes).toBe(30)
  })

  it('ignores entries older than the eight weeks', () => {
    const weeks = weeklyTotals([entry('2026-08-30', 99), entry('2026-08-31', 5)], '2026-10-21')
    expect(weeks[0].minutes).toBe(5)
    expect(weeks.reduce((sum, week) => sum + week.minutes, 0)).toBe(5)
  })
})

describe('aerobicSummary', () => {
  it('gives this week, rounded average and best', () => {
    const entries = [entry('2026-10-20', 100), entry('2026-10-01', 30)]
    const summary = aerobicSummary(entries, '2026-10-21')
    expect(summary.thisWeek).toBe(100)
    expect(summary.average).toBe(16)
    expect(summary.best).toBe(100)
  })

  it('rounds the average of eight weeks', () => {
    expect(aerobicSummary([entry('2026-10-20', 100)], '2026-10-21').average).toBe(13)
  })

  it('counts only the current calendar month for this month', () => {
    const entries = [entry('2026-09-30', 20), entry('2026-10-01', 30), entry('2026-09-10', 60)]
    const summary = aerobicSummary(entries, '2026-10-02')
    expect(summary.thisWeek).toBe(50)
    expect(summary.thisMonth).toBe(30)
  })

  it('does not count December in January', () => {
    const summary = aerobicSummary([entry('2026-12-30', 10), entry('2027-01-01', 20)], '2027-01-02')
    expect(summary.thisWeek).toBe(30)
    expect(summary.thisMonth).toBe(20)
  })

  it('gives zeros with no entries', () => {
    expect(aerobicSummary([], '2026-10-21')).toEqual({ thisWeek: 0, thisMonth: 0, average: 0, best: 0 })
  })
})

describe('buildAerobicChart', () => {
  it('gives eight bars with only the last one current', () => {
    const bars = buildAerobicChart(weeklyTotals([], '2026-10-21'))
    expect(bars).toHaveLength(8)
    expect(bars.map((bar) => bar.current)).toEqual([false, false, false, false, false, false, false, true])
  })

  it('gives zero height and the baseline as top for empty weeks', () => {
    const bars = buildAerobicChart(weeklyTotals([], '2026-10-21'))
    for (const bar of bars) {
      expect(bar.height).toBe(0)
      expect(bar.y).toBe(AEROBIC_CHART.baseline)
      expect(bar.valueY).toBe(AEROBIC_CHART.baseline - 6)
    }
  })

  it('scales a small total against 60 minutes', () => {
    const bars = buildAerobicChart(weeklyTotals([entry('2026-10-20', 30)], '2026-10-21'))
    expect(bars[7].height).toBeCloseTo(50)
    expect(bars[7].y).toBeCloseTo(AEROBIC_CHART.baseline - 50)
  })

  it('gives the largest total over 60 the full plot height', () => {
    const bars = buildAerobicChart(weeklyTotals([entry('2026-10-20', 120), entry('2026-10-13', 60)], '2026-10-21'))
    expect(bars[7].height).toBeCloseTo(AEROBIC_CHART.plotHeight)
    expect(bars[6].height).toBeCloseTo(AEROBIC_CHART.plotHeight / 2)
  })

  it('lays the bars out left to right inside the slots', () => {
    const bars = buildAerobicChart(weeklyTotals([], '2026-10-21'))
    expect(bars[0].centerX).toBeCloseTo(AEROBIC_CHART.left + 34)
    expect(bars[0].width).toBeCloseTo(68 * 0.56)
    expect(bars[1].x).toBeGreaterThan(bars[0].x)
  })
})

describe('aerobicStartedAt', () => {
  it('gives local noon for a past date', () => {
    const now = new Date(2026, 9, 21, 9, 0)
    expect(aerobicStartedAt('2026-10-01', now)).toBe(new Date(2026, 9, 1, 12, 0).toISOString())
  })

  it('gives now for today before noon', () => {
    const now = new Date(2026, 9, 21, 9, 30)
    expect(aerobicStartedAt('2026-10-21', now)).toBe(now.toISOString())
  })

  it('gives noon for today after noon', () => {
    const now = new Date(2026, 9, 21, 15, 0)
    expect(aerobicStartedAt('2026-10-21', now)).toBe(new Date(2026, 9, 21, 12, 0).toISOString())
  })
})
