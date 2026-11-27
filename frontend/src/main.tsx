import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App as AntApp, ConfigProvider } from 'antd'
import '@fontsource-variable/manrope'
import App from './App'
import { AuthProvider } from './auth/AuthContext'
import { sendClientError } from './api/clientErrors'
import { tokenStore } from './api/client'
import { createClientErrorReporter, routePatternOf } from './lib/clientErrors'
import { ROUTE_PATTERNS } from './lib/routes'
import { appTheme } from './theme'
import './index.css'

const reportClientError = createClientErrorReporter(sendClientError, () => !!tokenStore.get())
const currentRoute = () => routePatternOf(window.location.pathname, ROUTE_PATTERNS)
window.addEventListener('error', (event) => reportClientError(event.error, currentRoute()))
window.addEventListener('unhandledrejection', (event) =>
  reportClientError(event.reason, currentRoute()),
)

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ConfigProvider theme={appTheme}>
      <AntApp>
        <BrowserRouter>
          <AuthProvider>
            <App />
          </AuthProvider>
        </BrowserRouter>
      </AntApp>
    </ConfigProvider>
  </StrictMode>,
)
