import { useState, type ChangeEvent, type DragEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { Alert, Button, Checkbox, Input, Steps, Tag } from 'antd'
import { InboxOutlined } from '@ant-design/icons'
import { importsApi } from '../api/imports'
import type { ImportPreview, ImportResult } from '../api/types'
import { ErrorMessage } from '../components/ErrorMessage'
import { ExerciseDetails } from '../components/ExerciseDetails'
import { formatShortDate, todayIso } from '../lib/dates'
import {
  initialSelection,
  selectedCounts,
  toConfirmRequest,
  workoutDateProblem,
  workoutsWithDateProblem,
  type ImportSelection,
} from '../lib/importing'

type Stage = 'upload' | 'review' | 'done'

const STAGES: Stage[] = ['upload', 'review', 'done']

function plural(count: number, one: string, many: string): string {
  return `${count} ${count === 1 ? one : many}`
}

export function ImportPage() {
  const navigate = useNavigate()
  const [stage, setStage] = useState<Stage>('upload')
  const [fileName, setFileName] = useState('')
  const [preview, setPreview] = useState<ImportPreview | null>(null)
  const [selection, setSelection] = useState<ImportSelection>({ workouts: [], weights: [], steps: [] })
  const [result, setResult] = useState<ImportResult | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)
  const [dragging, setDragging] = useState(false)

  async function read(file: File) {
    setError(null)
    setBusy(true)
    try {
      const read = await importsApi.preview(file)
      setFileName(file.name)
      setPreview(read)
      setSelection(initialSelection(read))
      setStage('review')
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  function onChoose(e: ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (file) void read(file)
  }

  function onDrop(e: DragEvent<HTMLLabelElement>) {
    e.preventDefault()
    setDragging(false)
    const file = e.dataTransfer.files?.[0]
    if (file && !busy) void read(file)
  }

  function startOver() {
    setStage('upload')
    setPreview(null)
    setResult(null)
    setError(null)
  }

  async function confirm() {
    if (!preview) return
    setError(null)
    setBusy(true)
    try {
      setResult(await importsApi.confirm(toConfirmRequest(preview, selection)))
      setStage('done')
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  function setWorkout(index: number, change: Partial<ImportSelection['workouts'][number]>) {
    setSelection((current) => ({
      ...current,
      workouts: current.workouts.map((workout, i) => (i === index ? { ...workout, ...change } : workout)),
    }))
  }

  function setRow(kind: 'weights' | 'steps', index: number, include: boolean) {
    setSelection((current) => ({
      ...current,
      [kind]: current[kind].map((value, i) => (i === index ? include : value)),
    }))
  }

  function setAllRows(kind: 'weights' | 'steps', include: boolean) {
    setSelection((current) => ({ ...current, [kind]: current[kind].map(() => include) }))
  }

  const counts = selectedCounts(selection)
  const today = todayIso()
  const dateProblems = workoutsWithDateProblem(selection, today)
  const nothingFound =
    preview !== null && preview.workouts.length === 0 && preview.weights.length === 0 && preview.steps.length === 0

  return (
    <>
      <header className="page-header">
        <div>
          <div className="page-kicker">CSV from your spreadsheet · you confirm before anything is written</div>
          <h1>Import</h1>
        </div>
      </header>
      <Steps
        size="small"
        current={STAGES.indexOf(stage)}
        items={[{ title: 'Upload' }, { title: 'Review' }, { title: 'Done' }]}
      />
      <ErrorMessage error={error} />

      {stage === 'upload' && (
        <section className="panel">
          <label
            className={dragging ? 'import-drop import-drop-active' : 'import-drop'}
            onDragOver={(e) => {
              e.preventDefault()
              setDragging(true)
            }}
            onDragLeave={() => setDragging(false)}
            onDrop={onDrop}
          >
            <InboxOutlined aria-hidden="true" style={{ fontSize: 32 }} />
            <strong>{busy ? 'Reading the file…' : 'Drop a CSV file here, or click to choose'}</strong>
            <span className="muted">.csv · workouts, body weight or daily steps · any column layout</span>
            <input type="file" accept=".csv,text/csv" onChange={onChoose} disabled={busy} hidden />
          </label>
          <p className="muted">
            To be read, the file's content is sent to an AI service (OpenRouter). Nothing is saved until you
            confirm on the next step. In Excel: File → Save As → CSV.
          </p>
        </section>
      )}

      {stage === 'review' && preview && (
        <>
          <section className="panel">
            <div className="panel-head">
              <h2>{fileName}</h2>
              <Button onClick={startOver} disabled={busy}>
                Choose another file
              </Button>
            </div>
            {nothingFound && (
              <div className="empty">
                {preview.unchangedWeights + preview.unchangedSteps > 0
                  ? 'Everything in this file is already in the app.'
                  : 'Nothing to import was found in this file.'}
              </div>
            )}
            {preview.unchangedWeights + preview.unchangedSteps > 0 && !nothingFound && (
              <p className="muted">
                Left out because the app already has the same value:{' '}
                {plural(preview.unchangedWeights, 'weight entry', 'weight entries')},{' '}
                {plural(preview.unchangedSteps, 'steps entry', 'steps entries')}.
              </p>
            )}
            {preview.unreadableRows > 0 && (
              <Alert
                type="warning"
                showIcon
                title={`${plural(preview.unreadableRows, 'value', 'values')} could not be read (not a date, out of range, or in the future) and ${preview.unreadableRows === 1 ? 'was' : 'were'} skipped.`}
              />
            )}
          </section>

          {preview.workouts.length > 0 && (
            <section className="panel">
              <div className="panel-head">
                <h2>Workouts</h2>
                <span>
                  {counts.workouts} of {preview.workouts.length} selected
                </span>
              </div>
              <div className="stack">
                {preview.workouts.map((workout, index) => (
                  <div className="import-workout" key={index}>
                    <div className="row">
                      <Checkbox
                        checked={selection.workouts[index].include}
                        onChange={(e) => setWorkout(index, { include: e.target.checked })}
                      >
                        <strong>{workout.label}</strong>
                      </Checkbox>
                      {workout.alreadyImported && <Tag color="gold">already imported</Tag>}
                      <Input
                        type="date"
                        aria-label={`Date for ${workout.label}`}
                        style={{ width: 170, marginLeft: 'auto' }}
                        max={today}
                        value={selection.workouts[index].date}
                        status={
                          workoutDateProblem(selection.workouts[index], today) === 'future'
                            ? 'error'
                            : workoutDateProblem(selection.workouts[index], today) === 'missing'
                              ? 'warning'
                              : undefined
                        }
                        onChange={(e) => setWorkout(index, { date: e.target.value })}
                      />
                    </div>
                    {workout.notes && <div className="muted">{workout.notes}</div>}
                    <ExerciseDetails details={workout.details} />
                  </div>
                ))}
              </div>
            </section>
          )}

          {preview.weights.length > 0 && (
            <section className="panel">
              <div className="panel-head">
                <h2>Weight</h2>
                <span className="row">
                  {counts.weights} of {preview.weights.length} selected
                  <Button size="small" onClick={() => setAllRows('weights', true)}>
                    All
                  </Button>
                  <Button size="small" onClick={() => setAllRows('weights', false)}>
                    None
                  </Button>
                </span>
              </div>
              <div className="import-rows">
                {preview.weights.map((row, index) => (
                  <Checkbox
                    key={row.date}
                    checked={selection.weights[index]}
                    onChange={(e) => setRow('weights', index, e.target.checked)}
                  >
                    <span className="import-date">{formatShortDate(row.date)} {row.date.slice(0, 4)}</span>
                    {row.existingWeightKg === null ? (
                      <span>{row.weightKg} kg</span>
                    ) : (
                      <span>
                        <span className="muted">{row.existingWeightKg} →</span> {row.weightKg} kg{' '}
                        <Tag color="gold">replaces</Tag>
                      </span>
                    )}
                  </Checkbox>
                ))}
              </div>
            </section>
          )}

          {preview.steps.length > 0 && (
            <section className="panel">
              <div className="panel-head">
                <h2>Steps</h2>
                <span className="row">
                  {counts.steps} of {preview.steps.length} selected
                  <Button size="small" onClick={() => setAllRows('steps', true)}>
                    All
                  </Button>
                  <Button size="small" onClick={() => setAllRows('steps', false)}>
                    None
                  </Button>
                </span>
              </div>
              <div className="import-rows">
                {preview.steps.map((row, index) => (
                  <Checkbox
                    key={row.date}
                    checked={selection.steps[index]}
                    onChange={(e) => setRow('steps', index, e.target.checked)}
                  >
                    <span className="import-date">{formatShortDate(row.date)} {row.date.slice(0, 4)}</span>
                    {row.existingSteps === null ? (
                      <span>{row.steps.toLocaleString('en-GB')}</span>
                    ) : (
                      <span>
                        <span className="muted">{row.existingSteps.toLocaleString('en-GB')} →</span>{' '}
                        {row.steps.toLocaleString('en-GB')} <Tag color="gold">replaces</Tag>
                      </span>
                    )}
                  </Checkbox>
                ))}
              </div>
            </section>
          )}

          {!nothingFound && (
            <section className="panel">
              <div className="row" style={{ justifyContent: 'space-between' }}>
                <span>
                  {plural(counts.workouts, 'workout', 'workouts')}, {plural(counts.weights, 'weight entry', 'weight entries')},{' '}
                  {plural(counts.steps, 'steps entry', 'steps entries')} will be written.
                  {dateProblems > 0 && (
                    <span className="muted">
                      {' '}
                      {plural(dateProblems, 'selected workout needs', 'selected workouts need')} a date that is not in the
                      future.
                    </span>
                  )}
                </span>
                <Button
                  type="primary"
                  loading={busy}
                  disabled={counts.total === 0 || dateProblems > 0}
                  onClick={() => void confirm()}
                >
                  Import {plural(counts.total, 'entry', 'entries')}
                </Button>
              </div>
            </section>
          )}
        </>
      )}

      {stage === 'done' && result && (
        <section className="panel">
          <div className="panel-head">
            <h2>Imported</h2>
          </div>
          <p>
            {plural(result.workouts, 'workout', 'workouts')}, {plural(result.weights, 'weight entry', 'weight entries')},{' '}
            {plural(result.steps, 'steps entry', 'steps entries')}.
          </p>
          <div className="row">
            <Button type="primary" onClick={() => navigate('/')}>
              Open dashboard
            </Button>
            <Button onClick={() => navigate('/activities')}>See workouts</Button>
            <Button onClick={startOver}>Import another file</Button>
          </div>
        </section>
      )}
    </>
  )
}
