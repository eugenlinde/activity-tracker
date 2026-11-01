import { useCallback, useEffect, useState, type ChangeEvent } from 'react'
import { mediaApi } from '../api/media'
import type { MediaResponse } from '../api/types'
import { ErrorMessage } from './ErrorMessage'
import { MediaThumbnail } from './MediaThumbnail'

interface Props {
  activityId: number
  editable: boolean
}

export function ActivityMedia({ activityId, editable }: Props) {
  const [media, setMedia] = useState<MediaResponse[]>([])
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)

  const reload = useCallback(async () => {
    try {
      setMedia(await mediaApi.list(activityId))
      setError(null)
    } catch (err) {
      setError(err)
    }
  }, [activityId])

  useEffect(() => {
    void reload()
  }, [reload])

  async function upload(e: ChangeEvent<HTMLInputElement>) {
    const input = e.currentTarget
    const file = input.files?.[0]
    if (!file) return
    setBusy(true)
    try {
      await mediaApi.upload(activityId, file)
      await reload()
    } catch (err) {
      setError(err)
    } finally {
      input.value = ''
      setBusy(false)
    }
  }

  async function remove(id: number) {
    if (!window.confirm('Delete this image?')) return
    try {
      await mediaApi.remove(id)
      await reload()
    } catch (err) {
      setError(err)
    }
  }

  return (
    <div className="media">
      <div className="row">
        {media.map((m) => (
          <div key={m.id} className="media-item">
            <MediaThumbnail mediaId={m.id} />
            {editable && (
              <button type="button" className="link" onClick={() => remove(m.id)}>
                Remove
              </button>
            )}
          </div>
        ))}
        {editable && (
          <label className="upload">
            {busy ? 'Uploading…' : '+ Image'}
            <input type="file" accept="image/jpeg" onChange={upload} disabled={busy} hidden />
          </label>
        )}
      </div>
      <ErrorMessage error={error} />
    </div>
  )
}
