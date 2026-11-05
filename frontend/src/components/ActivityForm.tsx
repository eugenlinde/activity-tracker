import { useState, type FormEvent } from 'react'
import { ACTIVITY_TYPES, type ActivityRequest, type ActivityResponse, type ActivityType } from '../api/types'
import { isoToLocalInput, localInputToIso, nowLocalInput } from '../lib/datetime'
import { fromDrafts, toDrafts, type ExerciseDraft } from '../lib/details'
import { isoToMinutes, minutesToIso } from '../lib/duration'
import { ErrorMessage } from './ErrorMessage'
import { ExerciseEditor } from './ExerciseEditor'

interface Props {
  initial?: ActivityResponse
  onSubmit: (body: ActivityRequest) => Promise<void>
  onCancel?: () => void
}

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
      if (!initial) {
        setNotes('')
        setStartedAt(nowLocalInput())
        setExercises([])
      }
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="card wide" onSubmit={handleSubmit}>
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
        <input type="number" min={1} value={minutes} onChange={(e) => setMinutes(e.target.value)} />
      </label>
      <label>
        Notes
        <input value={notes} maxLength={255} onChange={(e) => setNotes(e.target.value)} />
      </label>
      <ExerciseEditor value={exercises} onChange={setExercises} />
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
