import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import Mark from './Mark'
import RescheduleAppointment from './RescheduleAppointment'
import { useSideScroll } from './useSideScroll'
import { useDayAppointments } from '../data/hooks'
import { formatDay, formatRecordNumber, formatTime } from '../data/format'
import { APPOINTMENT_STATUS, STATUS_MARK } from '../constants'

/* The clinic's booked appointments for one day, moving any of them, and
   checking in today's (SCRUM-105).

   Real data, not sample: reception may see and move every patient's
   appointment, and the server checks that on every call. The day shown and
   the appointment being moved live in the URL (`?day=…&move=…`), like the
   chosen doctor, so a refresh comes back to the same place. */

const DESK_LABEL = {
  [APPOINTMENT_STATUS.BOOKED]: 'Booked',
  [APPOINTMENT_STATUS.CHECKED_IN]: 'Checked in',
  [APPOINTMENT_STATUS.COMPLETED]: 'Completed',
  [APPOINTMENT_STATUS.CANCELLED]: 'Cancelled',
  [APPOINTMENT_STATUS.MISSED]: 'Missed',
}

/* Today as YYYY-MM-DD in local time. toISOString would give the UTC date,
   which is yesterday for the first hours of a Beirut morning. */
function localToday() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function shiftDay(date, days) {
  const [y, m, d] = date.split('-').map(Number)
  const next = new Date(y, m - 1, d + days)
  return `${next.getFullYear()}-${String(next.getMonth() + 1).padStart(2, '0')}-${String(next.getDate()).padStart(2, '0')}`
}

/* `openQueues` names the queues open right now; a refusal given under other
   ones (say "queue not open", before the desk opened it) is no longer true,
   so it stops being shown. `onCheckedIn` lets the page refresh its board. */
