import { Alert } from 'antd'
import { ApiError } from '../api/client'

export function errorText(error: unknown): string {
  if (error instanceof ApiError) {
    const fields = error.validationErrors
    if (fields) {
      return Object.entries(fields)
        .map(([field, messages]) => `${field}: ${messages.join(', ')}`)
        .join('; ')
    }
    return error.message
  }
  if (error instanceof Error) return error.message
  return 'Something went wrong'
}

export function ErrorMessage({ error }: { error: unknown }) {
  if (!error) return null
  return <Alert type="error" showIcon title={errorText(error)} />
}
