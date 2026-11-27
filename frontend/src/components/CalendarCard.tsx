import { useEffect, useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { Button } from 'antd'
import { CloseOutlined, LeftOutlined, RightOutlined } from '@ant-design/icons'
import { activitiesApi } from '../api/activities'
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
  type DaySummary,
} from '../lib/calendar'
import { todayIso } from '../lib/dates'
import { palette } from '../theme'
import { ErrorMessage } from './ErrorMessage'

const FUTURE_TEXT = '#5E6773'

function pad(n: number): string {
  return String(n).padStart(2, '0')
}

export function CalendarCard() {
  const today = todayIso()
  const [year, setYear] = useState(() => Number(today.slice(0, 4)))
  const [month, setMonth] = useState(() => Number(today.slice(5, 7)))
  const [selected, setSelected] = useState<string | null>(null)
  const [days, setDays] = useState<Record<string, DaySummary>>({})
  const [error, setError] = useState<unknown>(null)

  useEffect(() => {
    let cancelled = false
    activitiesApi
      .list()
      .then((list) => {
        if (cancelled) return
        setDays(groupByLocalDay(list))
        setError(null)
      })
      .catch((err) => {
        if (!cancelled) setError(err)
      })
    return () => {
      cancelled = true
    }
  }, [])

  function goTo(target: { year: number; month: number }) {
    setYear(target.year)
    setMonth(target.month)
    setSelected(null)
  }

  function toggle(isoDate: string) {
    setSelected((current) => (current === isoDate ? null : isoDate))
  }

  const { leadingBlanks, daysInMonth } = monthGrid(year, month)
  const trailingBlanks = (7 - ((leadingBlanks + daysInMonth) % 7)) % 7
  const selectedSummary = selected === null ? undefined : days[selected]

  const cells: ReactNode[] = []
  for (let i = 0; i < leadingBlanks; i++) {
    cells.push(<div key={`lead-${i}`} className="calendar-blank" />)
  }
  for (let day = 1; day <= daysInMonth; day++) {
    const iso = `${year}-${pad(month)}-${pad(day)}`
    const isFuture = iso > today
    const isToday = iso === today
    const summary = isFuture ? undefined : days[iso]
    const isSelected = iso === selected
    let background: string = palette.input
    let border: string = palette.raised
    let numberColor: string = palette.text
    if (isToday) {
      background = palette.raised
      border = palette.sage
      numberColor = palette.sage
    }
    if (isFuture) numberColor = FUTURE_TEXT
    if (isSelected) border = palette.text
    cells.push(
      <button
        key={iso}
        type="button"
        className="calendar-day"
        aria-label={dayLabel(iso, summary)}
        style={{ background, borderColor: border }}
        onClick={() => toggle(iso)}
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
      </button>,
    )
  }
  for (let i = 0; i < trailingBlanks; i++) {
    cells.push(<div key={`trail-${i}`} className="calendar-blank" />)
  }

  return (
    <section className="panel calendar-card">
      <div className="calendar-head">
        <h2>{monthTitle(year, month)}</h2>
        <div className="calendar-nav">
          <Button
            icon={<LeftOutlined />}
            aria-label="Previous month"
            onClick={() => goTo(shiftMonth(year, month, -1))}
          />
          <Button
            type="text"
            onClick={() => goTo({ year: Number(today.slice(0, 4)), month: Number(today.slice(5, 7)) })}
          >
            Today
          </Button>
          <Button icon={<RightOutlined />} aria-label="Next month" onClick={() => goTo(shiftMonth(year, month, 1))} />
        </div>
      </div>

      <ErrorMessage error={error} />

      <div className="calendar-grid">
        {['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'].map((name) => (
          <div key={name} className="calendar-weekday">
            {name}
          </div>
        ))}
        {cells}
      </div>

      {selected !== null && (
        <div className="calendar-detail" role="status">
          <div className="calendar-detail-text">
            <div className="calendar-detail-date">{dayTitle(selected)}</div>
            <div className="calendar-detail-line">{dayLine(selectedSummary)}</div>
          </div>
          <div className="calendar-detail-actions">
            {selectedSummary?.firstWorkoutId != null && (
              <Link to={workoutPath(selectedSummary.firstWorkoutId)}>
                <Button>View workout</Button>
              </Link>
            )}
            {selectedSummary?.hasAerobic && (
              <Link to={AEROBIC_PATH}>
                <Button>View aerobic</Button>
              </Link>
            )}
            <Button type="text" icon={<CloseOutlined />} aria-label="Close" onClick={() => setSelected(null)} />
          </div>
        </div>
      )}

      <div className="calendar-legend">
        <span className="calendar-legend-item">
          <span className="calendar-mark" style={{ background: palette.sage }}>
            W
          </span>
          Gym workout
        </span>
        <span className="calendar-legend-item">
          <span className="calendar-mark" style={{ background: palette.blue }}>
            A
          </span>
          Aerobic
        </span>
        <span className="calendar-legend-hint">Tap a day for details</span>
      </div>
    </section>
  )
}
