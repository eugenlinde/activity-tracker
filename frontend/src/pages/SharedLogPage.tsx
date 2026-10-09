import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { activitiesApi } from '../api/activities'
import type { ActivityResponse } from '../api/types'
import { ActivityList } from '../components/ActivityList'
import { ErrorMessage } from '../components/ErrorMessage'

export function SharedLogPage() {
  const { ownerId } = useParams()
  const [activities, setActivities] = useState<ActivityResponse[]>([])
  const [error, setError] = useState<unknown>(null)

  useEffect(() => {
    const id = Number(ownerId)
    if (!Number.isFinite(id)) return
    activitiesApi
      .list(id)
      .then((list) => {
        setActivities(list)
        setError(null)
      })
      .catch(setError)
  }, [ownerId])

  const sorted = [...activities].sort((a, b) => b.startedAt.localeCompare(a.startedAt))

  return (
    <>
      <p>
        <Link to="/shares">← Back to sharing</Link>
      </p>
      <h1>Shared log (user {ownerId})</h1>
      <ErrorMessage error={error} />
      {!error && <ActivityList activities={sorted} />}
    </>
  )
}
