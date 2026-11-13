import type { StepsEntry } from '../api/types'
import { addDays, mondayOf } from './dates'

export const STEPS_GOAL = 8000

export const STEPS_CHART = { width: 560, height: 200, left: 8, right: 552, top: 40, baseline: 180 }

export interface StepsBar {
  date: string
  steps: number
  x: number
  y: number
  width: number
  height: number
  reached: boolean
}

export interface StepsChart {
  bars: StepsBar[]
  goalY: number
}

export function buildStepsChart(entries: StepsEntry[], to: string, days: number, goal: number): StepsChart {
  const byDate = new Map(entries.map((entry) => [entry.date, entry.steps]))
  const from = addDays(to, -(days - 1))
  const visible = entries.filter((entry) => entry.date >= from && entry.date <= to)
  const ceiling = Math.max(goal, ...visible.map((entry) => entry.steps)) * 1.08
  const plotHeight = STEPS_CHART.baseline - STEPS_CHART.top
  const slot = (STEPS_CHART.right - STEPS_CHART.left) / days
  const width = Math.max(3, slot * 0.68)

  const bars: StepsBar[] = []
  for (let index = 0; index < days; index++) {
    const date = addDays(from, index)
    const steps = byDate.get(date)
    if (steps === undefined) continue
    const height = Math.max(1, (steps / ceiling) * plotHeight)
    bars.push({
      date,
      steps,
      x: STEPS_CHART.left + index * slot + (slot - width) / 2,
      y: STEPS_CHART.baseline - height,
      width,
      height,
      reached: steps >= goal,
    })
  }

  return { bars, goalY: STEPS_CHART.baseline - (goal / ceiling) * plotHeight }
}

function average(entries: StepsEntry[], from: string, to: string): number | null {
  const inRange = entries.filter((entry) => entry.date >= from && entry.date <= to)
  if (inRange.length === 0) return null
  return Math.round(inRange.reduce((sum, entry) => sum + entry.steps, 0) / inRange.length)
}

export function weeklyAverages(entries: StepsEntry[], today: string) {
  const monday = mondayOf(today)
  return {
    thisWeek: average(entries, monday, today),
    lastWeek: average(entries, addDays(monday, -7), addDays(monday, -1)),
  }
}

export function formatSteps(value: number | null): string {
  return value === null ? '—' : value.toLocaleString('en-US')
}
