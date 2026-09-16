import { useSearchParams } from 'react-router-dom'
import SiteHeader from '../components/SiteHeader'
import SiteFooter from '../components/SiteFooter'
import Legend from '../components/Legend'
import QueueDesk from '../components/QueueDesk'
import QrPlaceholder from '../components/QrPlaceholder'
import { useDeskQueue, useTodaysDoctors } from '../data/hooks'
import { clinic } from '../data/mock'
import '../styles/desk.css'

function ReceptionPage() {
  const { doctors } = useTodaysDoctors()

  /* The open doctor lives in the URL, so a refresh at a busy desk
     comes back to the same queue instead of a blank screen. */
  const [params, setParams] = useSearchParams()
  const doctorId = Number(params.get('doctor')) || null
  const openQueue = (id) => setParams({ doctor: String(id) })

  const queue = useDeskQueue(doctorId)
  const doctor = doctors.find((d) => d.id === doctorId) ?? null

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
                <div key={d.id} className={d.id === doctorId ? 'doc doc--on' : 'doc'}>
                  <div className="doc__row">
                    <div>
                      <div className="doc__name">{d.name}</div>
                      <p className="meta">
                        {d.specialization}{d.hours ? ` · ${d.hours}` : ''}
                      </p>
                    </div>
                    <button
                      type="button"
                      className="btn btn--sm"
                      disabled={!d.scheduledToday}
                      onClick={() => openQueue(d.id)}
                    >
                      Open queue
                    </button>
                  </div>
                  {!d.scheduledToday && (
                    <p className="doc__warn">
                      Not scheduled today — no queue can be opened.
                    </p>
                  )}
                </div>
              ))}
            </div>

            <div className="sheet">
              <div className="sheet__head"><p className="dash__name">Clinic QR</p></div>
              <div className="sheet__body" style={{ textAlign: 'center' }}>
                <QrPlaceholder code="clinic-join" />
                <p className="meta" style={{ marginTop: 12 }}>
                  Patients scan to join today&rsquo;s walk-in queue
                </p>
              </div>
            </div>
          </div>

          <div className="sheet">
            <div className="sheet__head">
              <div>
                <p className="dash__name">
                  {doctor ? `${doctor.name} — queue` : 'Queue'}
                </p>
                <p className="meta">
                  {doctor
                    ? `${queue.waiting} waiting · updated ${queue.updatedAt}`
                    : 'Choose a doctor to open their queue'}
                </p>
              </div>
              {queue.sample && <span className="synthetic">Sample data</span>}
            </div>

            {doctor ? (
              <>
                <Legend items={['live', 'wait', 'miss', 'done']} />
                <QueueDesk entries={queue.entries} onStatus={queue.setStatus} />
                <div className="sheet__foot">
                  <button type="button" className="btn btn--live">Add walk-in</button>
                  <button type="button" className="btn">Check in booked patient</button>
                  <button type="button" className="btn btn--quiet">Find patient</button>
                </div>
              </>
            ) : (
              <div className="empty"><p>No queue open.</p></div>
            )}
          </div>

        </div>
      </main>

      <SiteFooter />
    </div>
  )
}

export default ReceptionPage