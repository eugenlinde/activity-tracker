import { useCallback, useEffect, useState } from 'react'
import { activitiesApi } from '../api/activities'
import type { ActivityRequest, ActivityResponse } from '../api/types'
import { ActivityForm } from '../components/ActivityForm'
import { ActivityList } from '../components/ActivityList'
import { ErrorMessage } from '../components/ErrorMessage'

export function ActivitiesPage() {
  const [activities, setActivities] = useState<ActivityResponse[]>([])
  const [editing, setEditing] = useState<ActivityResponse | null>(null)
  const [error, setError] = useState<unknown>(null)

  const reload = useCallback(async () => {
    try {
      setActivities(await activitiesApi.list())
      setError(null)
    } catch (err) {
      setError(err)
    }
  }, [])

  useEffect(() => {
    void reload()
  }, [reload])

  async function create(body: ActivityRequest) {
    await activitiesApi.create(body)
    await reload()
  }

  async function update(body: ActivityRequest) {
    if (!editing) return
    await activitiesApi.update(editing.id, body)
    setEditing(null)
    await reload()
  }

  async function remove(activity: ActivityResponse) {
    if (!window.confirm('Delete this activity?')) return
    try {
      await activitiesApi.remove(activity.id)
      await reload()
    } catch (err) {
      setError(err)
    }
  }

  const sorted = [...activities].sort((a, b) => b.startedAt.localeCompare(a.startedAt))

  return (
    <>
      <h1>My activities</h1>
      {editing ? (
        <ActivityForm key={editing.id} initial={editing} onSubmit={update} onCancel={() => setEditing(null)} />
      ) : (
        <ActivityForm onSubmit={create} />
      )}
      <ErrorMessage error={error} />
      <ActivityList activities={sorted} onEdit={setEditing} onDelete={remove} />
    </>
  )
}
