import Mark from './Mark'
import { STATUS_MARK, TICKET_STATUS } from '../constants'
import { waitLabel } from '../data/hooks'

/* One patient's own ticket: their number, where they stand, and what to do.
   The same card on the ticket page and the patient dashboard, so the wording
   never differs between them. Reads a /tickets/me or /tickets/by-code answer. */

function aheadOfYou(count) {
  if (count === 0) return 'Nobody ahead of you'
  if (count === 1) return 'One person ahead of you'
  return `${count} people ahead of you`
}

function whatNow(ticket) {
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

function TicketCard({ ticket }) {
  return (
    <div className="ticket-card">
      <div className="now">
        <span className="meta">Your number</span>
        <span className="now__num">{ticket.ticket}</span>
        <Mark status={STATUS_MARK[ticket.status]} label={STATUS_LABEL[ticket.status] ?? ticket.status} />
      </div>
      <div className="ticket-card__body">
        <p className="ticket-card__what" role="status">{whatNow(ticket)}</p>
        <p className="meta">
          {ticket.doctorName} · {ticket.service}
        </p>
        <p className="meta">
          {ticket.nowServing ? `Now serving ${ticket.nowServing}` : 'Nobody in the chair yet'}
        </p>
      </div>
    </div>
  )
}

export default TicketCard
