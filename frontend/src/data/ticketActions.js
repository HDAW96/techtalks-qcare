import { ApiError, apiDelete } from '../api/client'

export async function leaveTicket(source, ticketId) {
  if (source?.me) {
    if (!Number.isInteger(ticketId) || ticketId <= 0) {
      throw new ApiError(
        'Your ticket could not be identified. Please refresh.',
        0,
        'INVALID_TICKET',
      )
    }
    return apiDelete(`/tickets/${ticketId}`)
  }

  if (typeof source?.code === 'string' && source.code.trim()) {
    return apiDelete(`/tickets/by-code/${encodeURIComponent(source.code)}`)
  }

  throw new ApiError(
    'Your ticket could not be identified. Please refresh.',
    0,
    'MISSING_TICKET_SOURCE',
  )
}
