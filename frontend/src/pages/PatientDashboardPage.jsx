import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import SiteHeader from '../components/SiteHeader'
import SiteFooter from '../components/SiteFooter'
import QueueBoard from '../components/QueueBoard'
import Legend from '../components/Legend'
import Mark from '../components/Mark'
import Cell from '../components/Cell'
import TicketCard from '../components/TicketCard'
import BookingFlow from '../components/BookingFlow'
import RescheduleAppointment from '../components/RescheduleAppointment'
import { BOOKING_STEPS, useBookingDraft } from '../components/useBookingDraft'
import { useAuth } from '../auth/useAuth'
import { clinic } from '../data/mock'
import {
  useAppointments,
  useAvailability,
  useNotifications,
  boardEntries,
  useMyTicket,
  usePublicBoard,
} from '../data/hooks'
import { formatDay, formatRecordNumber, formatTime } from '../data/format'
import { APPOINTMENT_STATUS, STATUS_MARK } from '../constants'

const SECTIONS = [
  { id: 'today', label: 'Today' },
  { id: 'book', label: 'Book appointment', subs: BOOKING_STEPS },
  {
    id: 'appointments',
    label: 'My appointments',
    subs: [
      { id: 'upcoming', label: 'Upcoming' },
      { id: 'past', label: 'Past' },
    ],
  },
  { id: 'ticket', label: 'My queue ticket' },
  { id: 'profile', label: 'Profile' },
  { id: 'notifications', label: 'Notifications' },
]

const SECTION_IDS = new Set(SECTIONS.map((s) => s.id))

const APPOINTMENT_LABEL = {
  [APPOINTMENT_STATUS.BOOKED]: 'Booked',
  [APPOINTMENT_STATUS.COMPLETED]: 'Completed',
  [APPOINTMENT_STATUS.CANCELLED]: 'Cancelled',
  [APPOINTMENT_STATUS.MISSED]: 'Missed',
}

function SheetHead({ title, note, right }) {
  return (
    <div className="sheet__head">
      <div>
        <p className="dash__name">{title}</p>
        {note && <p className="meta">{note}</p>}
      </div>
      {right && <p className="meta meta--right">{right}</p>}
    </div>
  )
}

function AppointmentRows({
  items,
  cancellable,
  onReschedule,
  confirmingId,
  cancelling,
  cancelError,
  onCancelStart,
  onCancelConfirm,
  onCancelKeep,
}) {
  if (items.length === 0) {
    return (
      <div className="empty">
        <p>Nothing recorded here yet.</p>
      </div>
    )
  }

  return (
    <div className="rows">
      {items.map((a) => {
        /* Cancelling is two presses, and the second happens on the row
           itself so the appointment being cancelled stays in view. The
           row's id lives in the URL, like everything else on this page. */
        const confirming = String(a.id) === confirmingId
        return (
          <div className="row" key={a.id}>
            <div>
              <p className="row__title">{a.service}</p>
              <p className="meta row__meta">
                {formatDay(a.datetime, { year: true })} · {formatTime(a.datetime)} ·{' '}
                {a.doctor}
              </p>
              {confirming && cancelError && (
                <p className="form-error" role="alert">
                  {cancelError}
                </p>
              )}
            </div>
            <div className="row__side">
              <Mark
                status={STATUS_MARK[a.status]}
                label={APPOINTMENT_LABEL[a.status] ?? a.status}
              />
              {cancellable && !confirming && (
                <div className="row__actions">
                  <button
                    type="button"
                    className="btn btn--quiet"
                    onClick={() => onReschedule(a)}
                  >
                    Reschedule
                  </button>
                  <button
                    type="button"
                    className="btn btn--quiet"
                    onClick={() => onCancelStart(a)}
                  >
                    Cancel
                  </button>
                </div>
              )}
              {cancellable && confirming && (
                <div className="row__actions">
                  <button
                    type="button"
                    className="btn btn--live"
                    disabled={cancelling}
                    onClick={() => onCancelConfirm(a)}
                  >
                    {cancelling ? 'Cancelling…' : 'Yes, cancel it'}
                  </button>
                  <button
                    type="button"
                    className="btn btn--quiet"
                    disabled={cancelling}
                    onClick={onCancelKeep}
                  >
                    Keep it
                  </button>
                </div>
              )}
            </div>
          </div>
        )
      })}
    </div>
  )
}

