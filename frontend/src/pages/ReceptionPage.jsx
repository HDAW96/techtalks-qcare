import { useSearchParams } from 'react-router-dom'
import SiteHeader from '../components/SiteHeader'
import SiteFooter from '../components/SiteFooter'
import Legend from '../components/Legend'
import QueueDesk from '../components/QueueDesk'
import QrPlaceholder from '../components/QrPlaceholder'
import DeskAppointments from '../components/DeskAppointments'
import { useDeskQueue, useTodaysDoctors } from '../data/hooks'
import { clinic } from '../data/mock'
import '../styles/desk.css'

function ReceptionPage() {
  const today = useTodaysDoctors()
  const { doctors } = today

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

  /* Opening creates today's queue for that doctor; viewing just selects
     one that is already open. Not being scheduled never blocks either. */
  const openQueue = async (id) => {
    await today.openQueue(id)
    choose(id)
  }

  // One clinic QR per day. Nothing to join until a queue is open.
  const anyQueueOpen = doctors.some((d) => d.queue)

  return (
    <div className="page">
      <SiteHeader />

      <main className="wrap stack">
        <div className="desk-cols">

          <div className="stack">
            <div className="sheet">
              <div className="sheet__head">
                <p className="dash__name">Today&rsquo;s doctors</p>
                <p className="meta meta--right">{clinic.name}</p>
              </div>

              {doctors.map((d) => (
                <div key={d.doctorId} className={d.doctorId === doctorId ? 'doc doc--on' : 'doc'}>
                  <div className="doc__row">
                    <div>
                      <div className="doc__name">{d.doctorName}</div>
                      <p className="meta">
                        {d.queue
                          ? `Queue open · ${d.doctorId === doctorId ? queue.waiting : d.queue.waitingCount} waiting`
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
                    <QrPlaceholder code={today.qrUrl} />
                    <p className="meta">Patients scan to join today&rsquo;s walk-in queue</p>
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
              {queue.sample && <span className="synthetic desk-head__tag">Sample data</span>}
            </div>

            {doctor?.queue && (
              <>
                <Legend items={['live', 'wait', 'miss', 'done']} />
                <QueueDesk entries={queue.entries} chairTaken={queue.chairTaken} onStatus={queue.setStatus} />
                <div className="sheet__foot desk-foot">
                  <button type="button" className="btn btn--live">Add walk-in</button>
                  <button type="button" className="btn">Check in booked patient</button>
                  <button type="button" className="btn btn--quiet">Find patient</button>
                </div>
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

        <DeskAppointments />
      </main>

      <SiteFooter />
    </div>
  )
}

export default ReceptionPage
