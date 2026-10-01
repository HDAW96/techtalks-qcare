import { useEffect, useState } from 'react'
import SiteHeader from '../components/SiteHeader'
import SiteFooter from '../components/SiteFooter'
import Mark from '../components/Mark'
import PatientHistoryButton from '../components/PatientHistoryButton'
import { completeVisit, useDoctorSchedule, usePublicBoard, waitLabel } from '../data/hooks'
import { formatDay, formatRecordNumber, formatTime } from '../data/format'
import { useAuth } from '../auth/useAuth'
import { APPOINTMENT_STATUS, STATUS_MARK, TICKET_STATUS } from '../constants'
import '../styles/doctor.css'

/* The doctor's own sheet for the day (SCRUM-66/67).

   Read between patients, at a glance, from across the surgery: who is in the
   chair now, who is waiting after them, and what the rest of the day holds.
   The doctor does not run the queue — the desk does — so nothing here moves
   a ticket. Everything is real data: the chair and the day list come from
   the doctor-only /doctors/me/schedule, the waiting line from the public
   board, which carries ticket numbers only. */

const DAY_LABEL = {
  [APPOINTMENT_STATUS.BOOKED]: 'Booked',
  [APPOINTMENT_STATUS.COMPLETED]: 'Completed',
  [APPOINTMENT_STATUS.CANCELLED]: 'Cancelled',
  [APPOINTMENT_STATUS.MISSED]: 'Missed',
}

/* The wall clock, re-read every half minute: enough for "12 min in the
   chair" and for the now-line on the day list, without re-rendering every
   second. */
function useNow(everyMs = 30000) {
  const [now, setNow] = useState(() => new Date())
  useEffect(() => {
    const id = setInterval(() => setNow(new Date()), everyMs)
    return () => clearInterval(id)
  }, [everyMs])
  return now
}

/* "2026-09-24T10:41" read as local time, never through UTC. */
function localDate(value) {
  const [day, time] = value.split('T')
  const [y, m, d] = day.split('-').map(Number)
  const [h, min] = time.split(':').map(Number)
  return new Date(y, m - 1, d, h, min)
}

const clock = (date) =>
  `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`

const plural = (n, one, many) => `${n} ${n === 1 ? one : many}`

function DoctorPage() {
  const { user } = useAuth()
  const { schedule, error, loading, reload } = useDoctorSchedule()
  const { queues } = usePublicBoard()
  const now = useNow()

  const name = schedule?.doctorName ?? user?.fullName ?? 'Doctor'
  const chair = schedule?.nowServing ?? null

  /* Which of today's appointments the person in the chair is here for.
     Matched on the patient, because a queue ticket carries no appointment -
     nothing links the two yet (see SCRUM-80). A walk-in has no appointment at
     all, and then there is nothing to complete: the desk closes their ticket. */
  const chairAppointment =
    chair == null
      ? null
      : (schedule?.appointments ?? []).find(
          (a) => a.patientId === chair.patientId && a.status === APPOINTMENT_STATUS.BOOKED,
        ) ?? null
  const appointments = schedule?.appointments ?? []
  const queue = schedule ? queues.find((q) => q.doctorId === schedule.doctorId) ?? null : null

  return (
    <div className="page">
      <title>{`${user.fullName}'s Dashboard - QCare`}</title>
      <SiteHeader />

      <main className="wrap dr">
        <header className="title-band dr-title">
          <div>
            <h1>{name}</h1>
            <p className="meta">
              {schedule ? formatDay(schedule.date, { year: true }) : 'Today'} · Your day at the clinic
            </p>
          </div>
          <p className="meta dr-title__pulse" role="status">
            {schedule && error ? (
              <span className="dr-stale">Could not refresh. Showing what was last received.</span>
            ) : schedule ? (
              <>
                <span className="dr-pulse" aria-hidden="true" />
                Updates on its own
              </>
            ) : null}
          </p>
        </header>

        {!schedule && loading && (
          <div className="sheet">
            <div className="empty" role="status"><p>Loading your day…</p></div>
          </div>
        )}

        {/* No schedule at all: say so and offer a retry, rather than an empty
            day that reads as "no patients". */}
        {!schedule && error && !loading && (
          <div className="sheet">
            <div className="empty">
              <p>{error.message || 'Your schedule could not be loaded.'}</p>
              <button type="button" className="btn" onClick={reload}>Try again</button>
            </div>
          </div>
        )}

        {schedule && (
          <>
            <div className="dr-top">
              <Chair chair={chair} now={now} appointment={chairAppointment} onDone={reload} />
              <Waiting queue={queue} />
            </div>
            <DayList appointments={appointments} chair={chair} now={now} date={schedule.date} />
          </>
        )}
      </main>

      <SiteFooter />
    </div>
  )
}

