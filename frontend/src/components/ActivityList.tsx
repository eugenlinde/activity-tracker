import { useState } from 'react'
import { Button } from 'antd'
import { DeleteOutlined, DownOutlined, EditOutlined, UpOutlined } from '@ant-design/icons'
import type { ActivityResponse } from '../api/types'
import { ActivityMedia } from './ActivityMedia'
import { ExerciseDetails } from './ExerciseDetails'
import { exerciseCount } from '../lib/details'
import { formatDuration } from '../lib/duration'
import { ACTIVITY_LABELS, formatDay, formatWeekdayTime } from '../lib/labels'

interface Props {
  activities: ActivityResponse[]
  onEdit?: (activity: ActivityResponse) => void
  onDelete?: (activity: ActivityResponse) => void
}

function summaryOf(activity: ActivityResponse): string {
  const names = (activity.details?.exerciseList ?? []).map((exercise) => exercise.name)
  if (names.length > 0) return names.join(' · ')
  return activity.notes ?? 'No details'
}

function titleOf(activity: ActivityResponse): string {
  const label = ACTIVITY_LABELS[activity.activityType]
  return activity.duration === null ? label : `${label} · ${formatDuration(activity.duration)}`
}

export function ActivityList({ activities, onEdit, onDelete }: Props) {
  const [expanded, setExpanded] = useState<ReadonlySet<number>>(new Set())

  function toggle(id: number) {
    setExpanded((current) => {
      const next = new Set(current)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  if (activities.length === 0) return <div className="empty">No workouts yet.</div>

  return (
    <div className="stack">
      {activities.map((a) => {
        const open = expanded.has(a.id)
        return (
          <article key={a.id} className="workout">
            <div className="workout-head">
              <button
                type="button"
                className="workout-toggle"
                onClick={() => toggle(a.id)}
                aria-expanded={open}
              >
                <span>
                  <span className="workout-day">{formatDay(a.startedAt)}</span>
                  <br />
                  <span className="workout-weekday">{formatWeekdayTime(a.startedAt)}</span>
                </span>
                <span style={{ minWidth: 0 }}>
                  <span className="workout-title">{titleOf(a)}</span>
                  <span className="workout-summary">{summaryOf(a)}</span>
                </span>
                <span className="muted" aria-hidden="true">
                  {open ? <UpOutlined /> : <DownOutlined />}
                </span>
              </button>
              {(onEdit || onDelete) && (
                <div className="workout-actions">
                  {onEdit && (
                    <Button type="text" aria-label="Edit workout" icon={<EditOutlined />} onClick={() => onEdit(a)} />
                  )}
                  {onDelete && (
                    <Button
                      type="text"
                      danger
                      aria-label="Delete workout"
                      icon={<DeleteOutlined />}
                      onClick={() => onDelete(a)}
                    />
                  )}
                </div>
              )}
            </div>
            {open && (
              <div className="workout-body">
                {a.notes && (
                  <div>
                    <h3 className="subhead">Notes</h3>
                    {a.notes}
                  </div>
                )}
                <div>
                  <h3 className="subhead">Exercises{exerciseCount(a.details) > 0 ? ` (${exerciseCount(a.details)})` : ''}</h3>
                  <ExerciseDetails details={a.details} />
                </div>
                <div>
                  <h3 className="subhead">Media</h3>
                  <ActivityMedia activityId={a.id} editable={Boolean(onEdit)} />
                </div>
              </div>
            )}
          </article>
        )
      })}
    </div>
  )
}
