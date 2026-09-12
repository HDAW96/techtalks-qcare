import Mark from './Mark'
import { STATUS_MARK, TICKET_STATUS } from '../constants'

/* The waiting list. Rank is encoded in the type itself: the front of the queue
   is set larger and heavier and it shrinks as it recedes, so the order is
   readable before any numeral is. `--rank-lift` runs 1 at the front to 0 at
   the back and drives size and weight together.
   Tickets only: other patients' names never appear on a patient-facing
   board. The one row that says who it is is your own. */
function QueueBoard({ entries }) {
  const last = Math.max(entries.length - 1, 1)

  return (
    <ol className="board">
      {entries.map((entry, i) => {
        const lift = 1 - i / last
        const inChair = entry.status === TICKET_STATUS.IN_CHAIR

        return (
          <li
            key={entry.ticket}
            className={[
              'qrow',
              inChair ? 'qrow--live' : '',
              entry.you ? 'qrow--you' : '',
            ]
              .filter(Boolean)
              .join(' ')}
            style={{ '--rank-lift': lift.toFixed(3) }}
          >
            <span className="qrow__num">{entry.ticket}</span>
            <span className="qrow__name">
              {entry.you ? 'You' : inChair ? 'In the chair' : ''}
              {entry.you && <span className="qrow__you-tag"> — your ticket</span>}
            </span>
            <span className="qrow__eta">
              <Mark status={STATUS_MARK[entry.status]} label={entry.eta} />
            </span>
          </li>
        )
      })}
    </ol>
  )
}

export default QueueBoard
