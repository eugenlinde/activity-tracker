import { matchPath } from 'react-router-dom'
import { FALLBACK_ROUTE } from './routes'

export interface ClientErrorReport {
  name: string
  frames: string[]
  route: string
}

const NAME_SHAPE = /^[A-Za-z]{1,60}$/
const FRAME_SHAPE = /^[A-Za-z0-9._/-]{1,180}:[0-9]{1,7}:[0-9]{1,7}$/
const LINE_SHAPE = /^(?:.*[\s(@])?([^\s()@]+):(\d+):(\d+)\)?$/
const URL_PREFIX = /^[A-Za-z][A-Za-z0-9+.-]*:\/\/[^/]*/
const MAX_FRAMES = 20
const MAX_FRAME_LENGTH = 200

export function routePatternOf(pathname: string, patterns: readonly string[]): string {
  for (const pattern of patterns) {
    if (pattern !== FALLBACK_ROUTE && matchPath({ path: pattern, end: true }, pathname)) {
      return pattern
    }
  }
  return FALLBACK_ROUTE
}

function pathOf(file: string): string {
  const withoutOrigin = file.replace(URL_PREFIX, '')
  const cut = withoutOrigin.search(/[?#]/)
  return cut === -1 ? withoutOrigin : withoutOrigin.slice(0, cut)
}

function framesOf(error: Error): string[] {
  if (typeof error.stack !== 'string') return []
  const text = error.message ? error.stack.split(error.message).join('') : error.stack
  const frames: string[] = []
  for (const rawLine of text.split('\n')) {
    const match = LINE_SHAPE.exec(rawLine.trim())
    if (!match) continue
    const frame = `${pathOf(match[1])}:${match[2]}:${match[3]}`
    if (frame.length > MAX_FRAME_LENGTH || !FRAME_SHAPE.test(frame)) continue
    frames.push(frame)
    if (frames.length === MAX_FRAMES) break
  }
  return frames
}

export function buildClientErrorReport(error: unknown, route: string): ClientErrorReport | null {
  if (!(error instanceof Error)) return null
  const frames = framesOf(error)
  if (frames.length === 0) return null
  const name = NAME_SHAPE.test(error.name) ? error.name : 'Error'
  return { name, frames, route }
}

export function createClientErrorReporter(
  send: (report: ClientErrorReport) => Promise<unknown>,
  isSignedIn: () => boolean,
  maxReports = 3,
): (error: unknown, route: string) => void {
  let attempts = 0
  return (error, route) => {
    try {
      if (!isSignedIn()) return
      if (attempts >= maxReports) return
      const report = buildClientErrorReport(error, route)
      if (!report) return
      attempts += 1
      Promise.resolve(send(report)).catch(() => {})
    } catch {
      return
    }
  }
}
