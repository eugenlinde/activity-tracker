import type { ActivityResponse, ActivityType } from '../api/types'
import { addDays, mondayOf, toIsoDate } from './dates'
import { isoToMinutes } from './duration'

export const AEROBIC_KINDS: ActivityType[] = ['RUNNING', 'CYCLING', 'WALKING', 'SWIMMING', 'ROWING', 'TREADMILL']

const WEEKS = 8

export interface AerobicEntry {
  id: number
  date: string
  kind: ActivityType
  minutes: number
  note: string | null
}

export interface WeekTotal {
  monday: string
  minutes: number
  current: boolean
}

export interface AerobicSummary {
  thisWeek: number
  thisMonth: number
  average: number
  best: number
}

export const AEROBIC_CHART = { width: 560, height: 170, left: 8, right: 552, baseline: 130, plotHeight: 100, labelY: 150 }

export interface AerobicBar extends WeekTotal {
  x: number
  y: number
  width: number
  height: number
  centerX: number
  valueY: number
}

export function isAerobic(type: ActivityType): boolean {
  return type !== 'WEIGHTS'
}

export function toAerobicEntries(activities: ActivityResponse[]): AerobicEntry[] {
  return activities
    .filter((activity) => isAerobic(activity.activityType))
    .sort((a, b) => new Date(b.startedAt).getTime() - new Date(a.startedAt).getTime())
    .map((activity) => ({
      id: activity.id,
      date: toIsoDate(new Date(activity.startedAt)),
      kind: activity.activityType,
      minutes: activity.duration === null ? 0 : isoToMinutes(activity.duration),
      note: activity.notes,
    }))
}

export function weeklyTotals(entries: AerobicEntry[], today: string): WeekTotal[] {
  const currentMonday = mondayOf(today)
  const sums = new Map<string, number>()
  for (const entry of entries) {
    const monday = mondayOf(entry.date)
    sums.set(monday, (sums.get(monday) ?? 0) + entry.minutes)
  }
  const weeks: WeekTotal[] = []
  for (let index = 0; index < WEEKS; index++) {
    const monday = addDays(currentMonday, -7 * (WEEKS - 1 - index))
    weeks.push({ monday, minutes: sums.get(monday) ?? 0, current: index === WEEKS - 1 })
  }
  return weeks
}

export function aerobicSummary(entries: AerobicEntry[], today: string): AerobicSummary {
  const weeks = weeklyTotals(entries, today)
  const month = today.slice(0, 7)
  const thisMonth = entries
    .filter((entry) => entry.date.slice(0, 7) === month)
    .reduce((sum, entry) => sum + entry.minutes, 0)
  const totals = weeks.map((week) => week.minutes)
  return {
    thisWeek: weeks[WEEKS - 1].minutes,
    thisMonth,
    average: Math.round(totals.reduce((sum, minutes) => sum + minutes, 0) / WEEKS),
    best: Math.max(...totals),
  }
}

export function buildAerobicChart(weeks: WeekTotal[]): AerobicBar[] {
  const { left, right, baseline, plotHeight } = AEROBIC_CHART
  const slot = (right - left) / WEEKS
  const width = slot * 0.56
  const ceiling = Math.max(60, ...weeks.map((week) => week.minutes))
  return weeks.map((week, index) => {
    const height = (week.minutes / ceiling) * plotHeight
    const y = baseline - height
    return {
      ...week,
      x: left + index * slot + (slot - width) / 2,
      y,
      width,
      height,
      centerX: left + index * slot + slot / 2,
      valueY: y - 6,
    }
  })
}

export function aerobicStartedAt(date: string, now: Date): string {
  const noon = new Date(`${date}T12:00:00`)
  return noon.getTime() > now.getTime() ? now.toISOString() : noon.toISOString()
}
