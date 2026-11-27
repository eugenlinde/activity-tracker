import { describe, expect, it } from 'vitest'
import {
  buildClientErrorReport,
  createClientErrorReporter,
  routePatternOf,
  type ClientErrorReport,
} from './clientErrors'
import { ROUTE_PATTERNS } from './routes'

function errorWith(stack: string | undefined, message = 'boom', name = 'TypeError'): Error {
  const error = new Error(message)
  error.name = name
  Object.defineProperty(error, 'stack', { value: stack, configurable: true })
  return error
}

const CHROME_STACK = [
  'TypeError: boom',
  '    at fn (https://host.example:8443/assets/index-AbC123.js:10:1234)',
  '    at https://host.example/assets/vendor-XyZ.js?v=1#top:5:6',
].join('\n')

const FIREFOX_STACK = [
  'fn@https://host.example/assets/index-AbC123.js:10:1234',
  '@https://host.example/assets/vendor-XyZ.js?v=1#top:5:6',
].join('\n')

const EXPECTED_FRAMES = ['/assets/index-AbC123.js:10:1234', '/assets/vendor-XyZ.js:5:6']

describe('buildClientErrorReport', () => {
  it('reads a Chrome-style stack', () => {
    expect(buildClientErrorReport(errorWith(CHROME_STACK), '/shares')).toEqual({
      name: 'TypeError',
      frames: EXPECTED_FRAMES,
      route: '/shares',
    })
  })

  it('reads a Firefox-style stack', () => {
    expect(buildClientErrorReport(errorWith(FIREFOX_STACK), '/')).toEqual({
      name: 'TypeError',
      frames: EXPECTED_FRAMES,
      route: '/',
    })
  })

  it('has exactly the keys name, frames and route', () => {
    const report = buildClientErrorReport(errorWith(CHROME_STACK), '/')
    expect(Object.keys(report!).sort()).toEqual(['frames', 'name', 'route'])
  })

  it('never carries the message', () => {
    const message = 'zebrafinch /secret/file.js:1:2'
    const stack = `TypeError: ${message}\n    at fn (https://h/assets/a.js:10:5)\n    at ${message}`
    const report = buildClientErrorReport(errorWith(stack, message), '/')
    expect(report).not.toBeNull()
    const text = JSON.stringify(report)
    expect(text).not.toContain('zebrafinch')
    expect(text).not.toContain('secret')
  })

  it('keeps no host, query string or fragment', () => {
    const text = JSON.stringify(buildClientErrorReport(errorWith(CHROME_STACK), '/'))
    expect(text).not.toContain('host.example')
    expect(text).not.toContain('8443')
    expect(text).not.toContain('v=1')
    expect(text).not.toContain('top')
    expect(text).not.toContain('https')
  })

  it('keeps at most 20 frames', () => {
    const lines = Array.from({ length: 30 }, (_, i) => `    at https://h/assets/a.js:${i + 1}:1`)
    const report = buildClientErrorReport(errorWith(['TypeError: boom', ...lines].join('\n')), '/')
    expect(report!.frames).toHaveLength(20)
    expect(report!.frames[0]).toBe('/assets/a.js:1:1')
  })

  it('skips a frame that is too long or has a forbidden character', () => {
    const long = `https://h/${'a'.repeat(190)}.js:1:1`
    const stack = [
      'TypeError: boom',
      `    at ${long}`,
      '    at https://h/assets/a%20b.js:2:2',
      '    at https://h/assets/ok.js:3:3',
    ].join('\n')
    expect(buildClientErrorReport(errorWith(stack), '/')!.frames).toEqual(['/assets/ok.js:3:3'])
  })

  it('turns a name that is not plain letters into Error', () => {
    expect(buildClientErrorReport(errorWith(CHROME_STACK, 'boom', 'My Error2'), '/')!.name).toBe('Error')
    expect(buildClientErrorReport(errorWith(CHROME_STACK, 'boom', 'A'.repeat(61)), '/')!.name).toBe('Error')
  })

  it('gives null for a value that is not an Error', () => {
    expect(buildClientErrorReport('text', '/')).toBeNull()
    expect(buildClientErrorReport({ stack: CHROME_STACK }, '/')).toBeNull()
    expect(buildClientErrorReport(undefined, '/')).toBeNull()
  })

  it('gives null for an Error without stack or without readable frames', () => {
    expect(buildClientErrorReport(errorWith(undefined), '/')).toBeNull()
    expect(buildClientErrorReport(errorWith('TypeError: boom\n    at nowhere'), '/')).toBeNull()
  })
})

describe('routePatternOf', () => {
  it('returns the declared pattern, never the real path', () => {
    expect(routePatternOf('/shared/42', ROUTE_PATTERNS)).toBe('/shared/:ownerId')
    expect(routePatternOf('/shared/42', ROUTE_PATTERNS)).not.toContain('42')
    expect(routePatternOf('/', ROUTE_PATTERNS)).toBe('/')
    expect(routePatternOf('/activities', ROUTE_PATTERNS)).toBe('/activities')
    expect(routePatternOf('/login', ROUTE_PATTERNS)).toBe('/login')
  })

  it('returns * for anything else', () => {
    expect(routePatternOf('/nope/7', ROUTE_PATTERNS)).toBe('*')
    expect(routePatternOf('/nope/7', ROUTE_PATTERNS)).not.toContain('7')
  })
})

describe('createClientErrorReporter', () => {
  const error = () => errorWith(CHROME_STACK)

  it('sends three reports and drops the fourth', () => {
    const sent: ClientErrorReport[] = []
    const report = createClientErrorReporter(
      (item) => {
        sent.push(item)
        return Promise.resolve()
      },
      () => true,
    )
    for (let i = 0; i < 4; i++) report(error(), '/')
    expect(sent).toHaveLength(3)
  })

  it('swallows a rejecting send and still counts it', async () => {
    let calls = 0
    const report = createClientErrorReporter(
      () => {
        calls += 1
        return Promise.reject(new Error('network'))
      },
      () => true,
    )
    for (let i = 0; i < 4; i++) expect(() => report(error(), '/')).not.toThrow()
    await Promise.resolve()
    expect(calls).toBe(3)
  })

  it('swallows a throwing send and still counts it', () => {
    let calls = 0
    const report = createClientErrorReporter(
      () => {
        calls += 1
        throw new Error('broken')
      },
      () => true,
    )
    for (let i = 0; i < 4; i++) expect(() => report(error(), '/')).not.toThrow()
    expect(calls).toBe(3)
  })

  it('sends nothing and counts nothing while not signed in', () => {
    let calls = 0
    let signedIn = false
    const report = createClientErrorReporter(
      () => {
        calls += 1
        return Promise.resolve()
      },
      () => signedIn,
    )
    for (let i = 0; i < 5; i++) report(error(), '/')
    expect(calls).toBe(0)
    signedIn = true
    for (let i = 0; i < 4; i++) report(error(), '/')
    expect(calls).toBe(3)
  })

  it('sends nothing for a value that gives no report', () => {
    let calls = 0
    const report = createClientErrorReporter(
      () => {
        calls += 1
        return Promise.resolve()
      },
      () => true,
    )
    report('text', '/')
    report(errorWith(undefined), '/')
    expect(calls).toBe(0)
  })
})
