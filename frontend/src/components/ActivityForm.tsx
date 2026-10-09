import { useState, type FormEvent } from 'react'
import { ACTIVITY_TYPES, type ActivityRequest, type ActivityResponse, type ActivityType } from '../api/types'
import { isoToLocalInput, localInputToIso, nowLocalInput } from '../lib/datetime'
import { isoToMinutes, minutesToIso } from '../lib/duration'
import { ErrorMessage } from './ErrorMessage'

interface Props {
  initial?: ActivityResponse
  onSubmit: (body: ActivityRequest) => Promise<void>
  onCancel?: () => void
}

export function ActivityForm({ initial, onSubmit, onCancel }: Props) {
  const [activityType, setActivityType] = useState<ActivityType>(initial?.activityType ?? 'RUNNING')
  const [startedAt, setStartedAt] = useState(initial ? isoToLocalInput(initial.startedAt) : nowLocalInput())
  const [minutes, setMinutes] = useState(initial ? isoToMinutes(initial.duration) : 30)
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
        duration: minutesToIso(minutes),
        notes: notes.trim() === '' ? null : notes.trim(),
      })
      if (!initial) {
        setNotes('')
        setStartedAt(nowLocalInput())
      }
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="card" onSubmit={handleSubmit}>
      <label>
        Type
        <select value={activityType} onChange={(e) => setActivityType(e.target.value as ActivityType)}>
          {ACTIVITY_TYPES.map((t) => (
            <option key={t} value={t}>
              {t}
            </option>
          ))}
        </select>
      </label>
      <label>
        Started at
        <input type="datetime-local" value={startedAt} onChange={(e) => setStartedAt(e.target.value)} required />
      </label>
      <label>
        Duration (minutes)
        <input type="number" min={1} value={minutes} onChange={(e) => setMinutes(Number(e.target.value))} required />
      </label>
      <label>
        Notes
        <input value={notes} maxLength={255} onChange={(e) => setNotes(e.target.value)} />
      </label>
      <ErrorMessage error={error} />
      <div className="row">
        <button type="submit" disabled={busy}>
          {initial ? 'Save' : 'Add activity'}
        </button>
        {onCancel && (
          <button type="button" onClick={onCancel}>
            Cancel
          </button>
        )}
      </div>
    </form>
  )
}
