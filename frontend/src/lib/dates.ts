const DAY_MS = 86_400_000

function pad(n: number): string {
  return String(n).padStart(2, '0')
}

export function toIsoDate(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

export function todayIso(): string {
  return toIsoDate(new Date())
}

function utcMillis(isoDate: string): number {
  return Date.parse(`${isoDate}T00:00:00Z`)
}

export function addDays(isoDate: string, days: number): string {
  return new Date(utcMillis(isoDate) + days * DAY_MS).toISOString().slice(0, 10)
}

export function daysBetween(fromIso: string, toIso: string): number {
  return Math.round((utcMillis(toIso) - utcMillis(fromIso)) / DAY_MS)
}

export function formatShortDate(isoDate: string): string {
  return new Date(utcMillis(isoDate)).toLocaleDateString('en-GB', {
    day: 'numeric',
    month: 'short',
    timeZone: 'UTC',
  })
}

export function formatLongToday(): string {
  return new Date().toLocaleDateString('en-GB', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  })
}

export function mondayOf(isoDate: string): string {
  const weekday = new Date(utcMillis(isoDate)).getUTCDay()
  return addDays(isoDate, -((weekday + 6) % 7))
}
