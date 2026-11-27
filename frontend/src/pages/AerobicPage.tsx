import { useCallback, useEffect, useState } from 'react'
import { App, Button } from 'antd'
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons'
import { activitiesApi } from '../api/activities'
import type { ActivityRequest } from '../api/types'
import { AerobicForm } from '../components/AerobicForm'
import { AerobicWeeksCard } from '../components/AerobicWeeksCard'
import { ErrorMessage } from '../components/ErrorMessage'
import { aerobicSummary, toAerobicEntries, type AerobicEntry } from '../lib/aerobic'
import { formatMonthName, formatShortDate, formatWeekday, todayIso } from '../lib/dates'
import { ACTIVITY_LABELS } from '../lib/labels'

export function AerobicPage() {
  const { modal } = App.useApp()
  const [entries, setEntries] = useState<AerobicEntry[]>([])
  const [creating, setCreating] = useState(false)
  const [error, setError] = useState<unknown>(null)

  const reload = useCallback(async () => {
    try {
      setEntries(toAerobicEntries(await activitiesApi.list()))
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

  function remove(entry: AerobicEntry) {
    modal.confirm({
      title: 'Delete this entry?',
      okText: 'Delete',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await activitiesApi.remove(entry.id)
          await reload()
        } catch (err) {
          setError(err)
        }
      },
    })
  }

  const today = todayIso()
  const summary = aerobicSummary(entries, today)

  return (
    <>
      <header className="page-header">
        <div>
          <div className="page-kicker">
            {summary.thisWeek} min this week · {summary.thisMonth} min in {formatMonthName(today)}
          </div>
          <h1>Aerobic</h1>
        </div>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreating(true)}>
          Add
        </Button>
      </header>
      <ErrorMessage error={error} />
      <div className="stack">
        <AerobicWeeksCard entries={entries} today={today} />
        {entries.length === 0 ? (
          <div className="empty">No aerobic logged yet.</div>
        ) : (
          <>
            <div className="aerobic-head">
              <span>Date</span>
              <span>Activity</span>
              <span>Min</span>
              <span />
            </div>
            {entries.map((entry) => (
              <article key={entry.id} className="aerobic-row">
                <span>
                  <span className="workout-day">{formatShortDate(entry.date)}</span>
                  <br />
                  <span className="workout-weekday">{formatWeekday(entry.date)}</span>
                </span>
                <span style={{ minWidth: 0 }}>
                  <span className="workout-title">{ACTIVITY_LABELS[entry.kind]}</span>
                  <span className="workout-summary">{entry.note ?? ''}</span>
                </span>
                <span className="aerobic-minutes">{entry.minutes}</span>
                <Button
                  type="text"
                  danger
                  aria-label="Delete entry"
                  icon={<DeleteOutlined />}
                  onClick={() => remove(entry)}
                />
              </article>
            ))}
          </>
        )}
      </div>
      <AerobicForm open={creating} onSubmit={create} onClose={() => setCreating(false)} />
    </>
  )
}
