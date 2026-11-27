import { useEffect, useState } from 'react'
import { Button } from 'antd'
import { LeftOutlined, RightOutlined } from '@ant-design/icons'
import { dietApi } from '../api/diet'
import type { DietEntry } from '../api/types'
import { DietDayDialog } from '../components/DietDayDialog'
import { ErrorMessage } from '../components/ErrorMessage'
import { todayIso } from '../lib/dates'
import {
  buildMonthGrid,
  canGoNext,
  countMonth,
  dayAriaLabel,
  DIET_STATUS_ORDER,
  DIET_STATUSES,
  monthOf,
  monthRange,
  monthTitle,
  NOT_LOGGED_COLOR,
  shiftMonth,
  withEntry,
  withoutEntry,
} from '../lib/dietCalendar'

const WEEKDAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']

interface Loaded {
  month: string
  entries: DietEntry[]
}

interface LoadFailure {
  month: string
  error: unknown
}

export function DietPage() {
  const today = todayIso()
  const [month, setMonth] = useState(() => monthOf(today))
  const [loaded, setLoaded] = useState<Loaded | null>(null)
  const [failure, setFailure] = useState<LoadFailure | null>(null)
  const [selected, setSelected] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    const range = monthRange(month)
    dietApi
      .list(range.from, range.to)
      .then((list) => {
        if (cancelled) return
        setLoaded({ month, entries: list })
        setFailure(null)
      })
      .catch((err) => {
        if (!cancelled) setFailure({ month, error: err })
      })
    return () => {
      cancelled = true
    }
  }, [month])

  const entries = loaded && loaded.month === month ? loaded.entries : null
  const error = failure && failure.month === month ? failure.error : null
  const byDate = new Map((entries ?? []).map((entry) => [entry.date, entry]))
  const counts = entries ? countMonth(month, entries, today) : null
  const cells = buildMonthGrid(month, today)

  function saved(entry: DietEntry) {
    setLoaded((current) =>
      current && current.month === month ? { month, entries: withEntry(current.entries, entry) } : current,
    )
  }

  function removed(date: string) {
    setLoaded((current) =>
      current && current.month === month ? { month, entries: withoutEntry(current.entries, date) } : current,
    )
  }

  const counters = [
    ...DIET_STATUS_ORDER.map((status) => ({
      key: status,
      label: DIET_STATUSES[status].counterLabel,
      color: DIET_STATUSES[status].color,
      value: counts ? counts[countKey(status)] : null,
    })),
    { key: 'NOT_LOGGED', label: 'Not logged', color: NOT_LOGGED_COLOR, value: counts ? counts.notLogged : null },
  ]

  return (
    <>
      <header className="page-header">
        <h1>Diet</h1>
      </header>

      <div className="diet-counters">
        {counters.map((counter) => (
          <div key={counter.key} className="stat-block">
            <span className="diet-counter-value" style={{ color: counter.color }}>
              {counter.value === null ? '–' : counter.value}
            </span>
            <span className="stat-label">{counter.label}</span>
          </div>
        ))}
      </div>

      <ErrorMessage error={error} />

      <section className="panel">
        <div className="diet-month-head">
          <h2>{monthTitle(month)}</h2>
          <div className="diet-month-nav">
            <Button
              aria-label="Previous month"
              icon={<LeftOutlined />}
              onClick={() => setMonth((current) => shiftMonth(current, -1))}
            />
            <Button
              aria-label="Next month"
              icon={<RightOutlined />}
              disabled={!canGoNext(month, today)}
              onClick={() => setMonth((current) => shiftMonth(current, 1))}
            />
          </div>
        </div>
        <div className="diet-grid diet-weekdays">
          {WEEKDAYS.map((weekday) => (
            <div key={weekday}>{weekday}</div>
          ))}
        </div>
        <div className="diet-grid">
          {cells.map((cell, index) => {
            if (cell === null) return <div key={`blank-${index}`} />
            const entry = byDate.get(cell.date)
            return (
              <button
                key={cell.date}
                type="button"
                className={cell.future ? 'diet-day future' : 'diet-day'}
                disabled={cell.future || entries === null}
                aria-label={dayAriaLabel(cell.date, entry?.status)}
                onClick={() => setSelected(cell.date)}
              >
                <span className="diet-day-number">{cell.day}</span>
                {entry && (
                  <span className="diet-mark" style={{ background: DIET_STATUSES[entry.status].color }}>
                    {DIET_STATUSES[entry.status].letter}
                  </span>
                )}
                {entry?.comment && (
                  <svg
                    width="12"
                    height="12"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2.4"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    aria-hidden="true"
                  >
                    <path d="M4 5h16v11H9l-5 4z" />
                  </svg>
                )}
              </button>
            )
          })}
        </div>
        <div className="diet-legend">
          {DIET_STATUS_ORDER.map((status) => (
            <span key={status} className="diet-legend-item">
              <span className="diet-legend-swatch" style={{ background: DIET_STATUSES[status].color }}>
                {DIET_STATUSES[status].letter}
              </span>
              {DIET_STATUSES[status].legendLabel}
            </span>
          ))}
          <span>Tap a day to log it</span>
        </div>
      </section>

      <DietDayDialog
        date={selected}
        today={today}
        entry={selected ? byDate.get(selected) : undefined}
        onClose={() => setSelected(null)}
        onSaved={saved}
        onRemoved={removed}
      />
    </>
  )
}

function countKey(status: DietEntry['status']): 'onPlan' | 'more' | 'less' | 'outside' {
  if (status === 'ON_PLAN') return 'onPlan'
  if (status === 'MORE') return 'more'
  if (status === 'LESS') return 'less'
  return 'outside'
}
