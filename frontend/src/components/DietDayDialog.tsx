import { useState } from 'react'
import { Button, Input, Modal } from 'antd'
import { dietApi } from '../api/diet'
import type { DietEntry, DietStatus } from '../api/types'
import { dayLabel, DIET_STATUS_ORDER, DIET_STATUSES, doneRequest } from '../lib/dietCalendar'
import { ErrorMessage } from './ErrorMessage'

interface Props {
  date: string | null
  today: string
  entry: DietEntry | undefined
  onClose: () => void
  onSaved: (saved: DietEntry) => void
  onRemoved: (date: string) => void
}

export function DietDayDialog({ date, today, ...form }: Props) {
  return (
    <Modal
      open={date !== null}
      title={
        date === null ? null : (
          <div>
            <div className="diet-dialog-kicker">How did you eat on</div>
            <div>{dayLabel(date, today)}</div>
          </div>
        )
      }
      onCancel={form.onClose}
      footer={null}
      destroyOnHidden
      width={440}
    >
      {date !== null && <DietDayForm date={date} {...form} />}
    </Modal>
  )
}

function DietDayForm({ date, entry, onClose, onSaved, onRemoved }: Omit<Props, 'today' | 'date'> & { date: string }) {
  const [text, setText] = useState(entry?.comment ?? '')
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)

  async function run(action: () => Promise<boolean>) {
    setError(null)
    setBusy(true)
    try {
      if (await action()) onClose()
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  function chooseStatus(status: DietStatus) {
    void run(async () => {
      onSaved(await dietApi.save(date, status, entry?.comment ?? null))
      return false
    })
  }

  function done() {
    const request = doneRequest(entry?.status ?? null, entry?.comment ?? null, text)
    if (request === null) {
      onClose()
      return
    }
    void run(async () => {
      onSaved(await dietApi.save(date, request.status, request.comment))
      return true
    })
  }

  function clearDay() {
    if (!entry) {
      onClose()
      return
    }
    void run(async () => {
      await dietApi.remove(date)
      onRemoved(date)
      return true
    })
  }

  return (
    <div className="form">
      <div className="diet-statuses" role="group" aria-label="How did you eat">
        {DIET_STATUS_ORDER.map((status) => (
          <Button
            key={status}
            block
            className={entry?.status === status ? 'diet-status selected' : 'diet-status'}
            aria-pressed={entry?.status === status}
            disabled={busy}
            onClick={() => chooseStatus(status)}
          >
            <span className="diet-swatch" style={{ background: DIET_STATUSES[status].color }} />
            {DIET_STATUSES[status].buttonLabel}
          </Button>
        ))}
      </div>
      <div className="diet-comment">
        <label htmlFor="diet-comment" className="stat-label">
          Comment
        </label>
        <Input.TextArea
          id="diet-comment"
          rows={3}
          maxLength={500}
          placeholder="What happened? e.g. birthday dinner, skipped lunch…"
          value={text}
          onChange={(e) => setText(e.target.value)}
        />
      </div>
      <ErrorMessage error={error} />
      <div className="diet-actions">
        <Button disabled={busy} onClick={clearDay}>
          Clear day
        </Button>
        <Button type="primary" disabled={busy} onClick={done}>
          Done
        </Button>
      </div>
    </div>
  )
}