/* ---------- in the chair ---------- */

function Chair({ chair, now, appointment, onDone }) {
  if (!chair) {
    return (
      <section className="sheet dr-chair dr-chair--empty" aria-labelledby="dr-chair-title">
        <div className="sheet__head">
          <h2 id="dr-chair-title" className="dash__name">In the chair</h2>
        </div>
        <div className="dr-chair__body">
          <span className="now__num dr-chair__void" aria-hidden="true">—</span>
          <div className="dr-chair__who">
            <p className="dr-chair__name">The chair is free</p>
            <p className="dr-chair__line">
              Reception calls the next patient in. They appear here without a reload.
            </p>
          </div>
        </div>
      </section>
    )
  }

  const started = chair.startedAt ? localDate(chair.startedAt) : null
  const elapsed = started ? Math.max(0, Math.floor((now - started) / 60000)) : null
  const planned = chair.minutes ?? null
  const over = elapsed != null && planned != null && elapsed > planned ? elapsed - planned : 0
  const used = elapsed != null && planned ? Math.min(elapsed / planned, 1) : 0

  return (
    <section className="sheet dr-chair" aria-labelledby="dr-chair-title">
      <div className="sheet__head">
        <h2 id="dr-chair-title" className="dash__name">In the chair</h2>
        <Mark status="live" label="Now" />
      </div>
      <div className="dr-chair__body">
        <div className="now">
          <span className="meta">Ticket</span>
          <span className="now__num">{chair.ticket}</span>
        </div>
        <div className="dr-chair__who">
          <p className="dr-chair__name">{chair.patientName}</p>
          <p className="meta">
            {formatRecordNumber(chair.patientId)} · {chair.service}
            {planned != null && ` · ${planned} min booked`}
          </p>

          {started && (
            <div className="dr-time">
              <p className="dr-time__line">
                Called in at <strong>{clock(started)}</strong>
                {' · '}
                {over > 0 ? (
                  <span className="dr-time__over">{over} min over</span>
                ) : (
                  <>{plural(elapsed, 'minute', 'minutes')} so far</>
                )}
              </p>
              {planned != null && (
                <div
                  className={over > 0 ? 'dr-time__track dr-time__track--over' : 'dr-time__track'}
                  role="img"
                  aria-label={`${elapsed} of ${planned} booked minutes used`}
                >
                  <span style={{ transform: `scaleX(${used})` }} />
                </div>
              )}
            </div>
          )}

          <div className="dr-chair__acts">
            <PatientHistoryButton
              patientId={chair.patientId ?? null}
              patientName={chair.patientName ?? ''}
              className="btn btn--live"
            />
            <CompleteVisit appointment={appointment} onDone={onDone} />
          </div>
        </div>
      </div>
    </section>
  )
}

/* ---------- finishing the visit ---------- */

/* "Complete visit", and the notes the doctor writes as they finish.

   Two presses: the first opens the notes box, the second saves. Nothing is
   filled in for them and notes are optional - a doctor standing over a
   patient should not have to type to close a visit.

   Only shown when the person in the chair has a booked appointment today,
   because that appointment is what is being completed. A walk-in has none,
   and the desk closes their ticket instead; saying so is more use than a
   button that cannot work. */
