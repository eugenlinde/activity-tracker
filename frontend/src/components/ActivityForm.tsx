import { useState, type FormEvent } from 'react'
import { Button, Input, Select } from 'antd'
import { ACTIVITY_TYPES, type ActivityRequest, type ActivityResponse, type ActivityType } from '../api/types'
import { isoToLocalInput, localInputToIso, nowLocalInput } from '../lib/datetime'
import { fromDrafts, toDrafts, type ExerciseDraft } from '../lib/details'
import { isoToMinutes, minutesToIso } from '../lib/duration'
import { ACTIVITY_LABELS } from '../lib/labels'
import { ErrorMessage } from './ErrorMessage'
import { ExerciseEditor } from './ExerciseEditor'
import { Field } from './Field'

interface Props {
  initial?: ActivityResponse
  onSubmit: (body: ActivityRequest) => Promise<void>
  onCancel?: () => void
}

const TYPE_OPTIONS = ACTIVITY_TYPES.map((type) => ({ value: type, label: ACTIVITY_LABELS[type] }))

export function ActivityForm({ initial, onSubmit, onCancel }: Props) {
  const [activityType, setActivityType] = useState<ActivityType>(initial?.activityType ?? 'RUNNING')
  const [startedAt, setStartedAt] = useState(initial ? isoToLocalInput(initial.startedAt) : nowLocalInput())
  const [minutes, setMinutes] = useState(
    initial ? (initial.duration === null ? '' : String(isoToMinutes(initial.duration))) : '30',
  )
  const [exercises, setExercises] = useState<ExerciseDraft[]>(toDrafts(initial?.details ?? null))
  const [notes, setNotes] = useState(initial?.notes ?? '')
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await onSubmit({
        activityType,
        startedAt: localInputToIso(startedAt),
        duration: minutes.trim() === '' ? null : minutesToIso(Number(minutes)),
        details: fromDrafts(exercises),
        notes: notes.trim() === '' ? null : notes.trim(),
      })
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="form" onSubmit={handleSubmit}>
      <div className="form-grid">
        <Field label="Type">
          <Select<ActivityType> value={activityType} onChange={setActivityType} options={TYPE_OPTIONS} />
        </Field>
        <Field label="Started at">
          <Input
            type="datetime-local"
            value={startedAt}
            onChange={(e) => setStartedAt(e.target.value)}
            required
          />
        </Field>
        <Field label="Duration (minutes)">
          <Input
            type="number"
            inputMode="numeric"
            min={1}
            value={minutes}
            onChange={(e) => setMinutes(e.target.value)}
          />
        </Field>
      </div>
      <Field label="Notes">
        <Input value={notes} maxLength={255} onChange={(e) => setNotes(e.target.value)} />
      </Field>
      <ExerciseEditor value={exercises} onChange={setExercises} />
      <ErrorMessage error={error} />
      <div className="form-actions">
        {onCancel && <Button onClick={onCancel}>Cancel</Button>}
        <Button type="primary" htmlType="submit" loading={busy}>
          {initial ? 'Save' : 'Add workout'}
        </Button>
      </div>
    </form>
  )
}
