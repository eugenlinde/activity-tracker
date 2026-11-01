import { useEffect, useState } from 'react'
import { mediaApi } from '../api/media'

interface Props {
  mediaId: number
}

export function MediaThumbnail({ mediaId }: Props) {
  const [url, setUrl] = useState<string | null>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    let cancelled = false
    let objectUrl: string | null = null

    mediaApi
      .download(mediaId)
      .then((blob) => {
        if (cancelled) return
        objectUrl = URL.createObjectURL(blob)
        setUrl(objectUrl)
      })
      .catch(() => {
        if (!cancelled) setFailed(true)
      })

    return () => {
      cancelled = true
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [mediaId])

  if (failed) return <span className="thumb muted">!</span>
  if (!url) return <span className="thumb muted">…</span>
  return (
    <a href={url} target="_blank" rel="noreferrer">
      <img className="thumb" src={url} alt="" />
    </a>
  )
}
