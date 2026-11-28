import { describe, expect, it } from 'vitest'
import type { GarminStatusResponse } from '../api/types'
import { garminStatusText } from './garmin'

const format = (iso: string) => `<${iso}>`

function status(overrides: Partial<GarminStatusResponse>): GarminStatusResponse {
  return {
    connected: true,
    connectedAt: '2026-11-28T09:00:00Z',
    lastSyncAt: null,
    lastSyncStatus: null,
    ...overrides,
  }
}

describe('garminStatusText', () => {
  it('says it is waiting when nothing was fetched yet', () => {
    expect(garminStatusText(status({}), format)).toBe(
      'Waiting for the first fetch. It runs every hour.',
    )
  })

  it('shows the time of the last successful fetch', () => {
    const text = garminStatusText(
      status({ lastSyncAt: '2026-11-28T10:00:00Z', lastSyncStatus: 'OK' }),
      format,
    )
    expect(text).toBe('Last fetch: <2026-11-28T10:00:00Z>.')
  })

  it('shows the time of a failed fetch and what to do', () => {
    const text = garminStatusText(
      status({ lastSyncAt: '2026-11-28T11:00:00Z', lastSyncStatus: 'FAILED' }),
      format,
    )
    expect(text).toBe(
      'Last fetch failed at <2026-11-28T11:00:00Z>. The key may be wrong or expired: remove it and add a new one.',
    )
  })
})
