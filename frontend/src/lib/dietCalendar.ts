import type { DietEntry, DietStatus } from '../api/types'
import { palette } from '../theme'

export interface DietStatusInfo {
  letter: string
  color: string
  counterLabel: string
  legendLabel: string
  buttonLabel: string
}

export const DIET_STATUS_ORDER: readonly DietStatus[] = ['ON_PLAN', 'MORE', 'LESS', 'OUTSIDE']

export const DIET_STATUSES: Record<DietStatus, DietStatusInfo> = {
  ON_PLAN: {
    letter: 'P',
    color: palette.sage,
    counterLabel: 'On plan',
    legendLabel: 'On plan',
    buttonLabel: 'Ate by the plan',
  },
  MORE: {
    letter: 'M',
    color: palette.sand,
    counterLabel: 'More',
    legendLabel: 'Ate more',
    buttonLabel: 'Ate more than planned',
  },
  LESS: {
    letter: 'L',
    color: palette.blue,
    counterLabel: 'Less',
    legendLabel: 'Ate less',
    buttonLabel: 'Ate less than planned',
  },
  OUTSIDE: {
    letter: 'O',
    color: palette.danger,
    counterLabel: 'Outside',
    legendLabel: 'Outside plan',
    buttonLabel: 'Ate outside the plan',
  },
}

export const NOT_LOGGED_COLOR = palette.muted

export type MonthCell = null | { date: string; day: number; future: boolean }

export interface MonthCounts {
  onPlan: number
  more: number
  less: number
  outside: number
  notLogged: number
}

export interface DoneRequest {
  status: DietStatus
  comment: string | null
}

function pad(n: number): string {
  return String(n).padStart(2, '0')
}

function parseMonth(month: string): { year: number; monthIndex: number } {
  const [year, monthNumber] = month.split('-').map(Number)
  return { year, monthIndex: monthNumber - 1 }
}

function daysInMonth(month: string): number {
  const { year, monthIndex } = parseMonth(month)
  return new Date(Date.UTC(year, monthIndex + 1, 0)).getUTCDate()
}

function dayOfMonth(month: string, day: number): string {
  return `${month}-${pad(day)}`
}

export function monthOf(isoDate: string): string {
  return isoDate.slice(0, 7)
}

export function shiftMonth(month: string, delta: number): string {
  const { year, monthIndex } = parseMonth(month)
  const total = year * 12 + monthIndex + delta
  const shiftedYear = Math.floor(total / 12)
  const shiftedMonth = total - shiftedYear * 12 + 1
  return `${String(shiftedYear).padStart(4, '0')}-${pad(shiftedMonth)}`
}

export function monthRange(month: string): { from: string; to: string } {
  return { from: dayOfMonth(month, 1), to: dayOfMonth(month, daysInMonth(month)) }
}

export function canGoNext(month: string, today: string): boolean {
  return month < monthOf(today)
}

export function monthTitle(month: string): string {
  const { year, monthIndex } = parseMonth(month)
  return new Date(Date.UTC(year, monthIndex, 1)).toLocaleDateString('en-GB', {
    month: 'long',
    year: 'numeric',
    timeZone: 'UTC',
  })
}

export function buildMonthGrid(month: string, today: string): MonthCell[] {
  const { year, monthIndex } = parseMonth(month)
  const leading = (new Date(Date.UTC(year, monthIndex, 1)).getUTCDay() + 6) % 7
  const total = daysInMonth(month)
  const cells: MonthCell[] = Array.from({ length: leading }, () => null)
  for (let day = 1; day <= total; day += 1) {
    const date = dayOfMonth(month, day)
    cells.push({ date, day, future: date > today })
  }
  while (cells.length % 7 !== 0) cells.push(null)
  return cells
}

export function countMonth(month: string, entries: readonly DietEntry[], today: string): MonthCounts {
  const counts: MonthCounts = { onPlan: 0, more: 0, less: 0, outside: 0, notLogged: 0 }
  const logged = new Set<string>()
  for (const entry of entries) {
    if (monthOf(entry.date) !== month || logged.has(entry.date)) continue
    logged.add(entry.date)
    if (entry.status === 'ON_PLAN') counts.onPlan += 1
    else if (entry.status === 'MORE') counts.more += 1
    else if (entry.status === 'LESS') counts.less += 1
    else counts.outside += 1
  }
  const total = daysInMonth(month)
  for (let day = 1; day <= total; day += 1) {
    const date = dayOfMonth(month, day)
    if (date <= today && !logged.has(date)) counts.notLogged += 1
  }
  return counts
}

export function dayLabel(isoDate: string, today: string): string {
  const text = new Date(`${isoDate}T00:00:00Z`).toLocaleDateString('en-GB', {
    day: 'numeric',
    month: 'long',
    timeZone: 'UTC',
  })
  return isoDate === today ? `Today · ${text}` : text
}

export function dayAriaLabel(isoDate: string, status: DietStatus | null | undefined): string {
  const text = dayLabel(isoDate, '')
  return status ? `${text}, ${DIET_STATUSES[status].counterLabel}` : text
}

export function doneRequest(
  savedStatus: DietStatus | null,
  savedComment: string | null,
  typedComment: string,
): DoneRequest | null {
  const typed = typedComment.trim() === '' ? null : typedComment.trim()
  if (typed === savedComment) return null
  return { status: savedStatus ?? 'ON_PLAN', comment: typed }
}

export function withEntry(entries: readonly DietEntry[], saved: DietEntry): DietEntry[] {
  return [...entries.filter((entry) => entry.date !== saved.date), saved].sort((a, b) =>
    a.date.localeCompare(b.date),
  )
}

export function withoutEntry(entries: readonly DietEntry[], date: string): DietEntry[] {
  return entries.filter((entry) => entry.date !== date)
}
