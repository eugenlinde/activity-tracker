const ISO_DURATION = /^PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?$/

export function isoToMinutes(iso: string): number {
  const match = ISO_DURATION.exec(iso)
  if (!match) return 0
  const hours = Number(match[1] ?? 0)
  const minutes = Number(match[2] ?? 0)
  const seconds = Number(match[3] ?? 0)
  return Math.round(hours * 60 + minutes + seconds / 60)
}

export function minutesToIso(minutes: number): string {
  return `PT${Math.max(0, Math.round(minutes))}M`
}

export function formatDuration(iso: string): string {
  const total = isoToMinutes(iso)
  const hours = Math.floor(total / 60)
  const minutes = total % 60
  if (hours === 0) return `${minutes} min`
  if (minutes === 0) return `${hours} h`
  return `${hours} h ${minutes} min`
}
