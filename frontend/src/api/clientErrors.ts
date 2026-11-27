import type { ClientErrorReport } from '../lib/clientErrors'
import { tokenStore } from './client'

export function sendClientError(report: ClientErrorReport): Promise<Response> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  const token = tokenStore.get()
  if (token) headers.Authorization = `Bearer ${token}`
  return fetch('/api/client-errors', {
    method: 'POST',
    headers,
    body: JSON.stringify(report),
  })
}
