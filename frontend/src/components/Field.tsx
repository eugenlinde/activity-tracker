import type { ReactNode } from 'react'

interface Props {
  label: string
  children: ReactNode
}

export function Field({ label, children }: Props) {
  return (
    <label className="field">
      <span className="field-label">{label}</span>
      {children}
    </label>
  )
}
