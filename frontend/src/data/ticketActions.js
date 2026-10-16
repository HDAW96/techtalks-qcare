import { ApiError, apiDelete } from '../api/client'
import { TICKET_STATUS } from '../constants'

/* After a confirmed departure the screen keeps a receipt (`departure`) so the
   card can say "Left queue". It still counts only while the same source is
   shown and the server either has no ticket any more (/tickets/me answers
   204) or shows this same ticket as SKIPPED. SKIPPED by itself is not proof
   the patient chose to leave. */
export function hasLeftQueue(departure, path, data) {
  if (!departure || departure.path !== path) return false
  if (!data) return true
  return data.ticketId === departure.ticket.ticketId && data.status === TICKET_STATUS.SKIPPED
}

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
