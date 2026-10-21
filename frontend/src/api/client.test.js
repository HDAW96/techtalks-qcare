import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  ApiError,
  apiGet,
  apiPost,
  setPasswordChangeRequiredHandler,
  setToken,
  setUnauthorizedHandler,
} from './client'

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

/* The server refuses an account still on a temporary password with 403
   PASSWORD_CHANGE_REQUIRED. That must send the user to change it, and must not
   be mistaken for an expired session. */
describe('password change required', () => {
  function answers(status, body) {
    return vi.fn().mockResolvedValue(
      new Response(JSON.stringify(body), {
        status,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
  }

  afterEach(() => {
    setPasswordChangeRequiredHandler(null)
    setUnauthorizedHandler(null)
    setToken(null)
  })

  it('calls the handler and keeps the session', async () => {
    const changeRequired = vi.fn()
    const unauthorized = vi.fn()
    setPasswordChangeRequiredHandler(changeRequired)
    setUnauthorizedHandler(unauthorized)
    setToken('a-token')
    vi.stubGlobal(
      'fetch',
      answers(403, {
        error: 'PASSWORD_CHANGE_REQUIRED',
        message: 'Please choose a new password before continuing.',
      }),
    )

    const error = await apiGet('/queues/today').catch((e) => e)

    expect(changeRequired).toHaveBeenCalledOnce()
    expect(unauthorized).not.toHaveBeenCalled()
    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(403)
    expect(error.code).toBe('PASSWORD_CHANGE_REQUIRED')
    expect(error.message).toBe('Please choose a new password before continuing.')
  })

  it('leaves any other 403 alone', async () => {
    const changeRequired = vi.fn()
    setPasswordChangeRequiredHandler(changeRequired)
    vi.stubGlobal('fetch', answers(403, { error: 'FORBIDDEN', message: 'No.' }))

    const error = await apiGet('/admin/users').catch((e) => e)

    expect(changeRequired).not.toHaveBeenCalled()
    expect(error.code).toBe('FORBIDDEN')
  })
})
