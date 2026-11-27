import { useState, type FormEvent } from 'react'
import { Button, Input, Modal, Select } from 'antd'
import type { ActivityRequest, ActivityType } from '../api/types'
import { AEROBIC_KINDS, aerobicStartedAt } from '../lib/aerobic'
import { todayIso } from '../lib/dates'
import { minutesToIso } from '../lib/duration'
import { ACTIVITY_LABELS } from '../lib/labels'
import { ErrorMessage } from './ErrorMessage'
import { Field } from './Field'

interface Props {
  open: boolean
  onSubmit: (body: ActivityRequest) => Promise<unknown>
  onClose: () => void
}

const KIND_OPTIONS = AEROBIC_KINDS.map((kind) => ({ value: kind, label: ACTIVITY_LABELS[kind] }))

export function AerobicForm({ open, onClose, onSubmit }: Props) {
  return (
    <Modal open={open} title="Add aerobic" onCancel={onClose} footer={null} destroyOnHidden width={440}>
      <AerobicFormBody onClose={onClose} onSubmit={onSubmit} />
    </Modal>
  )
}

function AerobicFormBody({ onClose, onSubmit }: Omit<Props, 'open'>) {
  const [kind, setKind] = useState<ActivityType>('RUNNING')
  const [date, setDate] = useState(todayIso())
  const [minutes, setMinutes] = useState('30')
  const [note, setNote] = useState('')
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)

  async function save(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await onSubmit({
        activityType: kind,
        startedAt: aerobicStartedAt(date, new Date()),
        duration: minutesToIso(Number(minutes)),
        details: null,
        notes: note.trim() === '' ? null : note.trim(),
      })
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="form" onSubmit={save}>
      <Field label="Activity">
        <Select<ActivityType> value={kind} onChange={setKind} options={KIND_OPTIONS} />
      </Field>
      <div className="form-grid">
        <Field label="Date">
          <Input type="date" max={todayIso()} value={date} onChange={(e) => setDate(e.target.value)} required />
        </Field>
        <Field label="Minutes">
          <Input
            type="number"
            inputMode="numeric"
            min={1}
            max={600}
            step={1}
            value={minutes}
            onChange={(e) => setMinutes(e.target.value)}
            required
          />
        </Field>
      </div>
      <Field label="Note (optional)">
        <Input value={note} maxLength={255} placeholder="Easy pace, Zone 2…" onChange={(e) => setNote(e.target.value)} />
      </Field>
      <ErrorMessage error={error} />
      <div className="form-actions">
        <Button onClick={onClose}>Cancel</Button>
        <Button type="primary" htmlType="submit" loading={busy}>
          Add
        </Button>
      </div>
    </form>
  )
}
