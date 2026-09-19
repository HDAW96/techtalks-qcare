import { useState } from 'react'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import SiteHeader from '../components/SiteHeader'
import SiteFooter from '../components/SiteFooter'
import Cell from '../components/Cell'
import { useAuth } from '../auth/useAuth'
import { clinic } from '../data/mock'
import { joinQueue, useBookingOptions, useJoinOptions, waitLabel } from '../data/hooks'
import { readTicketCode, saveTicketCode } from '../data/ticketCode'
import { isValidPhone } from '../phone'
import { ROLE } from '../constants'

/* Where the clinic's QR code leads (/join?date=…&sig=…). The patient picks a
   doctor whose queue is open and a treatment, then takes a ticket.

   With an account, they join as themselves. Without one, a name and phone
   number are enough, the same as at the desk, and the browser keeps the
   ticket's code so the ticket page can find it again. The server checks the
   QR itself: an edited or old code is refused before anything is filled in. */
function JoinPage() {
  const [params] = useSearchParams()
  const date = params.get('date')
  const sig = params.get('sig')
  const location = useLocation()
  const navigate = useNavigate()
  const { user } = useAuth()

  const { options, error: optionsError, loading } = useJoinOptions(date, sig)
  const { services } = useBookingOptions()

  const [doctorId, setDoctorId] = useState(null)
  const [serviceId, setServiceId] = useState('')
  const [fullName, setFullName] = useState('')
  const [phone, setPhone] = useState('')
  const [phoneTouched, setPhoneTouched] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState(null)

  const signedInPatient = user?.role === ROLE.PATIENT
  const signedInStaff = user && !signedInPatient
  const heldCode = readTicketCode()

  // A doctor whose queue closed while the page was open stops counting as chosen.
  const chosen = options.some((o) => o.doctorId === doctorId) ? doctorId : null
  const ready =
    chosen != null &&
    serviceId !== '' &&
    (signedInPatient || (fullName.trim() !== '' && isValidPhone(phone)))

  async function handleSubmit(event) {
    event.preventDefault()
    if (!ready || submitting) return
    setSubmitting(true)
    setFormError(null)
    try {
      const ticket = await joinQueue({
        date,
        sig,
        doctorId: chosen,
        serviceId: Number(serviceId),
        ...(signedInPatient ? {} : { fullName: fullName.trim(), phoneNumber: phone.trim() }),
      })
      // Kept even for a signed-in patient: signing out later should not
      // lose the way back to a ticket they are still holding.
      if (ticket.accessCode) saveTicketCode(ticket.accessCode)
      navigate('/ticket', { replace: true })
    } catch (error) {
      setFormError(error)
      setSubmitting(false)
    }
  }

  return (
    <div className="page">
      <SiteHeader />

      <main className="auth">
        <div className="wrap" style={{ maxWidth: 760 }}>
          <div className="sheet">
            <div className="sheet__head">
              <div>
                <p className="dash__name">{clinic.name}</p>
                <p className="meta">{clinic.area}</p>
              </div>
              <p className="meta meta--right">Walk-in queue</p>
            </div>

            <div className="sheet__body">
              <div className="title-band">
                <h1>Join the queue</h1>
                <p className="meta">Choose your doctor and treatment, then follow your place on this phone</p>
              </div>

              {heldCode && (
                <p className="form-error" style={{ color: 'var(--live)' }}>
                  You already have a ticket today. <Link to="/ticket">See my ticket</Link>
                </p>
              )}

              {(!date || !sig) && (
                <div className="empty">
                  <p>Scan the QR code at the clinic&rsquo;s front desk to join today&rsquo;s queue.</p>
                </div>
              )}

              {date && sig && optionsError && (
                <div className="empty" role="alert">
                  <p>{optionsError.message}</p>
                </div>
              )}

              {date && sig && !optionsError && loading && (
                <div className="empty" role="status">
                  <p>Looking for open queues…</p>
                </div>
              )}

              {date && sig && !optionsError && !loading && options.length === 0 && (
                <div className="empty">
                  <p>No doctor&rsquo;s queue is open right now. Please ask at the front desk.</p>
                </div>
              )}

              {signedInStaff && options.length > 0 && (
                <div className="empty">
                  <p>
                    You are signed in as clinic staff. Staff add walk-ins from the desk. To join as a
                    patient, sign out first.
                  </p>
                </div>
              )}

              {!signedInStaff && options.length > 0 && !optionsError && (
                <form onSubmit={handleSubmit} className="stack">
                  <fieldset style={{ border: 0, padding: 0, margin: 0 }}>
                    <legend className="cell__label" style={{ marginBottom: 12 }}>Doctor</legend>
                    {options.map((o) => (
                      <label key={o.doctorId} className="join-doctor">
                        <input
                          type="radio"
                          name="doctor"
                          value={o.doctorId}
                          checked={chosen === o.doctorId}
                          disabled={submitting}
                          onChange={() => setDoctorId(o.doctorId)}
                        />
                        <span>
                          <span className="join-doctor__name">{o.doctorName}</span>
                          <br />
                          <span className="meta">{o.specialization}</span>
                        </span>
                        <span className="join-doctor__wait">
                          <span>{o.waitingCount} waiting</span>
                          <br />
                          <span className="meta">
                            {o.etaMinutes === 0 ? 'No wait now' : `${waitLabel(o.etaMinutes)} wait`}
                          </span>
                        </span>
                      </label>
                    ))}
                  </fieldset>

                  <div className="cells">
                    <Cell
                      label="Treatment"
                      value={serviceId}
                      disabled={submitting}
                      placeholder="What you came for"
                      onChange={(e) => setServiceId(e.target.value)}
                      options={services.map((s) => ({ value: s.id, label: `${s.name} · ${s.minutes} min` }))}
                    />
                  </div>

                  {signedInPatient ? (
                    <p className="meta">Joining as {user.fullName}.</p>
                  ) : (
                    <>
                      <div className="cells cells--two">
                        <Cell
                          label="Full name"
                          autoComplete="name"
                          value={fullName}
                          disabled={submitting}
                          onChange={(e) => setFullName(e.target.value)}
                        />
                        <Cell
                          label="Phone number"
                          type="tel"
                          autoComplete="tel"
                          placeholder="03 123456"
                          value={phone}
                          disabled={submitting}
                          error={
                            phoneTouched && !isValidPhone(phone)
                              ? 'Enter a Lebanese number, for example 03 123456'
                              : undefined
                          }
                          onBlur={() => setPhoneTouched(true)}
                          onChange={(e) => setPhone(e.target.value)}
                        />
                      </div>
                      <p className="meta">
                        Have a QCare record?{' '}
                        <Link to="/login" state={{ from: location }}>
                          Sign in first
                        </Link>{' '}
                        and your ticket goes on it.
                      </p>
                    </>
                  )}

                  {formError && (
                    <p className="form-error" role="alert">
                      {formError.message}{' '}
                      {formError.code === 'ALREADY_IN_QUEUE' && <Link to="/ticket">See my ticket</Link>}
                    </p>
                  )}

                  <div className="auth__actions">
                    <button
                      type="submit"
                      className="btn btn--live btn--block"
                      disabled={!ready || submitting}
                    >
                      {submitting ? 'Joining…' : 'Take a ticket'}
                    </button>
                  </div>
                </form>
              )}
            </div>
          </div>
        </div>
      </main>

      <SiteFooter />
    </div>
  )
}

export default JoinPage
