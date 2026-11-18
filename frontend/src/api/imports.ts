import { http } from './client'
import type { ImportConfirmRequest, ImportPreview, ImportResult } from './types'

export const importsApi = {
  preview: (file: File, year?: number) => {
    const body = new FormData()
    body.append('file', file)
    return http.upload<ImportPreview>(`/api/imports/preview${year ? `?year=${year}` : ''}`, body)
  },
  confirm: (request: ImportConfirmRequest) => http.post<ImportResult>('/api/imports/confirm', request),
}
