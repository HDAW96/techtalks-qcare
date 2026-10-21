import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import SiteHeader from '../components/SiteHeader'
import SiteFooter from '../components/SiteFooter'
import Legend from '../components/Legend'
import QueueDesk from '../components/QueueDesk'
import QrCode from '../components/QrCode'
import WalkInForm from '../components/WalkInForm'
import PatientSearch from '../components/PatientSearch'
import DeskAppointments from '../components/DeskAppointments'
import { useBookingOptions, useDeskQueue, useTodaysDoctors } from '../data/hooks'
import { clinic } from '../data/mock'
import '../styles/desk.css'

function ReceptionPage() {
  const today = useTodaysDoctors()
  const { doctors } = today
  // The treatment list for the walk-in form. Public, and already fetched for
  // the booking screen, so it costs nothing extra here.
  const { services } = useBookingOptions()

  /* The chosen doctor lives in the URL, so a refresh at a busy desk
     comes back to the same queue instead of a blank screen. */
  const [params, setParams] = useSearchParams()
  const doctorId = Number(params.get('doctor')) || null
  // Keeps the appointments section's own keys (day, move) as they are.
  const choose = (id) =>
    setParams((p) => {
      const next = new URLSearchParams(p)
      next.set('doctor', String(id))
      return next
    })

  const doctor = doctors.find((d) => d.doctorId === doctorId) ?? null
  const queue = useDeskQueue(doctor?.queue?.queueId ?? null)

  const [addingWalkIn, setAddingWalkIn] = useState(false)
  const [searching, setSearching] = useState(false)
  /* What the server refused, shown above the board. A rejected move is the
     normal way two receptionists on two screens find out about each other -
     the chair was taken while this screen still showed it free - so it is a
     line of text, not an alarm. */
  const [actionError, setActionError] = useState(null)

  /* Opening creates today's queue for that doctor; viewing just selects
     one that is already open. Not being scheduled never blocks either. */
  const openQueue = async (id) => {
    setActionError(null)
    try {
      await today.openQueue(id)
      choose(id)
    } catch (error) {
      setActionError(error.message || 'Could not open that queue.')
    }
  }

  /* The board is replaced with whatever the server sends back, so a refused
     move leaves the screen showing what is true rather than what was pressed. */
  const setStatus = async (ticketId, status) => {
    setActionError(null)
    try {
      await queue.setStatus(ticketId, status)
    } catch (error) {
      setActionError(error.message || 'Could not move that ticket.')
    }
  }

  const addWalkIn = async (details) => {
    await queue.addWalkIn(details)
    setAddingWalkIn(false)
  }

  // One clinic QR per day. Nothing to join until a queue is open.
  const anyQueueOpen = doctors.some((d) => d.queue?.status === 'OPEN')
  const selectedOpen = doctor?.queue?.status === 'OPEN'
  const openQueues = doctors.filter((d) => d.queue?.status === 'OPEN').map((d) => d.doctorId).join(',')

  /* Closing stops new joins, by QR and at the desk; people already in the
     line can still be called in and finished. */
  const closeQueue = async (queueId) => {
    setActionError(null)
    try {
      await today.closeQueue(queueId)
      setAddingWalkIn(false)
    } catch (error) {
      setActionError(error.message || 'Could not close that queue.')
    }
  }

  return (
    <div className="page">
      <title>{`${clinic.name}'s Reception - QCare`}</title>
      <SiteHeader />

      <main className="wrap stack">
        <div className="desk-cols">

          <div className="stack">
            <div className="sheet">
              <div className="sheet__head">
                <p className="dash__name">Today&rsquo;s doctors</p>
                <p className="meta meta--right">{clinic.name}</p>
              </div>

              {today.loading && (
                <div className="empty" role="status">
                  <p>Loading today&rsquo;s queues…</p>
                </div>
              )}

              {/* A desk with no connection must say so plainly rather than
                  show an empty list, which reads as "no doctors today". */}
              {today.error && !today.loading && (
                <div className="empty">
                  <p>{today.error.message}</p>
                  <p className="meta">The queue cannot be worked until this screen reconnects.</p>
                </div>
              )}

              {!today.loading && !today.error && doctors.length === 0 && (
                <div className="empty">
                  <p>No doctors are listed for today.</p>
                </div>
              )}

              {doctors.map((d) => (
                <div key={d.doctorId} className={d.doctorId === doctorId ? 'doc doc--on' : 'doc'}>
                  <div className="doc__row">
                    <div>
                      <div className="doc__name">{d.doctorName}</div>
                      <p className="meta">
                        {d.queue
                          ? `${d.queue.status === 'OPEN' ? 'Queue open' : 'Queue closed'} · ${d.doctorId === doctorId ? queue.waiting : d.queue.waitingCount} waiting`
                          : 'No queue yet today'}
                      </p>
                    </div>
                    {d.queue ? (
                      <button type="button" className="btn btn--sm" onClick={() => choose(d.doctorId)}>
                        View queue
                      </button>
                    ) : (
                      <button type="button" className="btn btn--live btn--sm" onClick={() => openQueue(d.doctorId)}>
                        Open queue
                      </button>
                    )}
                  </div>
                  {!d.scheduledToday && (
                    <p className="doc__warn">Not on the schedule today. You can still open a queue.</p>
                  )}
                </div>
              ))}
            </div>

            <div className="sheet">
              <div className="sheet__head"><p className="dash__name">Clinic QR</p></div>
              <div className="sheet__body desk-qr">
                {anyQueueOpen ? (
                  <>
                    <QrCode value={today.qrUrl} label="Today's clinic QR code for joining the walk-in queue" />
                    <p className="meta">Patients scan to join today&rsquo;s walk-in queue</p>
                    {/* The same link as text, for a laptop with no phone to hand. */}
                    <p className="meta" style={{ marginTop: 8 }}>
                      <a href={today.qrUrl} target="_blank" rel="noreferrer">Open the join page</a>
                    </p>
                  </>
                ) : (
                  <p className="meta">Open a doctor&rsquo;s queue to show today&rsquo;s QR</p>
                )}
              </div>
            </div>
          </div>

          <div className="sheet desk-cols__queue">
            <div className="sheet__head">
              <div>
                <p className="dash__name">
                  {doctor ? `${doctor.doctorName} — queue` : 'Queue'}
                </p>
                <p className="meta">
                  {!doctor && 'Choose a doctor to see their queue'}
                  {doctor && !doctor.queue && 'Not opened today'}
                  {doctor?.queue &&
                    `${queue.waiting} waiting${queue.updatedAt ? ` · updated ${queue.updatedAt}` : ''}`}
                </p>
              </div>
            </div>

            {actionError && (
              <p className="form-error desk-error" role="alert">
                {actionError}
              </p>
            )}

            {doctor?.queue && (
              <>
                {queue.error ? (
                  <div className="empty">
                    <p>{queue.error.message}</p>
                  </div>
                ) : queue.loading ? (
                  <div className="empty" role="status">
                    <p>Loading the queue…</p>
                  </div>
                ) : (
                  <>
                    <Legend items={['live', 'wait', 'miss', 'done']} />
                    <QueueDesk
                      entries={queue.entries}
                      chairTaken={queue.chairTaken}
                      onStatus={setStatus}
                    />
                  </>
                )}

                {!selectedOpen ? (
                  <div className="sheet__foot desk-foot">
                    <p className="meta" style={{ flexBasis: '100%' }}>
                      Closed: nobody new can join. Those already in the line can still be called in.
                    </p>
                    <button type="button" className="btn btn--live" onClick={() => openQueue(doctor.doctorId)}>
                      Reopen queue
                    </button>
                  </div>
                ) : addingWalkIn ? (
                  <WalkInForm
                    services={services}
                    onAdd={addWalkIn}
                    onCancel={() => setAddingWalkIn(false)}
                  />
                ) : (
                  <div className="sheet__foot desk-foot">
                    <button
                      type="button"
                      className="btn btn--live"
                      onClick={() => {
                        setActionError(null)
                        setAddingWalkIn(true)
                      }}
                    >
                      Add walk-in
                    </button>
                    <button
                      type="button"
                      className="btn btn--quiet"
                      aria-expanded={searching}
                      onClick={() => setSearching((open) => !open)}
                    >
                      Find patient
                    </button>
                    <button
                      type="button"
                      className="btn btn--quiet"
                      onClick={() => closeQueue(doctor.queue.queueId)}
                    >
                      Close queue
                    </button>
                  </div>
                )}
                {searching && selectedOpen && !addingWalkIn && (
                  <PatientSearch onClose={() => setSearching(false)} />
                )}
              </>
            )}
            {doctor && !doctor.queue && (
              <div className="empty">
                <p>{doctor.doctorName} has no queue open today.</p>
                <button type="button" className="btn btn--live" onClick={() => openQueue(doctor.doctorId)}>
                  Open queue
                </button>
              </div>
            )}
            {!doctor && <div className="empty"><p>No queue selected.</p></div>}
          </div>

        </div>

        <DeskAppointments openQueues={openQueues} onCheckedIn={queue.reload} />
      </main>

      <SiteFooter />
    </div>
  )
}

export default ReceptionPage
