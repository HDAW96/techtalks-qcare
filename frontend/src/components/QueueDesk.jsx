import Mark from './Mark'
import { STATUS_MARK, TICKET_STATUS } from '../constants'
import { formatRecordNumber } from '../data/format'

/* The desk board. Deliberately NOT QueueBoard: that one hides every
   name by design, because it is shown to patients. The desk must see
   who each ticket belongs to. Same CSS, different privacy rules. */

const DESK_LABEL = {
  [TICKET_STATUS.IN_CHAIR]: 'In the chair',
  [TICKET_STATUS.WAITING]: 'Waiting',
  [TICKET_STATUS.SKIPPED]: 'Skipped',
  [TICKET_STATUS.DONE]: 'Done',
}

function Actions({ entry, onStatus }) {
  const { ticketId, status } = entry
  if (status === TICKET_STATUS.DONE) return null

  return (
    <div className="desk__acts">
      {status === TICKET_STATUS.WAITING && (
        <button type="button" className="btn btn--live btn--sm"
          onClick={() => onStatus(ticketId, TICKET_STATUS.IN_CHAIR)}>Call in</button>
      )}
      {status === TICKET_STATUS.IN_CHAIR && (
        <button type="button" className="btn btn--live btn--sm"
          onClick={() => onStatus(ticketId, TICKET_STATUS.DONE)}>Done</button>
      )}
      {status !== TICKET_STATUS.SKIPPED && (
        <button type="button" className="btn btn--quiet btn--sm"
          onClick={() => onStatus(ticketId, TICKET_STATUS.SKIPPED)}>Skip</button>
      )}
      {status === TICKET_STATUS.SKIPPED && (
        <button type="button" className="btn btn--sm"
          onClick={() => onStatus(ticketId, TICKET_STATUS.WAITING)}>Bring back</button>
      )}
    </div>
  )
}

function QueueDesk({ entries, onStatus }) {
  if (entries.length === 0) {
    return <div className="empty"><p>No one in this queue yet.</p></div>
  }

  return (
    <table className="desk">
      <thead>
        <tr>
          <th>Ticket</th><th>Patient</th><th>Treatment</th>
          <th>Source</th><th>Wait</th><th>Status</th>
          <th className="desk__right">Actions</th>
        </tr>
      </thead>
      <tbody>
        {entries.map((e) => (
          <tr key={e.ticketId}
              className={e.status === TICKET_STATUS.IN_CHAIR ? 'desk--live' : undefined}>
            <td className="desk__tick">{e.label}</td>
            <td>
              <div className="desk__name">{e.patientName}</div>
              <p className="meta">{formatRecordNumber(e.patientId)}</p>
            </td>
            <td>{e.service}</td>
            <td><span className="desk__src">
              {e.source === 'BOOKED' ? 'Booked' : 'Walk-in'}
            </span></td>
            <td>{e.eta == null ? '—' : `~${e.eta} min`}</td>
            <td>
              <Mark status={STATUS_MARK[e.status]} label={DESK_LABEL[e.status] ?? e.status} />
            </td>
            <td className="desk__right">
              <Actions entry={e} onStatus={onStatus} />
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

export default QueueDesk