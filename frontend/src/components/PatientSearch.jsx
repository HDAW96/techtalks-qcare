import { useEffect, useState } from 'react'
import Cell from './Cell'
import { apiGet } from '../api/client'

/* Find patient at the desk (SCRUM-107): type a name or a phone number, see the
   matching patients. GET /api/patients?search=... answers in name order, but
   with no search text it answers with every patient, so an empty search must
   never be sent. The server also understands phone numbers typed with spaces
   or a country code, so the text goes as typed. */

const SEARCH_DELAY_MS = 300

function messageFor(error) {
  if (error.status === 403) return 'Only reception staff can search for patients.'
  return error.message || 'Could not search for patients. Please try again.'
}

function PatientSearch({ onClose }) {
  const [text, setText] = useState('')
  // What the server is actually asked for: the text, once typing pauses.
  const [query, setQuery] = useState('')
  const [result, setResult] = useState({ query: null, patients: null, error: null })

  useEffect(() => {
    const timer = setTimeout(() => setQuery(text.trim()), SEARCH_DELAY_MS)
    return () => clearTimeout(timer)
  }, [text])

  useEffect(() => {
    if (!query) return undefined
    let ignore = false
    apiGet(`/patients?${new URLSearchParams({ search: query })}`)
      .then((patients) => {
        if (!ignore) setResult({ query, patients, error: null })
      })
      .catch((error) => {
        if (!ignore) setResult({ query, patients: null, error })
      })
    // A newer search makes this answer stale, so it must not overwrite it.
    return () => {
      ignore = true
    }
  }, [query])

  const typed = text.trim() !== ''
  const current = typed && query !== '' && result.query === query
  const loading = typed && query !== '' && result.query !== query
  const { patients, error } = result

  return (
    <div className="sheet__body" role="search" style={{ borderTop: '1px solid var(--rule)' }}>
      <div className="sheet__head" style={{ padding: 0, border: 0 }}>
        <p className="dash__name">Find patient</p>
        <button type="button" className="btn btn--sm btn--quiet" onClick={onClose}>
          Close
        </button>
      </div>

      <Cell
        label="Name or phone number"
        type="search"
        autoFocus
        autoComplete="off"
        placeholder="Start typing"
        value={text}
        onChange={(e) => setText(e.target.value)}
      />

      {!typed && <p className="meta">Type a name or a phone number to search.</p>}

      {loading && (
        <p className="meta" role="status">
          Searching…
        </p>
      )}

      {current && error && (
        <p className="form-error" role="alert">
          {messageFor(error)}
        </p>
      )}

      {current && !error && patients.length === 0 && (
        <div className="empty" role="status">
          <p>No patient found</p>
        </div>
      )}

      {current && !error && patients.length > 0 && (
        <div>
          {patients.map((p) => (
            <div key={p.patientId} className="doc">
              <div className="doc__row">
                <div className="doc__name">{p.fullName}</div>
                <p className="meta">{p.phoneNumber}</p>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

export default PatientSearch