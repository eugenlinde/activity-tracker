import { http } from './client'
import type { ImportConfirmRequest, ImportPreview, ImportResult } from './types'

export const importsApi = {
  preview: (file: File) => {
    const body = new FormData()
    body.append('file', file)
    return http.upload<ImportPreview>('/api/imports/preview', body)
  },
  confirm: (request: ImportConfirmRequest) => http.post<ImportResult>('/api/imports/confirm', request),
}
