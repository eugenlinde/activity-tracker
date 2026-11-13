import { StepsCard } from '../components/StepsCard'
import { WeightCard } from '../components/WeightCard'
import { formatLongToday } from '../lib/dates'

export function DashboardPage() {
  return (
    <>
      <header className="page-header">
        <div>
          <div className="page-kicker">{formatLongToday()}</div>
          <h1>Overview</h1>
        </div>
      </header>
      <div className="dashboard-grid">
        <WeightCard />
        <StepsCard />
      </div>
    </>
  )
}
