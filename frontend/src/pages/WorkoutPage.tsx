import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Button, Modal, Tag } from 'antd'
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

function BackLink() {
  return (
    <Link to={ACTIVITIES_ROUTE} className="back-link">
      All workouts
    </Link>
  )
}

export function WorkoutPage() {
  const { id: idParam } = useParams()
  const id = idParam !== undefined && /^\d+$/.test(idParam) ? Number(idParam) : null
  const [activity, setActivity] = useState<ActivityResponse | null>(null)
  const [loaded, setLoaded] = useState(false)
  const [notFound, setNotFound] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [editing, setEditing] = useState(false)

  const load = useCallback(async () => {
    if (id === null) return
    try {
      setActivity(await activitiesApi.get(id))
      setNotFound(false)
      setError(null)
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) {
        setActivity(null)
        setNotFound(true)
        setError(null)
      } else {
        setError(err)
      }
    } finally {
      setLoaded(true)
    }
  }, [id])

  useEffect(() => {
    setActivity(null)
    setNotFound(false)
    setError(null)
    setLoaded(false)
    void load()
  }, [load])

  async function update(body: ActivityRequest) {
    if (id === null) return
    await activitiesApi.update(id, body)
    setEditing(false)
    await load()
  }

  if (id === null) {
    return (
      <>
        <BackLink />
        <p>Workout not found</p>
      </>
    )
  }

  if (!loaded) return <BackLink />

  if (notFound || (activity !== null && activity.activityType !== 'WEIGHTS')) {
    return (
      <>
        <BackLink />
        <p>Workout not found</p>
      </>
    )
  }

  if (activity === null) {
    return (
      <>
        <BackLink />
        <ErrorMessage error={error} />
      </>
    )
  }

  const exercises = activity.details?.exerciseList ?? []

  return (
    <>
      <BackLink />
      <ErrorMessage error={error} />
      <header className="page-header">
        <div>
          <div className="page-kicker">{formatWorkoutWhen(activity.startedAt, activity.duration)}</div>
          <h1>Workout</h1>
        </div>
        <Button onClick={() => setEditing(true)}>Edit</Button>
      </header>
      {activity.notes && <p className="workout-notes">{activity.notes}</p>}
      <div className="workout-strip">
        <div className="stat-block">
          <span className="stat">
            {activity.duration === null ? '—' : isoToMinutes(activity.duration)}
            {activity.duration !== null && <span className="stat-unit">min</span>}
          </span>
          <span className="stat-label">Duration</span>
        </div>
        <div className="stat-block">
          <span className="stat">{exerciseCount(activity.details)}</span>
          <span className="stat-label">Exercises</span>
        </div>
        <div className="stat-block">
          <span className="stat">{setCount(activity.details)}</span>
          <span className="stat-label">Sets</span>
        </div>
        <div className="stat-block">
          <span className="stat">
            {formatVolume(workoutVolume(activity.details))}
            <span className="stat-unit">kg</span>
          </span>
          <span className="stat-label">Volume</span>
        </div>
      </div>
      {exercises.length === 0 ? (
        <span className="muted">No exercises recorded.</span>
      ) : (
        <div className="stack">
          {exercises.map((exercise, index) => {
            const target = formatTarget(exercise)
            const sets = exercise.workoutSetList ?? []
            return (
              <article key={index} className="exercise-card">
                <div className="exercise-card-index">{formatExerciseIndex(index)}</div>
                <div className="exercise-card-body">
                  <strong>
                    {exercise.videoUrl ? (
                      <a href={exercise.videoUrl} target="_blank" rel="noreferrer">
                        {exercise.name}
                      </a>
                    ) : (
                      exercise.name
                    )}
                  </strong>
                  {target && <div className="muted">{target}</div>}
                  {sets.length > 0 && (
                    <div className="sets">
                      {sets.map((set, setIndex) => (
                        <Tag key={setIndex} variant="filled">
                          {formatSetChip(set)}
                        </Tag>
                      ))}
                    </div>
                  )}
                  {exercise.comment && <div className="muted">{exercise.comment}</div>}
                </div>
              </article>
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
        <ActivityForm key={activity.id} initial={activity} onSubmit={update} onCancel={() => setEditing(false)} />
      </Modal>
    </>
  )
}
