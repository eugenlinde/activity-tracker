import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { sharesApi } from '../api/shares'
import type { ShareResponse } from '../api/types'
import { ErrorMessage } from '../components/ErrorMessage'

export function SharesPage() {
  const [given, setGiven] = useState<ShareResponse[]>([])
  const [received, setReceived] = useState<ShareResponse[]>([])
  const [email, setEmail] = useState('')
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)

  const reload = useCallback(async () => {
    try {
      const [g, r] = await Promise.all([sharesApi.given(), sharesApi.received()])
      setGiven(g)
      setReceived(r)
      setError(null)
    } catch (err) {
      setError(err)
    }
  }, [])

  useEffect(() => {
    void reload()
  }, [reload])

  async function grant(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await sharesApi.grant({ email })
      setEmail('')
      await reload()
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  async function revoke(share: ShareResponse) {
    if (!window.confirm(`Stop sharing with ${share.email}?`)) return
    try {
      await sharesApi.revoke(share.id)
      await reload()
    } catch (err) {
      setError(err)
    }
  }

  return (
    <>
      <h1>Sharing</h1>
      <ErrorMessage error={error} />

      <section>
        <h2>Logs shared with me</h2>
        {received.length === 0 ? (
          <p className="muted">Nobody has shared a log with you.</p>
        ) : (
          <ul>
            {received.map((s) => (
              <li key={s.id}>
                <Link to={`/shared/${s.ownerId}`}>{s.email}</Link>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section>
        <h2>People who can see my log</h2>
        <form className="row" onSubmit={grant}>
          <input
            type="email"
            placeholder="viewer@example.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
          <button type="submit" disabled={busy}>
            Share
          </button>
        </form>
        {given.length === 0 ? (
          <p className="muted">You have not shared your log with anyone.</p>
        ) : (
          <ul>
            {given.map((s) => (
              <li key={s.id} className="row">
                <span>{s.email}</span>
                <button type="button" onClick={() => revoke(s)}>
                  Revoke
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  )
}
