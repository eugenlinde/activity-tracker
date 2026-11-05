import { Fragment, useState } from 'react'
import type { ActivityResponse } from '../api/types'
import { ActivityMedia } from './ActivityMedia'
import { ExerciseDetails } from './ExerciseDetails'
import { exerciseCount } from '../lib/details'
import { formatDateTime } from '../lib/datetime'
import { formatDuration } from '../lib/duration'

interface Props {
  activities: ActivityResponse[]
  onEdit?: (activity: ActivityResponse) => void
  onDelete?: (activity: ActivityResponse) => void
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

  const hasActions = Boolean(onEdit || onDelete)
  const columnCount = hasActions ? 7 : 6

  if (activities.length === 0) return <p className="muted">No activities yet.</p>

  return (
    <table>
      <thead>
        <tr>
          <th>Started</th>
          <th>Type</th>
          <th>Duration</th>
          <th>Notes</th>
          <th>Exercises</th>
          <th>Media</th>
          {hasActions && <th />}
        </tr>
      </thead>
      <tbody>
        {activities.map((a) => (
          <Fragment key={a.id}>
            <tr>
              <td>{formatDateTime(a.startedAt)}</td>
              <td>{a.activityType}</td>
              <td>{formatDuration(a.duration)}</td>
              <td>{a.notes}</td>
              <td>
                {exerciseCount(a.details) > 0 ? (
                  <button
                    type="button"
                    className="link"
                    onClick={() => toggle(a.id)}
                    aria-expanded={expanded.has(a.id)}
                  >
                    {expanded.has(a.id) ? 'Hide' : 'Show'} ({exerciseCount(a.details)})
                  </button>
                ) : (
                  <span className="muted">—</span>
                )}
              </td>
              <td>
                <ActivityMedia activityId={a.id} editable={Boolean(onEdit)} />
              </td>
              {hasActions && (
                <td className="row">
                  {onEdit && (
                    <button type="button" onClick={() => onEdit(a)}>
                      Edit
                    </button>
                  )}
                  {onDelete && (
                    <button type="button" onClick={() => onDelete(a)}>
                      Delete
                    </button>
                  )}
                </td>
              )}
            </tr>
            {expanded.has(a.id) && (
              <tr className="details-row">
                <td colSpan={columnCount}>
                  <ExerciseDetails details={a.details} />
                </td>
              </tr>
            )}
          </Fragment>
        ))}
      </tbody>
    </table>
  )
}
