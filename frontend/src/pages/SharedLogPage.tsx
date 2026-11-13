import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { LeftOutlined } from '@ant-design/icons'
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
      <header className="page-header">
        <div>
          <div className="page-kicker">
            <Link to="/shares">
              <LeftOutlined /> Back to shares
            </Link>
          </div>
          <h1>Shared log (user {ownerId})</h1>
        </div>
      </header>
      <ErrorMessage error={error} />
      {!error && <ActivityList activities={sorted} />}
    </>
  )
}
