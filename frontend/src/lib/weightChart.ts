import type { WeightEntry } from '../api/types'
import { daysBetween } from './dates'

export const CHART = { width: 560, height: 200, left: 8, right: 552, top: 40, bottom: 160 }

export interface ChartPoint {
  x: number
  y: number
  entry: WeightEntry
}

export interface WeightChart {
  points: ChartPoint[]
  line: string
  area: string
}

const Y_PADDING_KG = 0.4

export function buildWeightChart(entries: WeightEntry[], from: string, to: string): WeightChart {
  if (entries.length === 0) return { points: [], line: '', area: '' }

  const rangeDays = Math.max(1, daysBetween(from, to))
  const weights = entries.map((entry) => entry.weightKg)
  const min = Math.min(...weights) - Y_PADDING_KG
  const max = Math.max(...weights) + Y_PADDING_KG

  const points = entries.map((entry) => {
    const position = Math.min(1, Math.max(0, daysBetween(from, entry.date) / rangeDays))
    return {
      x: CHART.left + position * (CHART.right - CHART.left),
      y: CHART.bottom - ((entry.weightKg - min) / (max - min)) * (CHART.bottom - CHART.top),
      entry,
    }
  })

  const coordinates = points.map((point) => `${point.x.toFixed(1)},${point.y.toFixed(1)}`)
  const first = points[0]
  const last = points[points.length - 1]
  const area = `M${first.x.toFixed(1)},${CHART.bottom} L${coordinates.join(' L')} L${last.x.toFixed(1)},${CHART.bottom} Z`

  return { points, line: coordinates.join(' '), area }
}

export function formatKg(value: number): string {
  return value.toFixed(1)
}

export function formatDelta(value: number): string {
  if (Math.abs(value) < 0.05) return '0.0'
  return `${value > 0 ? '+' : '−'}${Math.abs(value).toFixed(1)}`
}
