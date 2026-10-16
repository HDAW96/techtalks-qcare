import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { hasLeftQueue, leaveTicket } from './ticketActions'
import { boardEntries } from './hooks'

beforeEach(() => {
  vi.stubGlobal('localStorage', {
    getItem: vi.fn(() => null),
    removeItem: vi.fn(),
  })
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue(new Response(null, { status: 204 })),
  )
})

afterEach(() => {
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('give up my place (SCRUM-110)', () => {
  it('deletes the signed-in ticket with its bearer token', async () => {
    localStorage.getItem.mockReturnValue('patient-token')

    await expect(leaveTicket({ me: true }, 17)).resolves.toBeNull()

    expect(fetch).toHaveBeenCalledTimes(1)
    const [url, options] = fetch.mock.calls[0]

    expect(url).toMatch(/\/tickets\/17$/)
    expect(options.method).toBe('DELETE')
    expect(options.headers.Authorization).toBe('Bearer patient-token')
    expect(options.body).toBeUndefined()
  })

  it('deletes a guest ticket using the encoded access code', async () => {
    await leaveTicket({ code: 'a/b?c=d' }, 17)

    const [url, options] = fetch.mock.calls[0]
    expect(url).toMatch(/\/tickets\/by-code\/a%2Fb%3Fc%3Dd$/)
    expect(options.method).toBe('DELETE')
    expect(options.headers.Authorization).toBeUndefined()
  })

  it('prefers the signed-in route over an old guest code', async () => {
    await leaveTicket({ me: true, code: 'old-guest-code' }, 17)
    expect(fetch.mock.calls[0][0]).toMatch(/\/tickets\/17$/)
  })

  it.each([null, {}, { code: '' }, { code: ' ' }])(
    'rejects an unusable source: %j',
    async (source) => {
      await expect(leaveTicket(source, 17)).rejects.toMatchObject({
        code: 'MISSING_TICKET_SOURCE',
      })
      expect(fetch).not.toHaveBeenCalled()
    },
  )

  it.each([undefined, 0, -1, '17', 1.5])(
    'rejects an invalid signed-in ticket id: %j',
    async (id) => {
      await expect(leaveTicket({ me: true }, id)).rejects.toMatchObject({
        code: 'INVALID_TICKET',
      })
      expect(fetch).not.toHaveBeenCalled()
    },
  )

  it('preserves a backend conflict instead of reporting success', async () => {
    fetch.mockResolvedValue(
      new Response(
        JSON.stringify({
          error: 'CANNOT_LEAVE_QUEUE',
          message: 'Only a waiting patient can leave.',
        }),
        { status: 409, headers: { 'Content-Type': 'application/json' } },
      ),
    )

    await expect(leaveTicket({ me: true }, 17)).rejects.toMatchObject({
      status: 409,
      code: 'CANNOT_LEAVE_QUEUE',
      message: 'Only a waiting patient can leave.',
    })
  })

  it('preserves network failures instead of reporting success', async () => {
    fetch.mockRejectedValue(new TypeError('Failed to fetch'))

    await expect(leaveTicket({ code: 'guest-code' }, 17))
      .rejects.toMatchObject({ code: 'NETWORK_ERROR' })
  })
})

describe('the "Left queue" receipt (SCRUM-110)', () => {
  const departure = { path: '/tickets/me', ticket: { ticketId: 17 } }

  it('holds once the server has no ticket any more', () => {
    expect(hasLeftQueue(departure, '/tickets/me', null)).toBe(true)
  })

  it('holds while the same ticket shows as SKIPPED', () => {
    expect(hasLeftQueue(departure, '/tickets/me', { ticketId: 17, status: 'SKIPPED' })).toBe(true)
  })

  it('drops when a newer ticket replaces it', () => {
    expect(hasLeftQueue(departure, '/tickets/me', { ticketId: 18, status: 'WAITING' })).toBe(false)
  })

  it('is not proven by SKIPPED on a different ticket', () => {
    expect(hasLeftQueue(departure, '/tickets/me', { ticketId: 18, status: 'SKIPPED' })).toBe(false)
  })

  it('drops when another source is shown, or there is no receipt', () => {
    expect(hasLeftQueue(departure, '/tickets/by-code/x', null)).toBe(false)
    expect(hasLeftQueue(null, '/tickets/me', null)).toBe(false)
  })
})

describe('the line after leaving', () => {
  const entries = [
    { ticket: 'R-1', status: 'WAITING', etaMinutes: 0 },
    { ticket: 'R-2', status: 'WAITING', etaMinutes: 20 },
  ]

  it('marks your own ticket while you wait', () => {
    expect(boardEntries(entries, 'R-2').map((e) => [e.ticket, e.you])).toEqual([['R-1', false], ['R-2', true]])
  })

  it('no longer lists you once you have left', () => {
    expect(boardEntries(entries, 'R-2', true).map((e) => e.ticket)).toEqual(['R-1'])
  })
})
