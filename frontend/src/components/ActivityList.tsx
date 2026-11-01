import type { ActivityResponse } from '../api/types'
import { ActivityMedia } from './ActivityMedia'
import { formatDateTime } from '../lib/datetime'
import { formatDuration } from '../lib/duration'

interface Props {
  activities: ActivityResponse[]
  onEdit?: (activity: ActivityResponse) => void
  onDelete?: (activity: ActivityResponse) => void
}

export function ActivityList({ activities, onEdit, onDelete }: Props) {
  if (activities.length === 0) return <p className="muted">No activities yet.</p>

  return (
    <table>
      <thead>
        <tr>
          <th>Started</th>
          <th>Type</th>
          <th>Duration</th>
          <th>Notes</th>
          <th>Media</th>
          {(onEdit || onDelete) && <th />}
        </tr>
      </thead>
      <tbody>
        {activities.map((a) => (
          <tr key={a.id}>
            <td>{formatDateTime(a.startedAt)}</td>
            <td>{a.activityType}</td>
            <td>{formatDuration(a.duration)}</td>
            <td>{a.notes}</td>
            <td>
              <ActivityMedia activityId={a.id} editable={Boolean(onEdit)} />
            </td>
            {(onEdit || onDelete) && (
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
        ))}
      </tbody>
    </table>
  )
}
