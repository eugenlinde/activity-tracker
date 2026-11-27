import { useState, type FormEvent } from 'react'
import { Button, Input, Modal } from 'antd'
import { stepsApi } from '../api/steps'
import { ErrorMessage } from './ErrorMessage'
import { Field } from './Field'

interface Props {
  open: boolean
  goal: number
  onClose: () => void
  onSaved: (goal: number) => void
}

export function StepsGoalDialog({ open, onClose, ...form }: Props) {
  return (
    <Modal open={open} title="Steps goal" onCancel={onClose} footer={null} destroyOnHidden width={420}>
      <StepsGoalForm onClose={onClose} {...form} />
    </Modal>
  )
}

function StepsGoalForm({ goal, onClose, onSaved }: Omit<Props, 'open'>) {
  const [value, setValue] = useState(String(goal))
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)

  async function save(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      const saved = await stepsApi.saveGoal(Number(value))
      onSaved(saved.goal)
      onClose()
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="form" onSubmit={(e) => void save(e)}>
      <Field label="Daily goal">
        <Input
          type="number"
          inputMode="numeric"
          min={1}
          max={100000}
          step={1}
          value={value}
          onChange={(e) => setValue(e.target.value)}
          required
          autoFocus
        />
      </Field>
      <ErrorMessage error={error} />
      <div className="form-actions">
        <Button onClick={onClose}>Cancel</Button>
        <Button type="primary" htmlType="submit" loading={busy}>
          Save
        </Button>
      </div>
    </form>
  )
}
