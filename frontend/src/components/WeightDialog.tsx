import { useState, type FormEvent } from 'react'
import { Button, Input, Modal } from 'antd'
import { weightsApi } from '../api/weights'
import type { WeightEntry } from '../api/types'
import { todayIso } from '../lib/dates'
import { ErrorMessage } from './ErrorMessage'
import { Field } from './Field'

interface Props {
  open: boolean
  entries: WeightEntry[]
  onClose: () => void
  onChanged: () => void
}

export function WeightDialog({ open, entries, onClose, onChanged }: Props) {
  return (
    <Modal open={open} title="Add weight" onCancel={onClose} footer={null} destroyOnHidden width={420}>
      <WeightForm entries={entries} onClose={onClose} onChanged={onChanged} />
    </Modal>
  )
}

function WeightForm({ entries, onClose, onChanged }: Omit<Props, 'open'>) {
  const [date, setDate] = useState(todayIso())
  const [weight, setWeight] = useState('')
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
    void run(() => weightsApi.save(date, Number(weight)))
  }

  return (
    <form className="form" onSubmit={save}>
      <div className="form-grid">
        <Field label="Date">
          <Input type="date" max={todayIso()} value={date} onChange={(e) => setDate(e.target.value)} required />
        </Field>
        <Field label="Weight (kg)">
          <Input
            type="number"
            inputMode="decimal"
            min={20}
            max={400}
            step={0.1}
            placeholder={existing ? String(existing.weightKg) : undefined}
            value={weight}
            onChange={(e) => setWeight(e.target.value)}
            required
            autoFocus
          />
        </Field>
      </div>
      {existing && (
        <span className="muted">
          {existing.weightKg.toFixed(1)} kg is already logged for this day. Saving replaces it.
        </span>
      )}
      <ErrorMessage error={error} />
      <div className="form-actions">
        {existing && (
          <Button danger disabled={busy} onClick={() => void run(() => weightsApi.remove(date))}>
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
