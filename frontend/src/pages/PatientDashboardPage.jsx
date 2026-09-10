import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import SiteHeader from '../components/SiteHeader'
import SiteFooter from '../components/SiteFooter'
import QueueBoard from '../components/QueueBoard'
import Legend from '../components/Legend'
import Mark from '../components/Mark'
import Cell from '../components/Cell'
import QrPlaceholder from '../components/QrPlaceholder'
import BookingFlow from '../components/BookingFlow'
import { patient, clinic, queue, appointments, notifications } from '../data/mock'

const SECTIONS = [
  { id: 'today', label: 'Today' },
  {
    id: 'book',
    label: 'Book appointment',
    subs: [
      { id: 'service', label: 'Service' },
      { id: 'when', label: 'Date & time' },
      { id: 'confirm', label: 'Confirm' },
    ],
  },
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

const yourTicket = queue.entries.find((e) => e.you)

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

function AppointmentRows({ items, cancellable }) {
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
              {a.date} · {a.time} · {a.doctor}
            </p>
          </div>
          <div className="row__side">
            <Mark
              status={a.status}
              label={a.status === 'wait' ? 'Booked' : undefined}
            />
            {cancellable && (
              <div className="row__actions">
                <button type="button" className="btn btn--quiet">
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

function PatientDashboardPage() {
  const navigate = useNavigate()
  const [section, setSection] = useState('today')
  const [sub, setSub] = useState({ book: 'service', appointments: 'upcoming' })

  const setSubFor = (id, value) => setSub({ ...sub, [id]: value })

  return (
    <div className="page">
      <SiteHeader />

      <main className="wrap dash">
        <aside className="dash__index">
          <div className="dash__who">
            <p className="dash__name">{patient.name}</p>
            <p className="meta">Record {patient.recordNo}</p>
          </div>

          <nav className="dash__nav" aria-label="Dashboard">
            {SECTIONS.map((s) => (
              <div key={s.id}>
                <button
                  type="button"
                  className="dash__link"
                  aria-current={section === s.id}
                  onClick={() => setSection(s.id)}
                >
                  {s.label}
                  {s.id === 'notifications' && (
                    <span className="meta">{notifications.length}</span>
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
                        aria-current={sub[s.id] === c.id}
                        onClick={() => setSubFor(s.id, c.id)}
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
              onClick={() => navigate('/')}
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
                  right="Live"
                />
                <div className="ticket">
                  <QrPlaceholder code={yourTicket.ticket} />
                  <div className="now">
                    <span className="meta">Your number</span>
                    <span className="now__num">{yourTicket.ticket}</span>
                    <p style={{ marginTop: 8 }}>
                      Two people ahead of you — about {yourTicket.eta} left.
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
              </div>

              <div className="sheet">
                <SheetHead title="Next appointment" />
                <AppointmentRows
                  items={appointments.upcoming.slice(0, 1)}
                  cancellable
                />
              </div>
            </>
          )}

          {section === 'book' && (
            <BookingFlow
              step={sub.book}
              onStep={(id) => setSubFor('book', id)}
            />
          )}

          {section === 'appointments' && (
            <div className="sheet">
              <SheetHead
                title="My appointments"
                note={`Record ${patient.recordNo}`}
              />
              <div className="tabs">
                {['upcoming', 'past'].map((t) => (
                  <button
                    key={t}
                    type="button"
                    className="tab"
                    aria-current={sub.appointments === t}
                    onClick={() => setSubFor('appointments', t)}
                  >
                    {t}
                  </button>
                ))}
              </div>
              <Legend items={['wait', 'done', 'miss']} />
              <AppointmentRows
                items={appointments[sub.appointments]}
                cancellable={sub.appointments === 'upcoming'}
              />
            </div>
          )}

          {section === 'ticket' && (
            <div className="sheet">
              <SheetHead
                title="Walk-in queue"
                note={`${clinic.name} · updated ${queue.updatedAt}`}
                right="Live"
              />
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
            </div>
          )}

          {section === 'profile' && (
            <div className="sheet">
              <SheetHead title="Profile" note={`Record ${patient.recordNo}`} />
              <div className="sheet__body">
                <form onSubmit={(e) => e.preventDefault()} className="stack">
                  <div className="cells cells--two">
                    <Cell label="Full name" defaultValue={patient.name} />
                    <Cell
                      label="Phone number"
                      type="tel"
                      defaultValue={patient.phone}
                    />
                  </div>
                  <div className="cells cells--two">
                    <Cell label="Email address" type="email" optional />
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
                {notifications.map((n) => (
                  <div className="row" key={n.id}>
                    <div>
                      <p className="row__title">{n.text}</p>
                      <p className="meta row__meta">{n.at}</p>
                    </div>
                    <div className="row__side">
                      <Mark status={n.status} />
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
