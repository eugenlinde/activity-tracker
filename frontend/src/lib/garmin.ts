import type { GarminStatusResponse } from '../api/types'

export function garminStatusText(
  status: GarminStatusResponse,
  format: (iso: string) => string,
): string {
  if (status.lastSyncStatus === null || status.lastSyncAt === null) {
    return 'Waiting for the first fetch. It runs every hour.'
  }
  const at = format(status.lastSyncAt)
  if (status.lastSyncStatus === 'FAILED') {
    return `Last fetch failed at ${at}. The key may be wrong or expired: remove it and add a new one.`
  }
  return `Last fetch: ${at}.`
}
