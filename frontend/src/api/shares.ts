import { http } from './client'
import type { ShareRequest, ShareResponse } from './types'

export const sharesApi = {
  given: () => http.get<ShareResponse[]>('/api/shares'),
  received: () => http.get<ShareResponse[]>('/api/shares/received'),
  grant: (body: ShareRequest) => http.post<void>('/api/shares', body),
  revoke: (id: number) => http.delete(`/api/shares/${id}`),
}
