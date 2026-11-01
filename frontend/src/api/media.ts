import { http } from './client'
import type { MediaResponse } from './types'

export const mediaApi = {
  list: (activityId: number) => http.get<MediaResponse[]>(`/api/activities/${activityId}/media`),
  upload: (activityId: number, file: File) => {
    const body = new FormData()
    body.append('file', file)
    return http.upload<MediaResponse>(`/api/activities/${activityId}/media`, body)
  },
  download: (id: number) => http.blob(`/api/media/${id}`),
  remove: (id: number) => http.delete(`/api/media/${id}`),
}
