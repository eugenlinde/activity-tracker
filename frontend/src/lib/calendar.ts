import type { ActivityResponse } from '../api/types'
import { toIsoDate } from './dates'
import { isoToMinutes } from './duration'

export const AEROBIC_PATH = '/aerobic'

const MONTH_NAMES = [
  'January',
  'February',
  'March',
  'April',
  'May',
  'June',
  'July',
  'August',
  'September',
  'October',
  'November',
  'December',
]

export interface DaySummary {
  hasWorkout: boolean
  hasAerobic: boolean
  aerobicMinutes: number
  firstWorkoutId: number | null
}

export function workoutPath(id: number): string {
  return `/activities/${id}`
}

export function monthGrid(year: number, month: number): { leadingBlanks: number; daysInMonth: number } {
  const firstWeekday = new Date(Date.UTC(year, month - 1, 1)).getUTCDay()
  const daysInMonth = new Date(Date.UTC(year, month, 0)).getUTCDate()
  return { leadingBlanks: (firstWeekday + 6) % 7, daysInMonth }
}

export function shiftMonth(year: number, month: number, delta: number): { year: number; month: number } {
  const index = year * 12 + (month - 1) + delta
  return { year: Math.floor(index / 12), month: (((index % 12) + 12) % 12) + 1 }
}

export function monthTitle(year: number, month: number): string {
  return `${MONTH_NAMES[month - 1]} ${year}`
}

function dayAndMonth(isoDate: string): string {
  const [, month, day] = isoDate.split('-').map(Number)
  return `${day} ${MONTH_NAMES[month - 1]}`
}

export function dayTitle(isoDate: string): string {
  return `${dayAndMonth(isoDate)} ${isoDate.slice(0, 4)}`
}

export function groupByLocalDay(activities: ActivityResponse[]): Record<string, DaySummary> {
  const result: Record<string, DaySummary> = {}
  const firstTimes: Record<string, number> = {}
  for (const activity of activities) {
    const started = new Date(activity.startedAt)
    const key = toIsoDate(started)
    const summary = result[key] ?? { hasWorkout: false, hasAerobic: false, aerobicMinutes: 0, firstWorkoutId: null }
    result[key] = summary
    if (activity.activityType === 'WEIGHTS') {
      summary.hasWorkout = true
      const time = started.getTime()
      if (summary.firstWorkoutId === null || time < firstTimes[key]) {
        summary.firstWorkoutId = activity.id
        firstTimes[key] = time
      }
    } else {
      summary.hasAerobic = true
      summary.aerobicMinutes += activity.duration === null ? 0 : isoToMinutes(activity.duration)
    }
  }
  return result
}

export function dayLine(summary: DaySummary | undefined): string {
  if (!summary || (!summary.hasWorkout && !summary.hasAerobic)) return 'Rest day — nothing logged'
  const parts: string[] = []
  if (summary.hasWorkout) parts.push('Gym workout')
  if (summary.hasAerobic) parts.push('Aerobic')
  if (summary.hasAerobic && summary.aerobicMinutes > 0) parts.push(`${summary.aerobicMinutes} min`)
  return parts.join(' · ')
}

export function dayLabel(isoDate: string, summary: DaySummary | undefined): string {
  const parts = [dayAndMonth(isoDate)]
  if (summary?.hasWorkout) parts.push('gym workout')
  if (summary?.hasAerobic) {
    parts.push(summary.aerobicMinutes > 0 ? `aerobic ${summary.aerobicMinutes} minutes` : 'aerobic')
  }
  return parts.join(', ')
}
