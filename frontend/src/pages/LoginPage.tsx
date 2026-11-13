import { useState, type FormEvent } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { Button, Input } from 'antd'
import { authApi } from '../api/auth'
import { useAuth } from '../auth/AuthContext'
import { ErrorMessage } from '../components/ErrorMessage'
import { Field } from '../components/Field'

type Mode = 'login' | 'signup'

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: { pathname: string } } | null)?.from?.pathname ?? '/activities'

  const [mode, setMode] = useState<Mode>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      if (mode === 'signup') {
        await authApi.signup({ email, password, firstName, lastName })
      }
      const { token } = await authApi.login({ email, password })
      login(token)
      navigate(from, { replace: true })
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="centered">
      <form className="panel form auth-card" onSubmit={onSubmit}>
        <div className="logo" style={{ padding: 0 }}>
          <span className="logo-dot" />
          tracker
        </div>
        <h1>{mode === 'login' ? 'Log in' : 'Sign up'}</h1>
        {mode === 'signup' && (
          <div className="form-grid">
            <Field label="First name">
              <Input value={firstName} onChange={(e) => setFirstName(e.target.value)} required minLength={2} />
            </Field>
            <Field label="Last name">
              <Input value={lastName} onChange={(e) => setLastName(e.target.value)} required minLength={2} />
            </Field>
          </div>
        )}
        <Field label="Email">
          <Input
            type="email"
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
        </Field>
        <Field label="Password">
          <Input.Password
            autoComplete={mode === 'signup' ? 'new-password' : 'current-password'}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            minLength={mode === 'signup' ? 8 : undefined}
          />
        </Field>
        <ErrorMessage error={error} />
        <Button type="primary" htmlType="submit" loading={busy} block>
          {mode === 'login' ? 'Log in' : 'Create account'}
        </Button>
        <Button type="link" onClick={() => setMode(mode === 'login' ? 'signup' : 'login')}>
          {mode === 'login' ? 'Need an account? Sign up' : 'Have an account? Log in'}
        </Button>
      </form>
    </div>
  )
}
