import { http } from './client'
import type { GarminKeyRequest, GarminStatusResponse } from './types'

export const garminApi = {
  status: () => http.get<GarminStatusResponse>('/api/garmin'),
  add: (body: GarminKeyRequest) => http.post<GarminStatusResponse>('/api/garmin', body),
  remove: () => http.delete('/api/garmin'),
}
