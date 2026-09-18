import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import SiteHeader from '../components/SiteHeader'
import SiteFooter from '../components/SiteFooter'
import QueueBoard from '../components/QueueBoard'
import Legend from '../components/Legend'
import Mark from '../components/Mark'
import Cell from '../components/Cell'
import QrPlaceholder from '../components/QrPlaceholder'
import BookingFlow from '../components/BookingFlow'
import RescheduleAppointment from '../components/RescheduleAppointment'
import { BOOKING_STEPS, useBookingDraft } from '../components/useBookingDraft'
import { useAuth } from '../auth/useAuth'
import { clinic } from '../data/mock'
import {
  useAppointments,
  useAvailability,
  useNotifications,
  useQueue,
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

function aheadOfYou(count) {
  if (count === 0) return 'You are at the front of the queue'
  if (count === 1) return 'One person ahead of you'
  return `${count} people ahead of you`
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

function AppointmentRows({ items, cancellable, onReschedule }) {
  if (items.length === 0) {
    return (
      <div className="empty">
        <p>Nothing recorded here yet.</p>
      </div>
    )
  }

  return (
    <div className="rows">
      {items.map((a) => (
        <div className="row" key={a.id}>
          <div>
            <p className="row__title">{a.service}</p>
            <p className="meta row__meta">
              {formatDay(a.datetime, { year: true })} · {formatTime(a.datetime)} ·{' '}
              {a.doctor}
            </p>
          </div>
          <div className="row__side">
            <Mark
              status={STATUS_MARK[a.status]}
              label={APPOINTMENT_LABEL[a.status] ?? a.status}
            />
            {cancellable && (
              <div className="row__actions">
                <button
                  type="button"
                  className="btn btn--quiet"
                  onClick={() => onReschedule(a)}
                >
                  Reschedule
                </button>
                <button type="button" className="btn btn--quiet">
                  Cancel
                </button>
              </div>
            )}
          </div>
        </div>
      ))}
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
  const queue = useQueue(user.patientId)
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

  const { yourTicket } = queue
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
                  note={`${clinic.name} · updated ${queue.updatedAt}`}
                  right={yourTicket ? 'Live' : undefined}
                />
                {yourTicket ? (
                  <>
                    <div className="ticket">
                      <QrPlaceholder code={yourTicket.ticket} />
                      <div className="now">
                        <span className="meta">Your number</span>
                        <span className="now__num">{yourTicket.ticket}</span>
                        <p style={{ marginTop: 8 }}>
                          {aheadOfYou(queue.ahead)} — about {yourTicket.eta} left.
                        </p>
                        <p>
                          <span className="synthetic">Sample data</span>
                        </p>
                      </div>
                    </div>
                    <Legend items={['live', 'wait']} />
                    <div className="sheet__body" style={{ paddingBlock: 16 }}>
                      <QueueBoard entries={queue.entries} />
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
              />
            </div>
          )}

          {section === 'ticket' && (
            <div className="sheet">
              <SheetHead
                title="Walk-in queue"
                note={`${clinic.name} · updated ${queue.updatedAt}`}
                right={yourTicket ? 'Live' : undefined}
              />
              {yourTicket ? (
                <>
                  <div className="ticket">
                    <QrPlaceholder code={yourTicket.ticket} />
                    <div className="now">
                      <span className="meta">Now serving</span>
                      <span className="now__num">{queue.nowServing}</span>
                      <p style={{ marginTop: 8 }}>
                        You hold {yourTicket.ticket} — about {yourTicket.eta} left.
                      </p>
                      <p>
                        <span className="synthetic">Sample data</span>
                      </p>
                    </div>
                  </div>
                  <Legend items={['live', 'wait']} />
                  <div className="sheet__body" style={{ paddingBlock: 16 }}>
                    <QueueBoard entries={queue.entries} />
                  </div>
                  <div className="sheet__foot">
                    <button type="button" className="btn btn--quiet">
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
                <form
                  key={user.userId}
                  onSubmit={(e) => e.preventDefault()}
                  className="stack"
                >
                  <div className="cells cells--two">
                    <Cell label="Full name" defaultValue={user.fullName} />
                    <Cell
                      label="Phone number"
                      type="tel"
                      note="You sign in with this and your name"
                      defaultValue={user.phoneNumber ?? ''}
                    />
                  </div>
                  <div className="cells cells--two">
                    <Cell
                      label="Preferred language"
                      defaultValue="English"
                      note="Arabic support is planned"
                    />
                  </div>
                  <div className="auth__actions">
                    <button type="submit" className="btn btn--live">
                      Save changes
                    </button>
                  </div>
                </form>
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
