import { useCallback, useEffect, useState } from 'react'
import { App, Button, Modal } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { activitiesApi } from '../api/activities'
import type { ActivityRequest, ActivityResponse } from '../api/types'
import { ActivityForm } from '../components/ActivityForm'
import { ActivityList } from '../components/ActivityList'
import { ErrorMessage } from '../components/ErrorMessage'

export function ActivitiesPage() {
  const { modal } = App.useApp()
  const [activities, setActivities] = useState<ActivityResponse[]>([])
  const [editing, setEditing] = useState<ActivityResponse | null>(null)
  const [creating, setCreating] = useState(false)
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
    setCreating(false)
    await reload()
  }

  async function update(body: ActivityRequest) {
    if (!editing) return
    await activitiesApi.update(editing.id, body)
    setEditing(null)
    await reload()
  }

  function remove(activity: ActivityResponse) {
    modal.confirm({
      title: 'Delete this workout?',
      okText: 'Delete',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await activitiesApi.remove(activity.id)
          await reload()
        } catch (err) {
          setError(err)
        }
      },
    })
  }

  function closeForm() {
    setCreating(false)
    setEditing(null)
  }

  const sorted = [...activities].sort((a, b) => b.startedAt.localeCompare(a.startedAt))
  const formOpen = creating || editing !== null

  return (
    <>
      <header className="page-header">
        <div>
          <div className="page-kicker">
            {sorted.length} {sorted.length === 1 ? 'session' : 'sessions'}
          </div>
          <h1>Workouts</h1>
        </div>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreating(true)}>
          New workout
        </Button>
      </header>
      <ErrorMessage error={error} />
      <ActivityList activities={sorted} onEdit={setEditing} onDelete={remove} />
      <Modal
        open={formOpen}
        title={editing ? 'Edit workout' : 'New workout'}
        onCancel={closeForm}
        footer={null}
        destroyOnHidden
        width={640}
      >
        {editing ? (
          <ActivityForm key={editing.id} initial={editing} onSubmit={update} onCancel={closeForm} />
        ) : (
          <ActivityForm key="new" onSubmit={create} onCancel={closeForm} />
        )}
      </Modal>
    </>
  )
}
