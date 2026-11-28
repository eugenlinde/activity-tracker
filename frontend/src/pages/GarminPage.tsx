import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Button, Input, Popconfirm } from 'antd'
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons'
import { garminApi } from '../api/garmin'
import type { GarminStatusResponse } from '../api/types'
import { ErrorMessage } from '../components/ErrorMessage'
import { Field } from '../components/Field'
import { formatDateTime } from '../lib/datetime'
import { garminStatusText } from '../lib/garmin'

export function GarminPage() {
  const [status, setStatus] = useState<GarminStatusResponse | null>(null)
  const [apiKey, setApiKey] = useState('')
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)

  const reload = useCallback(async () => {
    try {
      setStatus(await garminApi.status())
      setError(null)
    } catch (err) {
      setError(err)
    }
  }, [])

  useEffect(() => {
    void reload()
  }, [reload])

  async function add(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      const added = await garminApi.add({ apiKey })
      setApiKey('')
      setStatus(added)
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  async function remove() {
    setError(null)
    setBusy(true)
    try {
      await garminApi.remove()
      await reload()
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <header className="page-header">
        <div>
          <div className="page-kicker">Steps arrive from your watch by themselves.</div>
          <h1>Garmin</h1>
        </div>
      </header>
      <ErrorMessage error={error} />

      <section className="panel">
        <div className="panel-head">
          <h2>Personal API key</h2>
        </div>
        {status === null ? null : status.connected ? (
          <div className="stack">
            <p>
              Garmin key added on{' '}
              {status.connectedAt ? formatDateTime(status.connectedAt) : ''}.
            </p>
            <p>{garminStatusText(status, formatDateTime)}</p>
            <div>
              <Popconfirm
                title="Remove the Garmin key? Steps already fetched stay."
                okText="Remove"
                okButtonProps={{ danger: true }}
                onConfirm={remove}
              >
                <Button danger icon={<DeleteOutlined />} loading={busy}>
                  Remove key
                </Button>
              </Popconfirm>
            </div>
          </div>
        ) : (
          <form className="stack" onSubmit={add}>
            <p>
              Create a Personal API Key in Garmin Connect on the web (account settings, Personal API
              Key) and paste it here. Steps are fetched every hour, starting from today. Days you
              entered yourself are not changed.
            </p>
            <Field label="Personal API key">
              <Input.Password
                visibilityToggle={false}
                autoComplete="off"
                value={apiKey}
                onChange={(e) => setApiKey(e.target.value)}
                required
              />
            </Field>
            <div>
              <Button type="primary" htmlType="submit" icon={<PlusOutlined />} loading={busy}>
                Add key
              </Button>
            </div>
          </form>
        )}
      </section>
    </>
  )
}
