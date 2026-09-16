import { useEffect, useState } from 'react'
import Mark from './Mark'
import { STATUS_MARK, TICKET_STATUS } from '../constants'
import { formatRecordNumber, formatTime } from '../data/format'

/* The desk board. Deliberately NOT QueueBoard: that one hides every
   name by design, because it is shown to patients. The desk must see
   who each ticket belongs to. Same CSS, different privacy rules. */

const DESK_LABEL = {
  [TICKET_STATUS.IN_CHAIR]: 'In the chair',
  [TICKET_STATUS.WAITING]: 'Waiting',
  [TICKET_STATUS.SKIPPED]: 'Skipped',
  [TICKET_STATUS.DONE]: 'Done',
}

/* Only the moves the backend allows (SCRUM-56 guide):
   WAITING → IN_CHAIR, IN_CHAIR → DONE, WAITING → SKIPPED, SKIPPED → WAITING.
   One doctor has one chair, so Call in waits until the chair is free. */
function Actions({ entry, chairTaken, onStatus }) {
  const { ticketId, status } = entry

  return (
    <div className="desk__acts">
      {status === TICKET_STATUS.WAITING && (
        <>
          <button type="button" className="btn btn--live btn--sm"
            disabled={chairTaken}
            title={chairTaken ? 'Someone is already in the chair' : undefined}
            onClick={() => onStatus(ticketId, TICKET_STATUS.IN_CHAIR)}>Call in</button>
          <button type="button" className="btn btn--quiet btn--sm"
            onClick={() => onStatus(ticketId, TICKET_STATUS.SKIPPED)}>Skip</button>
        </>
      )}
      {status === TICKET_STATUS.IN_CHAIR && (
        <button type="button" className="btn btn--live btn--sm"
          onClick={() => onStatus(ticketId, TICKET_STATUS.DONE)}>Done</button>
      )}
      {status === TICKET_STATUS.SKIPPED && (
        <button type="button" className="btn btn--sm"
          onClick={() => onStatus(ticketId, TICKET_STATUS.WAITING)}>Bring back</button>
      )}
    </div>
  )
}

/* On a narrow screen the table scrolls sideways, which nobody discovers by
   accident. So while the table is wider than its box, a line above it says
   so, and a fade on each edge shows which way there is more to see. On a
   desktop the whole table fits and none of this appears. */
function useSideScroll() {
  // A callback ref, so the effect re-runs when the table first appears.
  const [el, setEl] = useState(null)
  const [edges, setEdges] = useState({ wide: false, left: false, right: false })

  useEffect(() => {
    if (!el) return undefined
    const check = () => {
      const wide = el.scrollWidth > el.clientWidth + 4
      setEdges({
        wide,
        left: wide && el.scrollLeft > 4,
        right: wide && el.scrollLeft + el.clientWidth < el.scrollWidth - 4,
      })
    }
    // ResizeObserver also fires once right away, which covers the first render.
    const observer = new ResizeObserver(check)
    observer.observe(el)
    el.addEventListener('scroll', check, { passive: true })
    return () => {
      observer.disconnect()
      el.removeEventListener('scroll', check)
    }
  }, [el])

  return [setEl, edges]
}

function QueueDesk({ entries, chairTaken, onStatus }) {
  const [scrollRef, edges] = useSideScroll()

  if (entries.length === 0) {
    return <div className="empty"><p>No one in this queue yet.</p></div>
  }

  const frame = ['desk-frame', edges.left && 'desk-frame--left', edges.right && 'desk-frame--right']
    .filter(Boolean).join(' ')

  return (
    <>
      {edges.wide && (
        <p className="desk-hint">
          <span aria-hidden="true">←</span> Swipe the table to see status and actions <span aria-hidden="true">→</span>
        </p>
      )}
      <div className={frame}>
        <div className="desk-scroll" ref={scrollRef}>
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
                  <td className="desk__tick">{e.ticket}</td>
                  <td>
                    <div className="desk__name">{e.patientName}</div>
                    <p className="meta">
                      {formatRecordNumber(e.patientId)}{e.patientId != null ? ' · ' : ''}{e.phoneNumber}
                    </p>
                  </td>
                  <td>{e.service}</td>
                  <td><span className="desk__src">
                    {e.booked
                      ? `Booked${e.appointmentTime ? ` ${formatTime(e.appointmentTime)}` : ''}`
                      : 'Walk-in'}
                  </span></td>
                  <td className="desk__wait">{e.etaMinutes == null ? '—' : `~${e.etaMinutes} min`}</td>
                  <td>
                    <Mark status={STATUS_MARK[e.status]} label={DESK_LABEL[e.status] ?? e.status} />
                  </td>
                  <td className="desk__right">
                    <Actions entry={e} chairTaken={chairTaken} onStatus={onStatus} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </>
  )
}

export default QueueDesk