function CompleteVisit({ appointment, onDone }) {
  const [open, setOpen] = useState(false)
  const [notes, setNotes] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)

  if (!appointment) {
    return (
      <p className="meta dr-chair__walkin">
        Walk-in — reception marks them done when they leave the chair.
      </p>
    )
  }

  async function save() {
    if (saving) return
    setSaving(true)
    setError(null)
    try {
      await completeVisit(appointment.appointmentId, notes)
      setOpen(false)
      setNotes('')
      // The chair empties because the server said so, not because this
      // screen assumed it would.
      onDone?.()
    } catch (e) {
      setError(e.message || 'Could not complete this visit. Please try again.')
      setSaving(false)
    }
  }

  if (!open) {
    return (
      <button type="button" className="btn" onClick={() => setOpen(true)}>
        Complete visit
      </button>
    )
  }

  return (
    <div className="dr-complete">
      <label className="cell__label" htmlFor="dr-notes">
        Clinical notes <span className="cell__optional">optional</span>
      </label>
      <textarea
        id="dr-notes"
        className="cell__input dr-complete__notes"
        rows={3}
        maxLength={2000}
        placeholder="What was done, and anything the next visit should know."
        value={notes}
        disabled={saving}
        onChange={(e) => setNotes(e.target.value)}
      />
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
      <div className="dr-complete__acts">
        <button type="button" className="btn btn--live" disabled={saving} onClick={save}>
          {saving ? 'Saving…' : 'Save and finish'}
        </button>
        <button
          type="button"
          className="btn btn--quiet"
          disabled={saving}
          onClick={() => {
            setError(null)
            setOpen(false)
          }}
        >
          Not yet
        </button>
      </div>
    </div>
  )
}

/* ---------- waiting after them ---------- */

/* Ticket numbers only: the board is public, so it never names anyone. The
   doctor gets names from the day list and from the chair. */
function Waiting({ queue }) {
  const waiting = queue ? queue.entries.filter((e) => e.status === TICKET_STATUS.WAITING) : []
  const last = Math.max(waiting.length - 1, 1)

  let summary = 'Not opened yet today'
  if (queue) {
    summary = `${queue.status === 'OPEN' ? 'Queue open' : 'Queue closed'} · ${waiting.length} waiting`
  }

  return (
    <section className="sheet dr-wait" aria-labelledby="dr-wait-title">
      <div className="sheet__head">
        <h2 id="dr-wait-title" className="dash__name">Waiting</h2>
        <p className="meta meta--right">{summary}</p>
      </div>

      {!queue && (
        <p className="dr-wait__note">
          Reception has not opened your queue today. Walk-ins appear here once it opens.
        </p>
      )}
      {queue && waiting.length === 0 && (
        <p className="dr-wait__note">
          Nobody is waiting. {queue.status === 'OPEN' ? 'New walk-ins appear here as they join.' : ''}
        </p>
      )}

      {waiting.length > 0 && (
        <ol className="board dr-wait__board">
          {waiting.map((entry, i) => (
            <li
              key={entry.ticket}
              className="qrow"
              style={{ '--rank-lift': (1 - i / last).toFixed(3) }}
            >
              <span className="qrow__num">{entry.ticket}</span>
              <span className="qrow__name" />
              <span className="qrow__eta">
                <Mark status="wait" label={waitLabel(entry.etaMinutes) || 'Waiting'} />
              </span>
            </li>
          ))}
        </ol>
      )}
    </section>
  )
}

/* ---------- the day ---------- */

