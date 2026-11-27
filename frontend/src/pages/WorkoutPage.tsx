import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Button, Modal } from 'antd'
import { EditOutlined, LeftOutlined } from '@ant-design/icons'
import { activitiesApi } from '../api/activities'
import { ApiError } from '../api/client'
import type { ActivityRequest, ActivityResponse } from '../api/types'
import { ActivityForm } from '../components/ActivityForm'
import { ActivityMedia } from '../components/ActivityMedia'
import { ErrorMessage } from '../components/ErrorMessage'
import { exerciseCount } from '../lib/details'
import { isoToMinutes } from '../lib/duration'
import { ACTIVITIES_ROUTE } from '../lib/routes'
import {
  formatExerciseIndex,
  formatSetChip,
  formatTarget,
  formatVolume,
  formatWorkoutWhen,
  setCount,
  workoutVolume,
} from '../lib/workout'

type LoadState =
  | { status: 'loading' }
  | { status: 'notFound' }
  | { status: 'error'; error: unknown }
  | { status: 'ready'; activity: ActivityResponse }

function BackLink() {
  return (
    <div className="page-kicker">
      <Link to={ACTIVITIES_ROUTE}>
        <LeftOutlined /> All workouts
      </Link>
    </div>
  )
}

export function WorkoutPage() {
  const { id } = useParams()
  const [state, setState] = useState<LoadState>({ status: 'loading' })
  const [editing, setEditing] = useState(false)

  const load = useCallback(async () => {
    const numericId = Number(id)
    if (id === undefined || !/^\d+$/.test(id) || !Number.isSafeInteger(numericId)) {
      setState({ status: 'notFound' })
      return
    }
    try {
      const activity = await activitiesApi.get(numericId)
      setState(activity.activityType === 'WEIGHTS' ? { status: 'ready', activity } : { status: 'notFound' })
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) setState({ status: 'notFound' })
      else setState({ status: 'error', error: err })
    }
  }, [id])

  useEffect(() => {
    setState({ status: 'loading' })
    void load()
  }, [load])

  async function update(body: ActivityRequest) {
    if (state.status !== 'ready') return
    await activitiesApi.update(state.activity.id, body)
    setEditing(false)
    await load()
  }

  if (state.status === 'loading') return <BackLink />

  if (state.status === 'notFound') {
    return (
      <>
        <BackLink />
        <h1>Workout not found</h1>
      </>
    )
  }

  if (state.status === 'error') {
    return (
      <>
        <BackLink />
        <ErrorMessage error={state.error} />
      </>
    )
  }

  const { activity } = state
  const exercises = activity.details?.exerciseList ?? []

  return (
    <>
      <header className="page-header">
        <div>
          <BackLink />
          <div className="page-kicker">{formatWorkoutWhen(activity.startedAt, activity.duration)}</div>
          <h1>Workout</h1>
        </div>
        <Button icon={<EditOutlined />} onClick={() => setEditing(true)}>
          Edit
        </Button>
      </header>
      {activity.notes && <div className="workout-notes">{activity.notes}</div>}
      <div className="workout-strip">
        <div className="stat-block">
          <div>
            <span className="stat">{activity.duration === null ? '—' : isoToMinutes(activity.duration)}</span>
            {activity.duration !== null && <span className="stat-unit">min</span>}
          </div>
          <div className="stat-label">Duration</div>
        </div>
        <div className="stat-block">
          <div>
            <span className="stat">{exerciseCount(activity.details)}</span>
          </div>
          <div className="stat-label">Exercises</div>
        </div>
        <div className="stat-block">
          <div>
            <span className="stat">{setCount(activity.details)}</span>
          </div>
          <div className="stat-label">Sets</div>
        </div>
        <div className="stat-block">
          <div>
            <span className="stat">{formatVolume(workoutVolume(activity.details))}</span>
            <span className="stat-unit">kg</span>
          </div>
          <div className="stat-label">Volume</div>
        </div>
      </div>
      {exercises.length === 0 ? (
        <div className="muted">No exercises recorded.</div>
      ) : (
        <div className="stack">
          {exercises.map((exercise, index) => {
            const target = formatTarget(exercise)
            const exerciseSets = exercise.workoutSetList ?? []
            return (
              <section key={index} className="exercise-card">
                <div className="exercise-card-index">{formatExerciseIndex(index)}</div>
                <div className="exercise-card-main">
                  <h2 className="exercise-card-name">
                    {exercise.videoUrl ? (
                      <a href={exercise.videoUrl} target="_blank" rel="noreferrer">
                        {exercise.name}
                      </a>
                    ) : (
                      exercise.name
                    )}
                  </h2>
                  {target && <div className="muted">{target}</div>}
                  {exerciseSets.length > 0 && (
                    <div className="sets">
                      {exerciseSets.map((set, setIndex) => (
                        <span key={setIndex} className="set-chip">
                          {formatSetChip(set)}
                        </span>
                      ))}
                    </div>
                  )}
                  {exercise.comment && <div className="muted">{exercise.comment}</div>}
                </div>
              </section>
            )
          })}
        </div>
      )}
      <div>
        <h3 className="subhead">Media</h3>
        <ActivityMedia activityId={activity.id} editable />
      </div>
      <Modal
        open={editing}
        title="Edit workout"
        onCancel={() => setEditing(false)}
        footer={null}
        destroyOnHidden
        width={640}
      >
        <ActivityForm
          key={activity.id}
          initial={activity}
          onSubmit={update}
          onCancel={() => setEditing(false)}
        />
      </Modal>
    </>
  )
}
