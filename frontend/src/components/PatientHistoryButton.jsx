import { useEffect, useRef, useState } from 'react'
import { usePatientHistory } from '../data/hooks'
import { formatDay, formatRecordNumber, formatTime } from '../data/format'
import '../styles/history.css'

/* "View history" and the panel it opens: a patient's finished treatments,
   newest first, over the doctor's dashboard so they never lose their place.

   Drop it on any card that knows who the patient is:
     <PatientHistoryButton patientId={ticket.patientId} patientName={ticket.patientName} />

   A native <dialog> opened with showModal(), so the browser does the hard
   parts: Escape closes it, focus stays inside, and the page behind cannot be
   clicked or read by a screen reader while it is open. */
function PatientHistoryButton({ patientId, patientName, className = 'btn btn--quiet' }) {
  const [open, setOpen] = useState(false)
  const dialogRef = useRef(null)

  useEffect(() => {
    const dialog = dialogRef.current
    if (!dialog) return
    if (open && !dialog.open) dialog.showModal()
    if (!open && dialog.open) dialog.close()
  }, [open])

  return (
    <>
      <button
        type="button"
        className={className}
        onClick={() => setOpen(true)}
        disabled={patientId == null}
      >
        View history
      </button>
      <dialog
        ref={dialogRef}
        className="history"
        aria-labelledby="history-title"
        // Escape and the close button both end here, so state never
        // disagrees with what is on screen.
        onClose={() => setOpen(false)}
        // A click on the dimmed backdrop lands on the dialog element itself.
        onClick={(e) => {
          if (e.target === e.currentTarget) setOpen(false)
        }}
      >
        {open && (
          <HistoryPanel
            patientId={patientId}
            patientName={patientName}
            onClose={() => setOpen(false)}
          />
        )}
      </dialog>
    </>
  )
}

/* Rendered only while open, so the history is fetched when the doctor asks
   for it, and fresh each time. */
function HistoryPanel({ patientId, patientName, onClose }) {
  const { history, error, loading, reload } = usePatientHistory(patientId)
  const name = history?.patientName ?? patientName

  return (
    <div className="history__sheet">
      <header className="history__head">
        <div>
          <p className="meta">Treatment history · {formatRecordNumber(patientId)}</p>
          <h2 id="history-title">{name || 'Patient'}</h2>
        </div>
        <button type="button" className="btn btn--quiet history__close" onClick={onClose}>
          Close
        </button>
      </header>

      <div className="history__body">
        {loading && <p className="history__state">Loading history…</p>}

        {error && (
          <div className="form-error history__error" role="alert">
            <p>{errorMessage(error)}</p>
            {![401, 403, 404].includes(error.status) && (
              <button type="button" className="btn btn--quiet" onClick={reload}>
                Try again
              </button>
            )}
          </div>
        )}

        {history && history.visits.length === 0 && (
          <p className="history__state">
            No finished treatments on record yet. Visits appear here once they are marked
            complete.
          </p>
        )}

        {history && history.visits.length > 0 && (
          <ol className="history__list">
            {history.visits.map((visit) => (
              <li key={visit.appointmentId} className="history__visit">
                <p className="history__when">
                  <time dateTime={visit.datetime}>{formatDay(visit.datetime, { year: true })}</time>
                  <span>{formatTime(visit.datetime)}</span>
                </p>
                <div className="history__what">
                  <p className="history__service">{visit.service}</p>
                  <p className="meta">{visit.doctor}</p>
                  {visit.notes && <p className="history__notes">{visit.notes}</p>}
                </div>
              </li>
            ))}
          </ol>
        )}
      </div>
    </div>
  )
}

function errorMessage(error) {
  if (error.status === 401) return 'Please sign in again to see this history.'
  if (error.status === 403) return 'Only doctors can see a patient’s treatment history.'
  if (error.status === 404) return 'This patient was not found.'
  return error.message || 'Could not load the history.'
}

export default PatientHistoryButton
