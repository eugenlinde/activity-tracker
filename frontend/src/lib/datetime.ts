function pad(n: number): string {
  return String(n).padStart(2, '0')
}

export function isoToLocalInput(iso: string): string {
  const d = new Date(iso)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

export function localInputToIso(value: string): string {
  return new Date(value).toISOString()
}

export function nowLocalInput(): string {
  return isoToLocalInput(new Date().toISOString())
}

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString()
}
