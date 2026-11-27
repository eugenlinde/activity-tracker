import { describe, expect, it } from 'vitest'
import { buildWeightChart, CHART, formatDelta, formatKg } from './weightChart'

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