function DayList({ appointments, chair, now, date }) {
  const sorted = [...appointments].sort((a, b) => a.datetime.localeCompare(b.datetime))
  const isToday = date === `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
  const nowTime = clock(now)

  const booked = sorted.filter((a) => a.status === APPOINTMENT_STATUS.BOOKED)
  const done = sorted.filter((a) => a.status === APPOINTMENT_STATUS.COMPLETED).length
  const cancelled = sorted.filter((a) => a.status === APPOINTMENT_STATUS.CANCELLED).length

  // The first booked visit still ahead of the clock, unless its patient is
  // already in the chair.
  const next = isToday
    ? booked.find((a) => formatTime(a.datetime) >= nowTime && a.patientId !== chair?.patientId)
    : null

  // Where the now-line sits: before the first visit that has not started.
  const nowIndex = isToday ? sorted.findIndex((a) => formatTime(a.datetime) > nowTime) : -1
  const showNowLine = isToday && sorted.length > 0

  const parts = [plural(booked.length, 'booked', 'booked')]
  if (done) parts.push(`${done} completed`)
  if (cancelled) parts.push(`${cancelled} cancelled`)

  const nowLine = (
    <li key="now" className="dr-now" aria-label={`Now, ${nowTime}`}>
      <span className="dr-now__time">{nowTime}</span>
      <span className="dr-now__rule" aria-hidden="true" />
    </li>
  )

  return (
    <section className="sheet dr-day" aria-labelledby="dr-day-title">
      <div className="sheet__head">
        <h2 id="dr-day-title" className="dash__name">Today&rsquo;s appointments</h2>
        <p className="meta meta--right">
          {sorted.length === 0 ? 'None booked' : parts.join(' · ')}
        </p>
      </div>

      {sorted.length === 0 ? (
        <div className="empty">
          <p>No appointments are booked for you today.</p>
          <p className="meta">Walk-ins still reach you through the queue.</p>
        </div>
      ) : (
        <>
          <div className="legend">
            <span className="legend__title">Legend</span>
            <Mark status="live" label="In the chair" />
            <Mark status="wait" label="Booked" />
            <Mark status="done" label="Completed" />
            <Mark status="miss" label="Cancelled or missed" />
          </div>

          <ol className="dr-rows">
            {sorted.flatMap((a, i) => {
              const inChair = chair != null && a.patientId === chair.patientId && a.status === APPOINTMENT_STATUS.BOOKED
              const isNext = next != null && a.appointmentId === next.appointmentId
              const closed = a.status !== APPOINTMENT_STATUS.BOOKED
              const className = [
                'dr-row',
                inChair && 'dr-row--live',
                isNext && 'dr-row--next',
                closed && 'dr-row--closed',
                a.status === APPOINTMENT_STATUS.CANCELLED && 'dr-row--cancelled',
              ]
                .filter(Boolean)
                .join(' ')

              return [
                showNowLine && i === nowIndex ? nowLine : null,
                <li key={a.appointmentId} className={className}>
                  <span className="dr-row__time">
                    {formatTime(a.datetime)}
                    {isNext && <span className="dr-row__next">Next</span>}
                  </span>
                  <span className="dr-row__who">
                    <span className="dr-row__name">{a.patientName}</span>
                    <span className="meta">{formatRecordNumber(a.patientId)}</span>
                  </span>
                  <span className="dr-row__what">
                    {a.service}
                    {a.minutes != null && <span className="dr-row__min"> · {a.minutes} min</span>}
                  </span>
                  <span className="dr-row__mark">
                    {inChair ? (
                      <Mark status="live" label="In the chair" />
                    ) : (
                      <Mark status={STATUS_MARK[a.status]} label={DAY_LABEL[a.status] ?? a.status} />
                    )}
                  </span>
                  <span className="dr-row__act">
                    <PatientHistoryButton
                      patientId={a.patientId ?? null}
                      patientName={a.patientName ?? ''}
                      className="btn btn--quiet dr-btn-sm"
                    />
                  </span>
                </li>,
              ]
            })}
            {showNowLine && nowIndex === -1 && nowLine}
          </ol>
        </>
      )}
    </section>
  )
}

export default DoctorPage
