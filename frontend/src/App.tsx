import { Navigate, Route, Routes } from 'react-router-dom'
import { RequireAuth } from './auth/RequireAuth'
import { Layout } from './components/Layout'
import { ActivitiesPage } from './pages/ActivitiesPage'
import { LoginPage } from './pages/LoginPage'
import { SharedLogPage } from './pages/SharedLogPage'
import { SharesPage } from './pages/SharesPage'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<RequireAuth />}>
        <Route element={<Layout />}>
          <Route path="/activities" element={<ActivitiesPage />} />
          <Route path="/shares" element={<SharesPage />} />
          <Route path="/shared/:ownerId" element={<SharedLogPage />} />
          <Route path="*" element={<Navigate to="/activities" replace />} />
        </Route>
      </Route>
    </Routes>
  )
}
