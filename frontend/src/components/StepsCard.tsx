import { useEffect, useState } from 'react'
import { Button, Segmented } from 'antd'
import { EditOutlined, PlusOutlined } from '@ant-design/icons'
import { stepsApi } from '../api/steps'
import type { StepsEntry } from '../api/types'
import { addDays, formatShortDate, todayIso } from '../lib/dates'
import { buildStepsChart, formatSteps, STEPS_CHART, STEPS_GOAL, weeklyAverages } from '../lib/stepsChart'
import { palette } from '../theme'
import { DayEntryDialog } from './DayEntryDialog'
import { ErrorMessage } from './ErrorMessage'
import { StepsGoalDialog } from './StepsGoalDialog'

const LOADED_DAYS = 56
const BELOW_GOAL = '#3A414B'

const RANGE_OPTIONS = [
  { label: '2 weeks', value: 14 },
  { label: '8 weeks', value: 56 },
]

export function StepsCard() {
  const [entries, setEntries] = useState<StepsEntry[] | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [days, setDays] = useState(14)
  const [dialogOpen, setDialogOpen] = useState(false)
  const [version, setVersion] = useState(0)
  const [goal, setGoal] = useState<number | null>(null)
  const [goalDialogOpen, setGoalDialogOpen] = useState(false)

  const to = todayIso()
  const from = addDays(to, -(LOADED_DAYS - 1))

  useEffect(() => {
    let cancelled = false
    stepsApi
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

  useEffect(() => {
    let cancelled = false
    stepsApi
      .goal()
      .then((saved) => {
        if (!cancelled) setGoal(saved.goal)
      })
      .catch((err) => {
        if (!cancelled) setError(err)
      })
    return () => {
      cancelled = true
    }
  }, [])

  const activeGoal = goal ?? STEPS_GOAL
  const list = entries ?? []
  const chart = buildStepsChart(list, to, days, activeGoal)
  const averages = weeklyAverages(list, to)

  return (
    <section className="panel">
      <div className="panel-head">
        <h2>Daily steps</h2>
        <Segmented size="small" options={RANGE_OPTIONS} value={days} onChange={setDays} aria-label="Range" />
      </div>

      <div className="stat-row">
        <div className="stat-block">
          <div className="stat-label">This week avg</div>
          <div className="stat">{formatSteps(averages.thisWeek)}</div>
        </div>
        <div className="stat-block">
          <div className="stat-label">Last week avg</div>
          <div className="stat muted">{formatSteps(averages.lastWeek)}</div>
        </div>
        <div className="stat-block">
          <div className="stat-label">Goal</div>
          <div className="stat muted">
            {formatSteps(activeGoal)}
            <Button
              type="text"
              size="small"
              icon={<EditOutlined />}
              aria-label="Edit steps goal"
              onClick={() => setGoalDialogOpen(true)}
            />
          </div>
        </div>
        <Button icon={<PlusOutlined />} onClick={() => setDialogOpen(true)} style={{ marginLeft: 'auto' }}>
          Add
        </Button>
      </div>

      <ErrorMessage error={error} />

      {entries !== null && chart.bars.length === 0 ? (
        <span className="muted">No steps logged in this period.</span>
      ) : (
        <>
          <svg
            viewBox={`0 0 ${STEPS_CHART.width} ${STEPS_CHART.height}`}
            style={{ width: '100%', height: 'auto', display: 'block' }}
            role="img"
            aria-label={`Daily steps for the last ${days} days with the goal line at ${formatSteps(activeGoal)}`}
          >
            <line
              x1={STEPS_CHART.left}
              y1={STEPS_CHART.baseline}
              x2={STEPS_CHART.right}
              y2={STEPS_CHART.baseline}
              stroke={palette.borderSoft}
              strokeWidth="1"
            />
            {chart.bars.map((bar) => (
              <rect
                key={bar.date}
                x={bar.x}
                y={bar.y}
                width={bar.width}
                height={bar.height}
                rx="2"
                fill={bar.reached ? palette.sage : BELOW_GOAL}
              >
                <title>
                  {formatShortDate(bar.date)}: {formatSteps(bar.steps)} steps
                </title>
              </rect>
            ))}
            <line
              x1={STEPS_CHART.left}
              y1={chart.goalY}
              x2={STEPS_CHART.right}
              y2={chart.goalY}
              stroke={palette.muted}
              strokeWidth="1"
              strokeDasharray="4 4"
            />
          </svg>
          <div className="chart-axis">
            <span>{formatShortDate(addDays(to, -(days - 1)))}</span>
            <span className="legend">
              <span className="legend-swatch" style={{ background: palette.sage }} />
              Goal reached
              <span className="legend-swatch" style={{ background: BELOW_GOAL }} />
              Below goal
            </span>
            <span>{formatShortDate(to)}</span>
          </div>
        </>
      )}

      <StepsGoalDialog
        open={goalDialogOpen}
        goal={activeGoal}
        onClose={() => setGoalDialogOpen(false)}
        onSaved={setGoal}
      />

      <DayEntryDialog
        open={dialogOpen}
        title="Add steps"
        valueLabel="Steps"
        min={0}
        max={100000}
        step={1}
        inputMode="numeric"
        entries={list.map((entry) => ({ date: entry.date, value: entry.steps }))}
        describe={(value) => `${formatSteps(value)} steps`}
        onSave={stepsApi.save}
        onRemove={stepsApi.remove}
        onClose={() => setDialogOpen(false)}
        onChanged={() => setVersion((current) => current + 1)}
      />
    </section>
  )
}
