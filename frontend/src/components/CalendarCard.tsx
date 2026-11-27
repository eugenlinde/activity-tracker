import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Button } from 'antd'
import { CloseOutlined, LeftOutlined, RightOutlined } from '@ant-design/icons'
import { activitiesApi } from '../api/activities'
import type { ActivityResponse } from '../api/types'
import {
  AEROBIC_PATH,
  dayLabel,
  dayLine,
  dayTitle,
  groupByLocalDay,
  monthGrid,
  monthTitle,
  shiftMonth,
  workoutPath,
} from '../lib/calendar'
import { todayIso } from '../lib/dates'
import { palette } from '../theme'
import { ErrorMessage } from './ErrorMessage'

const FUTURE_TEXT = '#5E6773'
const WEEKDAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']

function pad(n: number): string {
  return String(n).padStart(2, '0')
}

export function CalendarCard() {
  const today = todayIso()
  const [activities, setActivities] = useState<ActivityResponse[]>([])
  const [error, setError] = useState<unknown>(null)
  const [year, setYear] = useState(() => Number(today.slice(0, 4)))
  const [month, setMonth] = useState(() => Number(today.slice(5, 7)))
  const [selected, setSelected] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    activitiesApi
      .list()
      .then((list) => {
        if (cancelled) return
        setActivities(list)
        setError(null)
      })
      .catch((err) => {
        if (!cancelled) setError(err)
      })
    return () => {
      cancelled = true
    }
  }, [])

  const days = groupByLocalDay(activities)
  const { leadingBlanks, daysInMonth } = monthGrid(year, month)
  const trailingBlanks = (7 - ((leadingBlanks + daysInMonth) % 7)) % 7

  function goTo(nextYear: number, nextMonth: number) {
    setYear(nextYear)
    setMonth(nextMonth)
    setSelected(null)
  }

  function shift(delta: number) {
    const next = shiftMonth(year, month, delta)
    goTo(next.year, next.month)
  }

  function toggle(iso: string) {
    setSelected((current) => (current === iso ? null : iso))
  }

  const selectedSummary = selected === null ? undefined : days[selected]

  return (
    <section className="panel calendar-card">
      <div className="calendar-head">
        <h2 className="calendar-title">{monthTitle(year, month)}</h2>
        <div className="calendar-nav">
          <Button icon={<LeftOutlined />} aria-label="Previous month" onClick={() => shift(-1)} />
          <Button
            onClick={() => goTo(Number(today.slice(0, 4)), Number(today.slice(5, 7)))}
          >
            Today
          </Button>
          <Button icon={<RightOutlined />} aria-label="Next month" onClick={() => shift(1)} />
        </div>
      </div>

      <ErrorMessage error={error} />

      <div className="calendar-grid">
        {WEEKDAYS.map((name) => (
          <div key={name} className="calendar-weekday">
            {name}
          </div>
        ))}
        {Array.from({ length: leadingBlanks }, (_, i) => (
          <div key={`lead-${i}`} />
        ))}
        {Array.from({ length: daysInMonth }, (_, i) => {
          const day = i + 1
          const iso = `${year}-${pad(month)}-${pad(day)}`
          const isFuture = iso > today
          const isToday = iso === today
          const isSelected = iso === selected
          const summary = isFuture ? undefined : days[iso]
          let border = palette.raised
          let background = palette.input
          let numberColor = palette.text
          if (isToday) {
            background = palette.raised
            border = palette.sage
            numberColor = palette.sage
          }
          if (isFuture) numberColor = FUTURE_TEXT
          if (isSelected) border = palette.text
          return (
            <button
              key={iso}
              type="button"
              className="calendar-day"
              aria-label={dayLabel(iso, summary)}
              onClick={() => toggle(iso)}
              style={{ background, borderColor: border }}
            >
              <span className="calendar-day-number" style={{ color: numberColor }}>
                {day}
              </span>
              <span className="calendar-marks">
                {summary?.hasWorkout && (
                  <span className="calendar-mark" style={{ background: palette.sage }}>
                    W
                  </span>
                )}
                {summary?.hasAerobic && (
                  <span className="calendar-mark" style={{ background: palette.blue }}>
                    A
                  </span>
                )}
              </span>
            </button>
          )
        })}
        {Array.from({ length: trailingBlanks }, (_, i) => (
          <div key={`trail-${i}`} />
        ))}
      </div>

      {selected !== null && (
        <div className="calendar-panel" role="status">
          <div className="calendar-panel-head">
            <strong>{dayTitle(selected)}</strong>
            <Button
              type="text"
              size="small"
              icon={<CloseOutlined />}
              aria-label="Close"
              onClick={() => setSelected(null)}
            />
          </div>
          <div className="calendar-panel-line">{dayLine(selectedSummary)}</div>
          {selectedSummary && (selectedSummary.hasWorkout || selectedSummary.hasAerobic) && (
            <div className="calendar-panel-actions">
              {selectedSummary.hasWorkout && selectedSummary.firstWorkoutId !== null && (
                <Link to={workoutPath(selectedSummary.firstWorkoutId)}>
                  <Button>View workout</Button>
                </Link>
              )}
              {selectedSummary.hasAerobic && (
                <Link to={AEROBIC_PATH}>
                  <Button>View aerobic</Button>
                </Link>
              )}
            </div>
          )}
        </div>
      )}

      <div className="calendar-legend">
        <span className="legend">
          <span className="calendar-mark" style={{ background: palette.sage }}>
            W
          </span>
          Gym workout
        </span>
        <span className="legend">
          <span className="calendar-mark" style={{ background: palette.blue }}>
            A
          </span>
          Aerobic
        </span>
        <span>Tap a day for details</span>
      </div>
    </section>
  )
}
