import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { App, Avatar, Button, Input } from 'antd'
import { PlusOutlined, RightOutlined } from '@ant-design/icons'
import { sharesApi } from '../api/shares'
import type { ShareResponse } from '../api/types'
import { ErrorMessage } from '../components/ErrorMessage'
import { Field } from '../components/Field'
import { palette } from '../theme'

function initialOf(email: string): string {
  return email.charAt(0).toUpperCase()
}

export function SharesPage() {
  const { modal } = App.useApp()
  const navigate = useNavigate()
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

  function revoke(share: ShareResponse) {
    modal.confirm({
      title: `Stop sharing with ${share.email}?`,
      okText: 'Revoke',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await sharesApi.revoke(share.id)
          await reload()
        } catch (err) {
          setError(err)
        }
      },
    })
  }

  return (
    <>
      <header className="page-header">
        <div>
          <div className="page-kicker">Sharing is whole-log and read-only.</div>
          <h1>Shares</h1>
        </div>
      </header>
      <ErrorMessage error={error} />

      <section className="panel">
        <div className="panel-head">
          <h2>Who can see your log</h2>
          <span>
            {given.length} {given.length === 1 ? 'person' : 'people'}
          </span>
        </div>
        <form className="row" style={{ alignItems: 'flex-end' }} onSubmit={grant}>
          <div style={{ flex: '1 1 260px' }}>
            <Field label="Share with (email)">
              <Input
                type="email"
                placeholder="viewer@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
              />
            </Field>
          </div>
          <Button type="primary" htmlType="submit" icon={<PlusOutlined />} loading={busy}>
            Share
          </Button>
        </form>
        {given.length === 0 ? (
          <div className="empty">Nobody can see your log right now.</div>
        ) : (
          <div className="stack">
            {given.map((s) => (
              <div key={s.id} className="person">
                <Avatar style={{ background: palette.sage, color: palette.ground }}>{initialOf(s.email)}</Avatar>
                <span className="person-name">{s.email}</span>
                <Button danger onClick={() => revoke(s)}>
                  Revoke
                </Button>
              </div>
            ))}
          </div>
        )}
      </section>

      <section className="panel">
        <div className="panel-head">
          <h2>Logs shared with you</h2>
          <span>
            {received.length} {received.length === 1 ? 'log' : 'logs'}
          </span>
        </div>
        {received.length === 0 ? (
          <div className="empty">Nobody has shared a log with you.</div>
        ) : (
          <div className="stack">
            {received.map((s) => (
              <div key={s.id} className="person">
                <Avatar style={{ background: palette.blue, color: palette.ground }}>{initialOf(s.email)}</Avatar>
                <span className="person-name">{s.email}</span>
                <Button
                  icon={<RightOutlined />}
                  iconPlacement="end"
                  onClick={() => navigate(`/shared/${s.ownerId}`)}
                >
                  View log
                </Button>
              </div>
            ))}
          </div>
        )}
      </section>
    </>
  )
}
