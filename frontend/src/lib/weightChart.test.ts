import { describe, expect, it } from 'vitest'
import { buildWeightChart, CHART, deltaDirection, formatDelta, formatKg } from './weightChart'

describe('formatKg', () => {
  it('shows one decimal', () => {
    expect(formatKg(76)).toBe('76.0')
    expect(formatKg(76.44)).toBe('76.4')
  })
})

describe('formatDelta', () => {
  it('signs a gain with plus and a loss with a minus sign', () => {
    expect(formatDelta(1.24)).toBe('+1.2')
    expect(formatDelta(-1.24)).toBe('−1.2')
  })

  it('treats a change below half a decimal as none', () => {
    expect(formatDelta(0.04)).toBe('0.0')
    expect(formatDelta(-0.04)).toBe('0.0')
  })

  it('shows a change of half a decimal or more', () => {
    expect(formatDelta(0.06)).toBe('+0.1')
    expect(formatDelta(-0.06)).toBe('−0.1')
  })
})

describe('deltaDirection', () => {
  it('is down for a loss and up for a gain', () => {
    expect(deltaDirection(-1.2)).toBe('down')
    expect(deltaDirection(1.2)).toBe('up')
  })

  it('is same when the change displays as zero', () => {
    expect(deltaDirection(0)).toBe('same')
    expect(deltaDirection(0.04)).toBe('same')
    expect(deltaDirection(-0.04)).toBe('same')
  })

  it('is down or up from half a decimal', () => {
    expect(deltaDirection(-0.06)).toBe('down')
    expect(deltaDirection(0.06)).toBe('up')
  })

  it('agrees with the sign formatDelta shows', () => {
    for (const value of [-2, -0.06, -0.04, 0, 0.04, 0.06, 2]) {
      const direction = deltaDirection(value)
      const text = formatDelta(value)
      if (direction === 'same') expect(text).toBe('0.0')
      if (direction === 'down') expect(text.startsWith('−')).toBe(true)
      if (direction === 'up') expect(text.startsWith('+')).toBe(true)
    }
  })
})

describe('buildWeightChart', () => {
  it('is empty without entries', () => {
    expect(buildWeightChart([], '2026-11-01', '2026-11-30')).toEqual({ points: [], line: '', area: '' })
  })

  it('places the first and last day at the edges and the heavier entry higher', () => {
    const chart = buildWeightChart(
      [
        { date: '2026-11-01', weightKg: 80 },
        { date: '2026-11-30', weightKg: 78 },
      ],
      '2026-11-01',
      '2026-11-30',
    )
    const [first, last] = chart.points
    expect(first.x).toBe(CHART.left)
    expect(last.x).toBe(CHART.right)
    expect(first.y).toBeLessThan(last.y)
  })

  it('keeps an entry outside the range inside the chart', () => {
    const chart = buildWeightChart([{ date: '2026-12-15', weightKg: 80 }], '2026-11-01', '2026-11-30')
    expect(chart.points[0].x).toBe(CHART.right)
  })
})
