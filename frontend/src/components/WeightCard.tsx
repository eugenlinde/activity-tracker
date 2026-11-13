import { useEffect, useState } from 'react'
import { Button } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { weightsApi } from '../api/weights'
import type { WeightEntry } from '../api/types'
import { addDays, formatShortDate, todayIso } from '../lib/dates'
import { buildWeightChart, CHART, formatDelta, formatKg } from '../lib/weightChart'
import { palette } from '../theme'
import { ErrorMessage } from './ErrorMessage'
import { DayEntryDialog } from './DayEntryDialog'

const RANGE_DAYS = 84

export function WeightCard() {
  const [entries, setEntries] = useState<WeightEntry[] | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [dialogOpen, setDialogOpen] = useState(false)
  const [version, setVersion] = useState(0)

  const to = todayIso()
  const from = addDays(to, -RANGE_DAYS)

  useEffect(() => {
    let cancelled = false
    weightsApi
      .list(from, to)
      .then((list) => {
        if (cancelled) return
        setEntries(list)
        setError(null)
      })
      .catch((err) => {
        if (!cancelled) setError(err)
      })
    return () => {
      cancelled = true
    }
  }, [from, to, version])

  const list = entries ?? []
  const chart = buildWeightChart(list, from, to)
  const first = list[0]
  const latest = list[list.length - 1]

  return (
    <section className="panel">
      <div className="panel-head">
        <h2>Weight</h2>
        <span>Last 12 weeks</span>
      </div>

      <div className="stat-row">
        {latest ? (
          <>
            <span className="stat">
              {formatKg(latest.weightKg)}
              <span className="stat-unit">kg</span>
            </span>
            {list.length > 1 && (
              <span className="muted">
                {formatDelta(latest.weightKg - first.weightKg)} kg since {formatShortDate(first.date)}
              </span>
            )}
          </>
        ) : (
          <span className="muted">{entries === null ? 'Loading…' : 'No weight logged yet.'}</span>
        )}
        <Button icon={<PlusOutlined />} onClick={() => setDialogOpen(true)} style={{ marginLeft: 'auto' }}>
          Add
        </Button>
      </div>

      <ErrorMessage error={error} />

      {list.length > 0 && (
        <>
          <svg
            viewBox={`0 0 ${CHART.width} ${CHART.height}`}
            style={{ width: '100%', height: 'auto', display: 'block' }}
            role="img"
            aria-label={`Weight over the last 12 weeks, latest ${formatKg(latest.weightKg)} kg`}
          >
            {[CHART.top, (CHART.top + CHART.bottom) / 2, CHART.bottom].map((y) => (
              <line key={y} x1={CHART.left} y1={y} x2={CHART.right} y2={y} stroke={palette.borderSoft} strokeWidth="1" />
            ))}
            {chart.points.length > 1 && (
              <>
                <path d={chart.area} fill={palette.sage} fillOpacity="0.08" />
                <polyline
                  points={chart.line}
                  fill="none"
                  stroke={palette.sage}
                  strokeWidth="2.5"
                  strokeLinejoin="round"
                  strokeLinecap="round"
                />
              </>
            )}
            {chart.points.map((point) => (
              <circle
                key={point.entry.date}
                cx={point.x}
                cy={point.y}
                r="3.5"
                fill={palette.ground}
                stroke={palette.sage}
                strokeWidth="2"
              >
                <title>
                  {formatShortDate(point.entry.date)}: {formatKg(point.entry.weightKg)} kg
                </title>
              </circle>
            ))}
          </svg>
          <div className="chart-axis">
            <span>{formatShortDate(from)}</span>
            <span>{formatShortDate(to)}</span>
          </div>
        </>
      )}

      <DayEntryDialog
        open={dialogOpen}
        title="Add weight"
        valueLabel="Weight (kg)"
        min={20}
        max={400}
        step={0.1}
        inputMode="decimal"
        entries={list.map((entry) => ({ date: entry.date, value: entry.weightKg }))}
        describe={(value) => `${formatKg(value)} kg`}
        onSave={weightsApi.save}
        onRemove={weightsApi.remove}
        onClose={() => setDialogOpen(false)}
        onChanged={() => setVersion((current) => current + 1)}
      />
    </section>
  )
}
