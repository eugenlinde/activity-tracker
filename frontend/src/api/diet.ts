import { http } from './client'
import type { DietEntry, DietStatus } from './types'

export const dietApi = {
  list: (from: string, to: string) => http.get<DietEntry[]>(`/api/diet?from=${from}&to=${to}`),
  save: (date: string, status: DietStatus, comment: string | null) =>
    http.put<DietEntry>(`/api/diet/${date}`, { status, comment }),
  remove: (date: string) => http.delete(`/api/diet/${date}`),
}
