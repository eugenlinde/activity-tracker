import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

export function Layout() {
  const { logout } = useAuth()
  const navigate = useNavigate()

  function onLogout() {
    logout()
    navigate('/login')
  }

  return (
    <div className="app">
      <header className="topbar">
        <strong>Activity Tracker</strong>
        <nav>
          <NavLink to="/activities">My activities</NavLink>
          <NavLink to="/shares">Sharing</NavLink>
        </nav>
        <button type="button" onClick={onLogout}>
          Log out
        </button>
      </header>
      <main>
        <Outlet />
      </main>
    </div>
  )
}
