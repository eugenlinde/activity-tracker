import { AEROBIC_CHART, aerobicSummary, buildAerobicChart, weeklyTotals, type AerobicEntry } from '../lib/aerobic'
import { formatShortDate } from '../lib/dates'
import { palette } from '../theme'

const OTHER_WEEKS = '#3A414B'

interface Props {
  entries: AerobicEntry[]
  today: string
}

export function AerobicWeeksCard({ entries, today }: Props) {
  const summary = aerobicSummary(entries, today)
  const bars = buildAerobicChart(weeklyTotals(entries, today))

  return (
    <section className="panel">
      <div className="panel-head">
        <h2>Weekly minutes</h2>
        <span>Last 8 weeks</span>
      </div>

      <div className="stat-row">
        <div className="stat-block">
          <div className="stat-label">This week</div>
          <div className="stat">
            {summary.thisWeek}
            <span className="stat-unit">min</span>
          </div>
        </div>
        <div className="stat-block">
          <div className="stat-label">Weekly average</div>
          <div className="stat muted">{summary.average}</div>
        </div>
        <div className="stat-block">
          <div className="stat-label">Best week</div>
          <div className="stat muted">{summary.best}</div>
        </div>
      </div>

      <svg
        viewBox={`0 0 ${AEROBIC_CHART.width} ${AEROBIC_CHART.height}`}
        style={{ width: '100%', height: 'auto', display: 'block' }}
        role="img"
        aria-label="Aerobic minutes per week for the last 8 weeks"
      >
        <line
          x1={AEROBIC_CHART.left}
          y1={AEROBIC_CHART.baseline}
          x2={AEROBIC_CHART.right}
          y2={AEROBIC_CHART.baseline}
          stroke={palette.borderSoft}
          strokeWidth="1"
        />
        {bars.map((bar) => (
          <g key={bar.monday}>
            <rect
              x={bar.x}
              y={bar.y}
              width={bar.width}
              height={bar.height}
              rx="2"
              fill={bar.current ? palette.blue : OTHER_WEEKS}
            />
            <text
              x={bar.centerX}
              y={bar.valueY}
              textAnchor="middle"
              fontSize="11"
              fill={bar.current ? palette.text : palette.muted}
            >
              {bar.minutes}
            </text>
            <text x={bar.centerX} y={AEROBIC_CHART.labelY} textAnchor="middle" fontSize="11" fill={palette.muted}>
              {formatShortDate(bar.monday)}
            </text>
          </g>
        ))}
      </svg>
    </section>
  )
}
