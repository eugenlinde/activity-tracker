import { useState } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { Button, Menu } from 'antd'
import {
  AppstoreOutlined,
  CloseOutlined,
  LogoutOutlined,
  MenuOutlined,
  TeamOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons'
import { useAuth } from '../auth/AuthContext'

const NAV_ITEMS = [
  { key: '/', icon: <AppstoreOutlined />, label: 'Dashboard' },
  { key: '/activities', icon: <ThunderboltOutlined />, label: 'Workouts' },
  { key: '/shares', icon: <TeamOutlined />, label: 'Shares' },
]

function selectedKey(pathname: string): string {
  if (pathname.startsWith('/shares') || pathname.startsWith('/shared')) return '/shares'
  if (pathname.startsWith('/activities')) return '/activities'
  return '/'
}

export function Layout() {
  const { logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [menuOpen, setMenuOpen] = useState(false)

  function onLogout() {
    logout()
    navigate('/login')
  }

  function go(path: string) {
    setMenuOpen(false)
    navigate(path)
  }

  return (
    <div className="shell">
      <nav className="sidebar" aria-label="Main">
        <div className="navtop">
          <div className="logo">
            <span className="logo-dot" />
            tracker
          </div>
          <Button
            className="burger"
            aria-label="Menu"
            aria-expanded={menuOpen}
            icon={menuOpen ? <CloseOutlined /> : <MenuOutlined />}
            onClick={() => setMenuOpen((open) => !open)}
          />
        </div>
        <div className={menuOpen ? 'navlinks open' : 'navlinks'}>
          <Menu
            mode="inline"
            items={NAV_ITEMS}
            selectedKeys={[selectedKey(location.pathname)]}
            onClick={({ key }) => go(key)}
          />
          <div className="navuser">
            <span>Signed in</span>
            <Button type="text" size="small" icon={<LogoutOutlined />} onClick={onLogout}>
              Sign out
            </Button>
          </div>
        </div>
      </nav>
      <main className="content">
        <Outlet />
      </main>
    </div>
  )
}
