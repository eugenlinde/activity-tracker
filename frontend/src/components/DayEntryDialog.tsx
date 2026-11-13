import { useState, type FormEvent } from 'react'
import { Button, Input, Modal } from 'antd'
import { todayIso } from '../lib/dates'
import { ErrorMessage } from './ErrorMessage'
import { Field } from './Field'

export interface DayValue {
  date: string
  value: number
}

interface Props {
  open: boolean
  title: string
  valueLabel: string
  min: number
  max: number
  step: number
  inputMode: 'decimal' | 'numeric'
  entries: DayValue[]
  describe: (value: number) => string
  onSave: (date: string, value: number) => Promise<unknown>
  onRemove: (date: string) => Promise<unknown>
  onClose: () => void
  onChanged: () => void
}

export function DayEntryDialog({ open, title, onClose, ...form }: Props) {
  return (
    <Modal open={open} title={title} onCancel={onClose} footer={null} destroyOnHidden width={420}>
      <DayEntryForm onClose={onClose} {...form} />
    </Modal>
  )
}

function DayEntryForm({
  valueLabel,
  min,
  max,
  step,
  inputMode,
  entries,
  describe,
  onSave,
  onRemove,
  onClose,
  onChanged,
}: Omit<Props, 'open' | 'title'>) {
  const [date, setDate] = useState(todayIso())
  const [value, setValue] = useState('')
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)

  const existing = entries.find((entry) => entry.date === date)

  async function run(action: () => Promise<unknown>) {
    setError(null)
    setBusy(true)
    try {
      await action()
      onChanged()
      onClose()
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  function save(e: FormEvent) {
    e.preventDefault()
    void run(() => onSave(date, Number(value)))
  }

  return (
    <form className="form" onSubmit={save}>
      <div className="form-grid">
        <Field label="Date">
          <Input type="date" max={todayIso()} value={date} onChange={(e) => setDate(e.target.value)} required />
        </Field>
        <Field label={valueLabel}>
          <Input
            type="number"
            inputMode={inputMode}
            min={min}
            max={max}
            step={step}
            value={value}
            onChange={(e) => setValue(e.target.value)}
            required
            autoFocus
          />
        </Field>
      </div>
      {existing && (
        <span className="muted">Already logged for this day: {describe(existing.value)}. Saving replaces it.</span>
      )}
      <ErrorMessage error={error} />
      <div className="form-actions">
        {existing && (
          <Button danger disabled={busy} onClick={() => void run(() => onRemove(date))}>
            Delete entry
          </Button>
        )}
        <Button onClick={onClose}>Cancel</Button>
        <Button type="primary" htmlType="submit" loading={busy}>
          Save
        </Button>
      </div>
    </form>
  )
}
