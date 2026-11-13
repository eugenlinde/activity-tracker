import { http } from './client'
import type { StepsEntry } from './types'

export const stepsApi = {
  list: (from: string, to: string) => http.get<StepsEntry[]>(`/api/steps?from=${from}&to=${to}`),
  save: (date: string, steps: number) => http.put<StepsEntry>(`/api/steps/${date}`, { steps }),
  remove: (date: string) => http.delete(`/api/steps/${date}`),
}
