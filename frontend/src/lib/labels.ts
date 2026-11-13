import type { ActivityType } from '../api/types'

export const ACTIVITY_LABELS: Record<ActivityType, string> = {
  RUNNING: 'Running',
  WEIGHTS: 'Weights',
  WALKING: 'Walking',
  AEROBIC: 'Aerobic',
}

export function formatDay(iso: string): string {
  return new Date(iso).toLocaleDateString('en-GB', { day: 'numeric', month: 'short' })
}

export function formatWeekdayTime(iso: string): string {
  const date = new Date(iso)
  const weekday = date.toLocaleDateString('en-GB', { weekday: 'short' })
  const time = date.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' })
  return `${weekday} ${time}`
}
