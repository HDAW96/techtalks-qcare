import SiteHeader from '../components/SiteHeader'
import SiteFooter from '../components/SiteFooter'
import PatientHistoryButton from '../components/PatientHistoryButton'
import { useDoctorSchedule } from '../data/hooks'
import { useAuth } from '../auth/useAuth'
import '../styles/desk.css'

function DoctorPage() {
  const { user } = useAuth()
  const { schedule, loading, error } = useDoctorSchedule()

  const currentPatient = schedule?.nowServing ?? null
  const appointments = schedule?.appointments ?? []

  return (
    <div className="page">
      <SiteHeader />

      <main className="wrap stack">
        <div className="sheet">
          <div className="sheet__head">
            <div>
              <p className="dash__name">Doctor Console — {user?.fullName || 'Doctor'}</p>
              <p className="meta">
                {loading && 'Loading your schedule…'}
                {error && 'Could not load schedule.'}
                {!loading && !error && `${appointments.length} appointments scheduled today`}
              </p>
            </div>
          </div>

          {!loading && !error && (
            <>
              {/* SCRUM-72: Currently Serving Patient Card with History Pop-up Button */}
              <div className="sheet__section">
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
                    <PatientHistoryButton patientId={null} />
                  </div>
                )}
              </div>

              {/* SCRUM-66 scope: today's appointments list only.
                  NOTE: Call/Finish/Skip controls (QueueDesk) removed —
                  those hit receptionist-only endpoints (/api/queues/**)
                  and return 403 for a doctor session. Per PR #31 review,
                  that's a separate backend-permissions ticket. */}
              <div className="sheet__section">
                <h3 style={{ marginTop: 0, marginBottom: '0.5rem' }}>Today's Appointments</h3>
                {appointments.length === 0 ? (
                  <p className="meta">No appointments scheduled today.</p>
                ) : (
                  <ul className="stack" style={{ listStyle: 'none', padding: 0, margin: 0 }}>
                    {appointments.map((appt) => (
                      // ASSUMPTION: field names (time/patientName) — confirm
                      // against the real DoctorScheduleResponse.java DTO for
                      // GET /api/doctors/me/schedule and adjust if different.
                      <li key={appt.id ?? appt.ticket} className="row">
                        <span>{appt.time}</span>
                        <span>{appt.patientName}</span>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </>
          )}
        </div>
      </main>

      <SiteFooter />
    </div>
  )
}

export default DoctorPage