import { Navigate, Route, Routes } from 'react-router-dom'
import { RequireAuth } from './auth/RequireAuth'
import { Layout } from './components/Layout'
import {
  ACTIVITIES_ROUTE,
  ACTIVITY_ROUTE,
  AEROBIC_ROUTE,
  DASHBOARD_ROUTE,
  FALLBACK_ROUTE,
  IMPORT_ROUTE,
  LOGIN_ROUTE,
  SHARED_ROUTE,
  SHARES_ROUTE,
} from './lib/routes'
import { ActivitiesPage } from './pages/ActivitiesPage'
import { AerobicPage } from './pages/AerobicPage'
import { DashboardPage } from './pages/DashboardPage'
import { ImportPage } from './pages/ImportPage'
import { LoginPage } from './pages/LoginPage'
import { SharedLogPage } from './pages/SharedLogPage'
import { SharesPage } from './pages/SharesPage'
import { WorkoutPage } from './pages/WorkoutPage'

export default function App() {
  return (
    <Routes>
      <Route path={LOGIN_ROUTE} element={<LoginPage />} />
      <Route element={<RequireAuth />}>
        <Route element={<Layout />}>
          <Route path={DASHBOARD_ROUTE} element={<DashboardPage />} />
          <Route path={ACTIVITIES_ROUTE} element={<ActivitiesPage />} />
          <Route path={AEROBIC_ROUTE} element={<AerobicPage />} />
          <Route path={SHARES_ROUTE}element={<SharesPage />} />
          <Route path={IMPORT_ROUTE} element={<ImportPage />} />
          <Route path={SHARED_ROUTE} element={<SharedLogPage />} />
          <Route path={ACTIVITY_ROUTE} element={<WorkoutPage />} />
          <Route path={FALLBACK_ROUTE} element={<Navigate to={DASHBOARD_ROUTE} replace />} />
        </Route>
      </Route>
    </Routes>
  )
}
