import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, apiPost } from './client'

afterEach(() => {
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

/* A server that accepts the connection and never answers. Like the real
   fetch, it only gives up when the request's signal aborts. */
function neverAnswers(_url, { signal }) {
  return new Promise((_resolve, reject) => {
    signal.addEventListener('abort', () => reject(signal.reason))
  })
}

describe('request timeout (SCRUM-102)', () => {
  it('fails with TIMEOUT instead of waiting forever', async () => {
    // AbortSignal.timeout runs on an internal timer fake timers cannot reach,
    // so hand the client a signal this test fires itself.
    const controller = new AbortController()
    const timeout = vi.spyOn(AbortSignal, 'timeout').mockReturnValue(controller.signal)
    vi.stubGlobal('fetch', vi.fn(neverAnswers))

    const result = apiPost('/appointments/1/complete', { clinicalNotes: 'x' }).catch((e) => e)
    controller.abort(new DOMException('The operation timed out.', 'TimeoutError'))
    const error = await result

    expect(timeout).toHaveBeenCalledWith(15000)
    expect(error).toBeInstanceOf(ApiError)
    expect(error.code).toBe('TIMEOUT')
  })

  it('still reports an unreachable server as NETWORK_ERROR', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))

    const error = await apiPost('/x', { a: 1 }).catch((e) => e)

    expect(error.code).toBe('NETWORK_ERROR')
  })
})