/* Most patients are not in a queue at any given moment — only walk-ins who
   scanned at the desk today are. */
function NoTicket() {
  return (
    <div className="empty">
      <p>
        You are not in a queue right now. At the clinic, scan the QR code at the
        desk to take a ticket.
      </p>
    </div>
  )
}

function PatientDashboardPage() {
  
  const { user, signOut } = useAuth()
  // Their real ticket today, if any, and their doctor's line (numbers only).
  const { ticket: yourTicket } = useMyTicket({ me: true })
  const { queues } = usePublicBoard()
  const myQueue = yourTicket ? queues.find((q) => q.doctorName === yourTicket.doctorName) : null
  const appointments = useAppointments()
  const notifications = useNotifications()
  const booking = useBookingDraft()
  // The server works out free times: it knows the doctor's hours and everyone
  // else's bookings, which this screen must never see.
  const { days: availability } = useAvailability(booking.doctor?.id, booking.service?.id)

  /* Where the patient is on the dashboard lives in the URL, so a refresh
     keeps it and the browser's Back button steps back through it. */
  const [params, setParams] = useSearchParams()
  const section = SECTION_IDS.has(params.get('section')) ? params.get('section') : 'today'
  const tab = params.get('tab') === 'past' ? 'past' : 'upcoming'
  const requestedStep = params.get('step')
  const bookingStep =
    requestedStep === 'done' && booking.placed ? 'done' : booking.stepFor(requestedStep)

  const open = (id, extra = {}) =>
    setParams(id === 'today' ? {} : { section: id, ...extra })
  const openStep = (step) => open('book', { step })

  /* The appointment being moved, and the one just moved, live in the URL
     like the rest of the dashboard. So the confirmation belongs to the page
     it was shown on: leave My appointments and it does not come back.
     Only an upcoming (still booked) one can be moved. */
  const findUpcoming = (param) =>
    section === 'appointments'
      ? appointments.upcoming.find((a) => String(a.id) === params.get(param))
      : undefined
  const moving = findUpcoming('move')
  const justMoved = findUpcoming('moved')
  const startMove = (a) => {
    open('appointments', { move: String(a.id) })
    window.scrollTo(0, 0)
  }
  const finishMove = (moved) => {
    open('appointments', { moved: String(moved.id) })
    // The picker is taller than the list, so the confirmation would be off screen.
    window.scrollTo(0, 0)
  }

  /* Cancelling works the same way: the row asked about lives in the URL as
     ?cancel=<id>, and the one just cancelled as ?cancelled=<id>. The
     cancelled one is looked up in `all`, not `upcoming` - it has just left
     that list, which is the point. */
  const confirmingCancel = section === 'appointments' ? params.get('cancel') : null
  const justCancelled =
    section === 'appointments'
      ? appointments.all.find((a) => String(a.id) === params.get('cancelled'))
      : undefined
  const [cancelling, setCancelling] = useState(false)
  const [cancelError, setCancelError] = useState(null)
  const startCancel = (a) => {
    setCancelError(null)
    open('appointments', { cancel: String(a.id) })
  }
  const keepAppointment = () => {
    setCancelError(null)
    open('appointments')
  }
  const confirmCancel = async (a) => {
    if (cancelling) return
    setCancelling(true)
    setCancelError(null)
    try {
      await appointments.cancel(a.id)
      open('appointments', { cancelled: String(a.id) })
    } catch (e) {
      setCancelError(e.message || 'Could not cancel this appointment. Please try again.')
    } finally {
      setCancelling(false)
    }
  }

  const { markAllRead } = notifications

  // Opening the notifications is what reads them.
  useEffect(() => {
    if (section === 'notifications') markAllRead()
  }, [section, markAllRead])

  const [bookingError, setBookingError] = useState(null)
  const [placing, setPlacing] = useState(false)

  /* The server has the final say. Someone else may have taken the slot while
     this screen was open, so the appointment shown afterwards is the one that
     came back, not the one we sent. */
  async function confirmBooking() {
    if (placing) return
    setPlacing(true)
    setBookingError(null)
    try {
      const appointment = await appointments.book({
        doctorId: booking.doctor.id,
        serviceId: booking.service.id,
        datetime: `${booking.day}T${booking.time}`,
      })
      booking.place(appointment)
      openStep('done')
    } catch (error) {
      setBookingError(
        error.code === 'SLOT_TAKEN'
          ? 'That time has just been taken. Please choose another.'
          : error.message || 'Could not book that time. Please try again.',
      )
    } finally {
      setPlacing(false)
    }
  }

  const isCurrentSub = (sectionId, subId) =>
    sectionId === 'book' ? bookingStep === subId : tab === subId

  return (
    <div className="page">
      <title>{`${user.fullName}'s Dashboard - QCare`}</title>
      <SiteHeader />

      <main className="wrap dash">
        <aside className="dash__index">
          <div className="dash__who">
            <p className="dash__name">{user.fullName}</p>
            {user.patientId != null && (
              <p className="meta">Record {formatRecordNumber(user.patientId)}</p>
            )}
          </div>

          <nav className="dash__nav" aria-label="Dashboard">
            {SECTIONS.map((s) => (
              <div key={s.id}>
                <button
                  type="button"
                  className="dash__link"
                  aria-current={section === s.id}
                  onClick={() => open(s.id)}
                >
                  {s.label}
                  {s.id === 'notifications' && notifications.unread > 0 && (
                    <span className="meta">{notifications.unread}</span>
                  )}
                  {s.id === 'ticket' && yourTicket && (
                    <span className="meta" style={{ color: 'var(--live)' }}>
                      {yourTicket.ticket}
                    </span>
                  )}
                </button>

                {s.subs && section === s.id && (
                  <div className="dash__sub">
                    {s.subs.map((c) => (
                      <button
                        key={c.id}
                        type="button"
                        aria-current={isCurrentSub(s.id, c.id)}
                        disabled={s.id === 'book' && !booking.reached[c.id]}
                        onClick={() =>
                          s.id === 'book' ? openStep(c.id) : open(s.id, { tab: c.id })
                        }
                      >
                        {c.label}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            ))}

            <button
              type="button"
              className="dash__link"
              onClick={signOut}
            >
              Log out
            </button>
          </nav>
        </aside>

        <div className="dash__main">
          {section === 'today' && (
            <>
              <div className="sheet">
                <SheetHead
                  title="Your queue ticket"
                  note={`${clinic.name} · updates every 15 seconds`}
                  right={yourTicket ? 'Live' : undefined}
                />
                {yourTicket ? (
                  <>
                    <TicketCard ticket={yourTicket} />
                    <div className="sheet__foot">
                      <button type="button" className="btn btn--quiet" onClick={() => open('ticket')}>
                        See the whole line
                      </button>
                    </div>
                  </>
                ) : (
                  <NoTicket />
                )}
              </div>

              <div className="sheet">
                <SheetHead title="Next appointment" />
                <AppointmentRows
                  items={appointments.upcoming.slice(0, 1)}
                  cancellable
                  onReschedule={startMove}
                  confirmingId={confirmingCancel}
                  cancelling={cancelling}
                  cancelError={cancelError}
                  onCancelStart={startCancel}
                  onCancelConfirm={confirmCancel}
                  onCancelKeep={keepAppointment}
                />
              </div>
            </>
          )}

          {section === 'book' && (
            <BookingFlow
              booking={booking}
              step={bookingStep}
              onStep={openStep}
              availability={availability}
              onConfirm={confirmBooking}
              placing={placing}
              error={bookingError}
            />
          )}

          {section === 'appointments' && moving && (
            <RescheduleAppointment
              key={moving.id}
              appointment={moving}
              onReschedule={appointments.reschedule}
              onDone={finishMove}
              onClose={() => open('appointments')}
            />
          )}

          {section === 'appointments' && !moving && (
            <div className="sheet">
              <SheetHead
                title="My appointments"
                note={`Record ${formatRecordNumber(user.patientId)}`}
              />
              {justMoved && (
                <div className="sheet__body" role="status" style={{ paddingBlock: 16 }}>
                  <p>
                    {justMoved.service} moved to {formatDay(justMoved.datetime)} at{' '}
                    {formatTime(justMoved.datetime)}.
                  </p>
                </div>
              )}
              {justCancelled && (
                <div className="sheet__body" role="status" style={{ paddingBlock: 16 }}>
                  <p>
                    {justCancelled.service} on {formatDay(justCancelled.datetime)} at{' '}
                    {formatTime(justCancelled.datetime)} cancelled. It is in your past
                    appointments.
                  </p>
                </div>
              )}
              <div className="tabs">
                {['upcoming', 'past'].map((t) => (
                  <button
                    key={t}
                    type="button"
                    className="tab"
                    aria-current={tab === t}
                    onClick={() => open('appointments', { tab: t })}
                  >
                    {t}
                  </button>
                ))}
              </div>
              <Legend items={['wait', 'done', 'miss']} />
              <AppointmentRows
                items={appointments[tab]}
                cancellable={tab === 'upcoming'}
                onReschedule={startMove}
                confirmingId={confirmingCancel}
                cancelling={cancelling}
                cancelError={cancelError}
                onCancelStart={startCancel}
                onCancelConfirm={confirmCancel}
                onCancelKeep={keepAppointment}
              />
            </div>
          )}

          {section === 'ticket' && (
            <div className="sheet">
              <SheetHead
                title="Walk-in queue"
                note={`${clinic.name} · updates every 15 seconds`}
                right={yourTicket ? 'Live' : undefined}
              />
              {yourTicket ? (
                <>
                  <TicketCard ticket={yourTicket} />
                  {myQueue && myQueue.entries.length > 0 && (
                    <>
                      <Legend items={['live', 'wait']} />
                      <div className="sheet__body" style={{ paddingBlock: 16 }}>
                        <QueueBoard entries={boardEntries(myQueue.entries, yourTicket.ticket)} />
                      </div>
                    </>
                  )}
                  <div className="sheet__foot">
                    {/* Leaving the queue needs the patient ticket endpoints
                        (SCRUM-56). Shown disabled rather than hidden, like the
                        desk's unfinished buttons, so it cannot silently do
                        nothing. */}
                    <button type="button" className="btn btn--quiet" disabled title="Not built yet">
                      Give up my place
                    </button>
                  </div>
                </>
              ) : (
                <NoTicket />
              )}
            </div>
          )}

          {section === 'profile' && (
            <div className="sheet">
              <SheetHead
                title="Profile"
                note={`Record ${formatRecordNumber(user.patientId)}`}
              />
              <div className="sheet__body">
                {/* Keyed by user so the fields refill if the user changes. */}
                {/* Read-only by decision: the name and phone number are what a
                    patient signs in with and how the desk finds their record,
                    so they are changed at the desk, never from here. */}
                <div key={user.userId} className="stack">
                  <div className="cells cells--two">
                    <Cell label="Full name" value={user.fullName} readOnly />
                    <Cell
                      label="Phone number"
                      type="tel"
                      note="You sign in with this and your name"
                      value={user.phoneNumber ?? ''}
                      readOnly
                    />
                  </div>
                  <div className="cells cells--two">
                    <Cell
                      label="Preferred language"
                      value="English"
                      note="Arabic support is planned"
                      readOnly
                    />
                  </div>
                  <p className="meta">
                    To change your name or phone number, ask at the front desk.
                  </p>
                </div>
              </div>
            </div>
          )}

          {section === 'notifications' && (
            <div className="sheet">
              <SheetHead title="Notifications" />
              <Legend items={['live', 'wait', 'miss']} />
              <div className="rows">
                {notifications.items.map((n) => (
                  <div className="row" key={n.id}>
                    <div>
                      <p className="row__title">{n.text}</p>
                      <p className="meta row__meta">{n.at}</p>
                    </div>
                    <div className="row__side">
                      <Mark status={n.mark} />
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      </main>

      <SiteFooter />
    </div>
  )
}

export default PatientDashboardPage
