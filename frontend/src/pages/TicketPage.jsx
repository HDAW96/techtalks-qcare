import { useEffect } from 'react'
import { Link } from 'react-router-dom'
import SiteHeader from '../components/SiteHeader'
import SiteFooter from '../components/SiteFooter'
import Legend from '../components/Legend'
import QueueBoard from '../components/QueueBoard'
import TicketCard from '../components/TicketCard'
import { useAuth } from '../auth/useAuth'
import { clinic } from '../data/mock'
import { boardEntries, useMyTicket, usePublicBoard } from '../data/hooks'
import { clearTicketCode, readTicketCode } from '../data/ticketCode'
import { ROLE } from '../constants'

/* A patient following their own place in the queue, refreshed every 15
   seconds. A signed-in patient's ticket is found through their account;
   anyone else's through the code their browser kept when they joined. */
function TicketPage() {
  const { user, loading: sessionLoading } = useAuth()
  const signedInPatient = user?.role === ROLE.PATIENT
  const code = readTicketCode()
  const source = sessionLoading ? null : signedInPatient ? { me: true } : code ? { code } : null

  const { ticket, error, loading, leave, hasLeft } = useMyTicket(source)
  const { queues } = usePublicBoard()
  const myQueue = ticket ? queues.find((q) => q.doctorName === ticket.doctorName) : null

  // A code the server no longer knows (another day, or never valid) would
  // otherwise be asked about forever.
  useEffect(() => {
    if (error?.status === 404 && source?.code) clearTicketCode()
  }, [error, source?.code])

  return (
    <div className="page">
      <SiteHeader />

      <main className="wrap stack" style={{ maxWidth: 760, paddingBlock: 32 }}>
        <div className="sheet">
          <div className="sheet__head">
            <div>
              <p className="dash__name">Your queue ticket</p>
              <p className="meta">{clinic.name} · updates every 15 seconds</p>
            </div>
            {ticket && !hasLeft && <p className="meta meta--right" style={{ color: 'var(--live)' }}>Live</p>}
          </div>

          {(sessionLoading || (source && loading)) && (
            <div className="empty" role="status"><p>Finding your ticket…</p></div>
          )}

          {!sessionLoading && !loading && error && (
            <div className="empty" role="alert">
              <p>{error.message}</p>
            </div>
          )}

          {!sessionLoading && !loading && !error && !ticket && (
            <div className="empty">
              <p>
                You are not in a queue right now. At the clinic, scan the QR code at the front desk to
                take a ticket.
              </p>
            </div>
          )}

          {ticket && <TicketCard key={ticket.ticketId} ticket={ticket} onLeave={leave} hasLeft={hasLeft} />}
        </div>

        {ticket && myQueue && myQueue.entries.length > 0 && (
          <div className="sheet">
            <div className="sheet__head">
              <p className="dash__name">{myQueue.doctorName} — the line</p>
            </div>
            <Legend items={['live', 'wait']} />
            <div className="sheet__body" style={{ paddingBlock: 16 }}>
              <QueueBoard entries={boardEntries(myQueue.entries, ticket.ticket, hasLeft)} />
            </div>
          </div>
        )}

        {signedInPatient && (
          <p className="meta">
            <Link to="/dashboard">Back to my record</Link>
          </p>
        )}
      </main>

      <SiteFooter />
    </div>
  )
}

export default TicketPage
