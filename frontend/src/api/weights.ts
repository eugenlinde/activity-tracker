import { http } from './client'
import type { WeightEntry } from './types'

export const weightsApi = {
  list: (from: string, to: string) => http.get<WeightEntry[]>(`/api/weights?from=${from}&to=${to}`),
  save: (date: string, weightKg: number) => http.put<WeightEntry>(`/api/weights/${date}`, { weightKg }),
  remove: (date: string) => http.delete(`/api/weights/${date}`),
}
