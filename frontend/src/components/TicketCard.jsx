import { useEffect, useId, useRef, useState } from 'react'
import Mark from './Mark'
import { STATUS_MARK, TICKET_STATUS } from '../constants'
import { waitLabel } from '../data/hooks'

/* One patient's own ticket: their number, where they stand, and what to do.
   The same card on the ticket page and the patient dashboard, so the wording
   never differs between them. Reads a /tickets/me or /tickets/by-code answer.
   `onLeave` adds "Give up my place" (with a confirm step) while they wait;
   `hasLeft` shows the receipt after they did. */

function aheadOfYou(count) {
  if (count === 0) return 'Nobody ahead of you'
  if (count === 1) return 'One person ahead of you'
  return `${count} people ahead of you`
}

function whatNow(ticket, hasLeft) {
  if (hasLeft) {
    return 'You have left the queue. You are no longer waiting for your turn.'
  }

  switch (ticket.status) {
    case TICKET_STATUS.IN_CHAIR:
      return 'It is your turn. Please go in.'
    case TICKET_STATUS.DONE:
      return 'Your visit is finished. Thank you!'
    case TICKET_STATUS.SKIPPED:
      return 'The desk skipped your turn. Please see the front desk.'
    default:
      return ticket.position === 0
        ? 'You are next. Please stay close to the desk.'
        : `${aheadOfYou(ticket.position)}, about ${waitLabel(ticket.etaMinutes).replace('~', '')} to wait.`
  }
}

const STATUS_LABEL = {
  [TICKET_STATUS.WAITING]: 'Waiting',
  [TICKET_STATUS.IN_CHAIR]: 'Your turn',
  [TICKET_STATUS.DONE]: 'Finished',
  [TICKET_STATUS.SKIPPED]: 'Skipped',
}

function TicketCard({ ticket, onLeave, hasLeft = false }) {
  const [confirming, setConfirming] = useState(false)
  const [saving, setSaving] = useState(false)
  const [leaveError, setLeaveError] = useState(null)
  const pending = useRef(false)
  const keepButton = useRef(null)
  const questionId = useId()

  const canLeave =
    Boolean(onLeave) &&
    ticket.status === TICKET_STATUS.WAITING &&
    !hasLeft

  useEffect(() => {
    if (confirming && canLeave) keepButton.current?.focus()
  }, [confirming, canLeave])

  const confirmLeave = async () => {
    if (!canLeave || pending.current) return

    pending.current = true
    setSaving(true)
    setLeaveError(null)

    try {
      await onLeave(ticket.ticketId)
      setConfirming(false)
    } catch (error) {
      setLeaveError(
        error.message || 'Could not leave the queue. Please try again.',
      )
    } finally {
      pending.current = false
      setSaving(false)
    }
  }

  return (
    <div className="ticket-card">
      <div className="now">
        <span className="meta">Your number</span>
        <span className="now__num">{ticket.ticket}</span>
        <Mark
          status={STATUS_MARK[ticket.status]}
          label={
            hasLeft
              ? 'Left queue'
              : STATUS_LABEL[ticket.status] ?? ticket.status
          }
        />
      </div>

      <div className="ticket-card__body">
        <p className="ticket-card__what" role="status">
          {whatNow(ticket, hasLeft)}
        </p>

        <p className="meta">
          {ticket.doctorName} · {ticket.service}
        </p>

        {!hasLeft && (
          <p className="meta">
            {ticket.nowServing
              ? `Now serving ${ticket.nowServing}`
              : 'Nobody in the chair yet'}
          </p>
        )}

        {canLeave && (
          <div className="ticket-card__leave">
            {!confirming ? (
              <div>
                <button
                  type="button"
                  className="btn btn--quiet"
                  onClick={() => {
                    setLeaveError(null)
                    setConfirming(true)
                  }}
                >
                  Give up my place
                </button>
              </div>
            ) : (
              <div
                role="group"
                aria-labelledby={questionId}
                aria-busy={saving}
              >
                <p id={questionId}>
                  Give up ticket {ticket.ticket}? You will lose your current
                  place in the queue.
                </p>

                <div className="ticket-card__confirm-actions">
                  <button
                    type="button"
                    className="btn"
                    disabled={saving}
                    onClick={confirmLeave}
                  >
                    {saving ? 'Leaving…' : 'Yes, give up my place'}
                  </button>

                  <button
                    ref={keepButton}
                    type="button"
                    className="btn btn--quiet"
                    disabled={saving}
                    onClick={() => {
                      setConfirming(false)
                      setLeaveError(null)
                    }}
                  >
                    Keep my place
                  </button>
                </div>
              </div>
            )}
          </div>
        )}

        {leaveError && (
          <p className="form-error" role="alert">
            {leaveError}
          </p>
        )}
      </div>
    </div>
  )
}

export default TicketCard