function DeskAppointments({ openQueues = '', onCheckedIn }) {
  const [params, setParams] = useSearchParams()
  const today = localToday()
  const day = /^\d{4}-\d{2}-\d{2}$/.test(params.get('day') ?? '') ? params.get('day') : today
  const moveId = Number(params.get('move')) || null

  const { items, loading, error, reschedule, checkIn } = useDayAppointments(day)
  const [scrollRef, edges] = useSideScroll()
  // The appointment just moved. It may now be on another day, so it is kept
  // from the server's answer rather than looked up in this day's list.
  const [justMoved, setJustMoved] = useState(null)
  // The last check-in: who, and the ticket the server gave them; or why it
  // was refused. One at a time, like `justMoved`.
  const [checkedIn, setCheckedIn] = useState(null)
  const [checkInError, setCheckInError] = useState(null)
  const [checkingId, setCheckingId] = useState(null)

  /* Changes only this section's keys, so the chosen doctor stays chosen. */
  const update = (changes) => {
    setJustMoved(null)
    setCheckedIn(null)
    setCheckInError(null)
    setParams((p) => {
      const next = new URLSearchParams(p)
      Object.entries(changes).forEach(([k, v]) => (v == null ? next.delete(k) : next.set(k, v)))
      return next
    })
  }

  // Only a booked appointment can be moved; the server says the same.
  const moving = items.find((a) => a.id === moveId && a.status === APPOINTMENT_STATUS.BOOKED)

  /* The server says why a check-in is refused (queue not open, already in
     the queue...) in words meant for the desk, so they are shown as they are. */
  const checkInPatient = async (a) => {
    setJustMoved(null)
    setCheckedIn(null)
    setCheckInError(null)
    setCheckingId(a.id)
    try {
      const ticket = await checkIn(a.id)
      setCheckedIn({ patientName: a.patientName, doctor: a.doctor, ticket: ticket?.ticket })
      onCheckedIn?.()
    } catch (err) {
      setCheckInError({
        text: `${a.patientName} was not checked in. ${err.message || 'Please try again.'}`,
        openQueues,
      })
    } finally {
      setCheckingId(null)
    }
  }

  if (moving) {
    return (
      <div className="stack">
        <p className="meta">
          Moving {moving.patientName}&rsquo;s appointment ({formatRecordNumber(moving.patientId)} · {moving.phoneNumber})
        </p>
        <RescheduleAppointment
          appointment={moving}
          onReschedule={reschedule}
          onDone={(moved) => {
            update({ move: null })
            setJustMoved({ ...moved, patientName: moving.patientName })
          }}
          onClose={() => update({ move: null })}
        />
      </div>
    )
  }

  const frame = ['desk-frame', edges.left && 'desk-frame--left', edges.right && 'desk-frame--right']
    .filter(Boolean).join(' ')

  return (
    <div className="sheet">
      <div className="sheet__head desk-appts__head">
        <div>
          <p className="dash__name">Appointments</p>
          <p className="meta">
            {formatDay(day, { year: true })}
            {day === today ? ' · today' : ''}
            {!loading && !error ? ` · ${items.length} ${items.length === 1 ? 'appointment' : 'appointments'}` : ''}
          </p>
        </div>
        <div className="desk__acts">
          <button type="button" className="btn btn--sm btn--quiet" onClick={() => update({ day: shiftDay(day, -1) })}
            aria-label="Previous day">←</button>
          {day !== today && (
            <button type="button" className="btn btn--sm btn--quiet" onClick={() => update({ day: null })}>
              Today
            </button>
          )}
          <button type="button" className="btn btn--sm btn--quiet" onClick={() => update({ day: shiftDay(day, 1) })}
            aria-label="Next day">→</button>
        </div>
      </div>

      {justMoved && (
        <div className="sheet__body" role="status" style={{ paddingBlock: 16 }}>
          <p>
            {justMoved.patientName}&rsquo;s {justMoved.service} moved to {formatDay(justMoved.datetime)} at{' '}
            {formatTime(justMoved.datetime)}.
          </p>
        </div>
      )}

      {checkedIn && (
        <div className="sheet__body" role="status" style={{ paddingBlock: 16 }}>
          <p>
            {checkedIn.patientName} checked in
            {checkedIn.ticket ? `, ticket ${checkedIn.ticket}` : ''}. They are now in {checkedIn.doctor}&rsquo;s queue.
          </p>
        </div>
      )}
      {checkInError && checkInError.openQueues === openQueues && (
        <p className="form-error desk-error" role="alert">{checkInError.text}</p>
      )}

      {error && (
        <div className="empty" role="alert"><p>{errorMessage(error)}</p></div>
      )}
      {!error && loading && items.length === 0 && (
        <div className="empty"><p>Loading appointments…</p></div>
      )}
      {!error && !loading && items.length === 0 && (
        <div className="empty"><p>No appointments on this day.</p></div>
      )}

      {items.length > 0 && (
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
                    <th>Time</th><th>Patient</th><th>Doctor</th><th>Treatment</th><th>Status</th>
                    <th className="desk__right">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map((a) => (
                    <tr key={a.id}>
                      <td className="desk__tick">{formatTime(a.datetime)}</td>
                      <td>
                        <div className="desk__name">{a.patientName}</div>
                        <p className="meta">{formatRecordNumber(a.patientId)} · {a.phoneNumber}</p>
                      </td>
                      <td>{a.doctor}</td>
                      <td>{a.service} <span className="meta">· {a.minutes} min</span></td>
                      <td><Mark status={STATUS_MARK[a.status]} label={DESK_LABEL[a.status] ?? a.status} /></td>
                      <td className="desk__right">
                        {/* Only today's bookings can join today's queue; the
                            server refuses any other, so the button is not shown. */}
                        {a.status === APPOINTMENT_STATUS.BOOKED && day === today && (
                          <button
                            type="button"
                            className="btn btn--sm btn--live"
                            disabled={checkingId != null}
                            onClick={() => checkInPatient(a)}
                          >
                            {checkingId === a.id ? 'Checking in…' : 'Check in'}
                          </button>
                        )}{' '}
                        {a.status === APPOINTMENT_STATUS.BOOKED && (
                          <button type="button" className="btn btn--sm" onClick={() => update({ move: String(a.id) })}>
                            Reschedule
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}
    </div>
  )
}

/* Until the desk has its own sign-in route (SCRUM-59), this page can be
   opened by anyone, so say plainly why the list is empty. */
function errorMessage(error) {
  if (error.status === 401) return 'Sign in with a reception account to see appointments.'
  if (error.status === 403) return 'Only reception staff can see the clinic’s appointments.'
  return error.message || 'Could not load the appointments.'
}

export default DeskAppointments
