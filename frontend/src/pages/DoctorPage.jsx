import { useState } from 'react'
import SiteHeader from '../components/SiteHeader'
import SiteFooter from '../components/SiteFooter'
import Legend from '../components/Legend'
import QueueDesk from '../components/QueueDesk'
import PatientHistoryButton from '../components/PatientHistoryButton'
import { useDeskQueue, useTodaysDoctors } from '../data/hooks'
import { useAuth } from '../auth/useAuth'
import { TICKET_STATUS } from '../constants'
import '../styles/desk.css'

function DoctorPage() {
  const { user } = useAuth()
  const today = useTodaysDoctors()
  const { doctors } = today

  // 1. Searching for the currently signed-in doctor to load their active queue
  const doctor = doctors.find((d) => d.doctorId === user?.doctorId || d.doctorName === user?.fullName) ?? null
  const queue = useDeskQueue(doctor?.queue?.queueId ?? null)

  const [actionError, setActionError] = useState(null)

  // 2. Getting the patient currently sitting in the chair (IN_CHAIR)
  const currentPatient = queue.entries.find((e) => e.status === TICKET_STATUS.IN_CHAIR) ?? null

  // 3. Updating ticket status when doctor clicks action buttons (Call / Finish / Skip)
  const setStatus = async (ticketId, status) => {
    setActionError(null)
    try {
      await queue.setStatus(ticketId, status)
    } catch (error) {
      setActionError(error.message || 'Could not update ticket status.')
    }
  }

  return (
    <div className="page">
      <SiteHeader />

      <main className="wrap stack">
        <div className="sheet">
          <div className="sheet__head">
            <div>
              <p className="dash__name">Doctor Console — {user?.fullName || 'Doctor'}</p>
              <p className="meta">
                {!doctor?.queue && 'No queue open for you today.'}
                {doctor?.queue &&
                  `${queue.waiting} waiting in queue ${queue.updatedAt ? `· updated ${queue.updatedAt}` : ''}`}
              </p>
            </div>
          </div>

          {actionError && (
            <p className="form-error desk-error" role="alert">
              {actionError}
            </p>
          )}

          {doctor?.queue ? (
            queue.error ? (
              <div className="empty">
                <p>{queue.error.message}</p>
              </div>
            ) : queue.loading ? (
              <div className="empty" role="status">
                <p>Loading your queue…</p>
              </div>
            ) : (
              <>
                {/* SCRUM-72: Currently Serving Patient Card with History Pop-up Button */}
                <div style={{ marginBottom: '1.5rem', padding: '1rem', border: '1px solid #e2e8f0', borderRadius: '8px', backgroundColor: '#f8fafc' }}>
                  <h3 style={{ marginTop: 0, marginBottom: '0.5rem' }}>Currently Serving</h3>
                  {currentPatient ? (
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div>
                        <strong>Ticket #{currentPatient.ticket}</strong>
                        <p className="meta" style={{ margin: 0 }}>
                          {currentPatient.patientName || 'Patient'}
                        </p>
                      </div>
                      {/* Patient History Button integrated for SCRUM-72 */}
                      <PatientHistoryButton
                        patientId={currentPatient.patientId ?? null}
                        patientName={currentPatient.patientName ?? ''}
                      />
                    </div>
                  ) : (
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <p className="meta" style={{ margin: 0 }}>No patient currently in the chair.</p>
                      {/* Greys out automatically when patientId is null */}
                      <PatientHistoryButton patientId={null} />
                    </div>
                  )}
                </div>

                <Legend items={['live', 'wait', 'miss', 'done']} />
                <QueueDesk
                  entries={queue.entries}
                  chairTaken={queue.chairTaken}
                  onStatus={setStatus}
                />
              </>
            )
          ) : (
            <div className="empty">
              <p>Your queue is not open yet today. Please ask reception to open your queue.</p>
            </div>
          )}
        </div>
      </main>

      <SiteFooter />
    </div>
  )
}

export default DoctorPage