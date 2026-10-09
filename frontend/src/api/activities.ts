import { http } from './client'
import type { ActivityRequest, ActivityResponse } from './types'

export const activitiesApi = {
  list: (ownerId?: number) =>
    http.get<ActivityResponse[]>(
      ownerId === undefined ? '/api/activities' : `/api/activities?ownerId=${ownerId}`,
    ),
  get: (id: number) => http.get<ActivityResponse>(`/api/activities/${id}`),
  create: (body: ActivityRequest) => http.post<ActivityResponse>('/api/activities', body),
  update: (id: number, body: ActivityRequest) =>
    http.put<ActivityResponse>(`/api/activities/${id}`, body),
  remove: (id: number) => http.delete(`/api/activities/${id}`),
}
