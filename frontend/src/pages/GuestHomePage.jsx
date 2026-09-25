import { useEffect } from 'react'
import { Link, useLocation } from 'react-router-dom'
import SiteHeader from '../components/SiteHeader'
import SiteFooter from '../components/SiteFooter'
import QueueBoard from '../components/QueueBoard'
import Legend from '../components/Legend'
import { useAuth } from '../auth/useAuth'
import { clinic } from '../data/mock'
import { boardEntries, usePublicBoard } from '../data/hooks'

const walkIn = [
  'Scan the QR code at the clinic desk.',
  'You get a numbered ticket on your own phone.',
  'Watch your position and estimated wait update as the queue moves.',
  'Leave the waiting room if you want — the ticket tells you when to come back.',
]

const booked = [
  'Choose a service and pick a free slot in the clinic day.',
  'Confirm the booking; it appears in your record.',
  'Arrive and check in against the booking — no second queue.',
]

function GuestHomePage() {
  // `key` changes on every navigation, even to the same "/#how" again.
  const { hash, key } = useLocation()
  const { user, loading } = useAuth()
  const untilKnown = loading ? { visibility: 'hidden' } : undefined
  // The public board: ticket numbers only, and no row is marked as anyone's.
  const { queues } = usePublicBoard()

  /* Client-side navigation does not scroll to a URL hash the way a normal
     page load does, so arriving at "/#how" (from this page or any other)
     needs an explicit scroll once the section exists in the DOM.
     Jumps rather than smooth-scrolling: the page's entrance animation is
     still playing on mount, and a smooth scroll started underneath it lands
     short by a varying amount. A jump matches what a plain anchor link does
     on a full page load anyway.
     Re-runs on `key` as well as `hash`, so following the same link twice
     scrolls twice. */
  useEffect(() => {
    if (!hash) return
    const id = hash.slice(1)
    const timer = setTimeout(() => {
      document.getElementById(id)?.scrollIntoView()
    }, 0)
    return () => clearTimeout(timer)
  }, [hash, key])

  return (
    <div className="page">
      <SiteHeader />

      <main>
        <section className="hero">
          <div className="wrap hero__grid">
            <div>
              <h1>Know exactly where you are in the queue.</h1>
              <p className="hero__lede">
                QCare gives dental clinics in North Lebanon online booking and a
                live walk-in queue. Scan once at the desk and follow your place
                in line from your phone, instead of guessing from a waiting room
                chair.
              </p>
              <div className="hero__actions">
                {user ? (
                  <>
                    <Link to="/dashboard" className="btn btn--live">
                      Go to my record
                    </Link>
                    <Link to="/dashboard?section=book" className="btn btn--quiet">
                      Book an appointment
                    </Link>
                  </>
                ) : (
                  <>
                    <Link to="/register" className="btn btn--live" style={untilKnown}>
                      Create a record
                    </Link>
                    <Link to="/login" className="btn btn--quiet" style={untilKnown}>
                      Sign in
                    </Link>
                  </>
                )}
              </div>
            </div>

            <div className="sheet">
              {queues.length === 0 && (
                <>
                  <div className="specimen__head">
                    <div className="now">
                      <span className="meta">Now serving</span>
                      <span className="now__num">—</span>
                    </div>
                    <div className="meta meta--right">
                      <p>{clinic.name}</p>
                      <p>Live · updates every 15 seconds</p>
                    </div>
                  </div>
                  <div className="empty">
                    <p>No walk-in queue is open right now.</p>
                  </div>
                </>
              )}

              {/* One board per open doctor's queue: ticket numbers and waits only. */}
              {queues.map((q, i) => (
                <div key={q.doctorId}>
                  <div className="specimen__head">
                    <div className="now">
                      <span className="meta">Now serving · {q.doctorName}</span>
                      <span className="now__num">{q.nowServing ?? '—'}</span>
                    </div>
                    {i === 0 && (
                      <div className="meta meta--right">
                        <p>{clinic.name}</p>
                        <p>Live · updates every 15 seconds</p>
                      </div>
                    )}
                  </div>
                  <Legend items={['live', 'wait']} />
                  <div className="specimen__body">
                    {q.entries.length > 0 ? (
                      <QueueBoard entries={boardEntries(q.entries, null)} />
                    ) : (
                      <p className="meta">Nobody waiting.</p>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>
        </section>

        <section className="band" id="how">
          <div className="wrap">
            <div className="title-band">
              <h2>Two ways to be seen</h2>
              <p className="meta">
                Walk in and take a live ticket, or book a slot in advance
              </p>
            </div>

            <div className="paths">
              <div className="path">
                <h3>Walk in</h3>
                <ol>
                  {walkIn.map((step, i) => (
                    <li key={step}>
                      <span className="path__step">
                        {String(i + 1).padStart(2, '0')}
                      </span>
                      <span>{step}</span>
                    </li>
                  ))}
                </ol>
              </div>

              <div className="path">
                <h3>Book ahead</h3>
                <ol>
                  {booked.map((step, i) => (
                    <li key={step}>
                      <span className="path__step">
                        {String(i + 1).padStart(2, '0')}
                      </span>
                      <span>{step}</span>
                    </li>
                  ))}
                </ol>
              </div>
            </div>
          </div>
        </section>

        <section className="band band--ground">
          <div className="wrap">
            <div className="title-band">
              <h2>Built for how these clinics actually run</h2>
            </div>
            <div className="paths">
              <div className="stack">
                <p className="lede">
                  Most clinics here run on a paper list and a phone that rings
                  all morning. Walk-ins and booked patients end up in the same
                  crowd, and nobody can say how long the wait is.
                </p>
                <p className="lede">
                  QCare keeps the walk-in queue and the appointment book in one
                  place, so the desk answers the question once and the waiting
                  room can see the answer.
                </p>
              </div>
              <div className="stack">
                <p className="lede">
                  It is bilingual Arabic and English, works on the phone people
                  already carry, and does not require the clinic to change how
                  it takes payment.
                </p>
                <p className="meta">
                  In development — QCare is not yet running in a live clinic
                </p>
              </div>
            </div>
          </div>
        </section>

        <section className="band band--tight">
          <div className="wrap">
            <div className="sheet">
              <div className="cta">
                <div>
                  <h2>
                    {user ? 'Book your next visit' : 'Register once, then book or walk in'}
                  </h2>
                  <p className="lede" style={{ marginTop: 8 }}>
                    A record keeps your appointments and your queue tickets
                    together.
                  </p>
                </div>
                {user ? (
                  <Link to="/dashboard?section=book" className="btn btn--live">
                    Book an appointment
                  </Link>
                ) : (
                  <Link to="/register" className="btn btn--live" style={untilKnown}>
                    Create a record
                  </Link>
                )}
              </div>
            </div>
          </div>
        </section>
      </main>

      <SiteFooter />
    </div>
  )
}

export default GuestHomePage
