import { useState } from 'react'
import Cell from './Cell'
import { isValidPhone } from '../phone'

/* Adding someone who walked in without an appointment.

   Three fields and no more. This is filled in with a person standing at the
   desk and a queue behind them, so anything that is not needed to give them a
   ticket does not belong here. Their record can be completed later; their
   place in the line cannot.

   The phone number is checked here only so the mistake is caught before the
   request is sent. The backend checks it again on arrival, because a check
   that happens only in the browser is not a check at all. */
function WalkInForm({ services, onAdd, onCancel }) {
  const [fullName, setFullName] = useState('')
  const [phone, setPhone] = useState('')
  const [serviceId, setServiceId] = useState('')
  const [touched, setTouched] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState(null)

  const phoneError =
    touched && !isValidPhone(phone)
      ? 'Enter a Lebanese number, for example 03 123456'
      : undefined

  const ready = fullName.trim() !== '' && isValidPhone(phone) && serviceId !== ''

  async function handleSubmit(event) {
    event.preventDefault()
    if (!ready || submitting) return

    setSubmitting(true)
    setFormError(null)
    try {
      await onAdd({
        fullName: fullName.trim(),
        phoneNumber: phone.trim(),
        serviceId: Number(serviceId),
      })
      // The caller closes the form. Clearing the fields here first means a
      // second walk-in starts from an empty form rather than the last one.
      setFullName('')
      setPhone('')
      setServiceId('')
    } catch (error) {
      setFormError(error.message || 'Could not add this patient. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form className="sheet__body walk-in" onSubmit={handleSubmit}>
      <div className="title-band">
        <h2>Add walk-in</h2>
        <p className="meta">They go to the back of this queue</p>
      </div>

      <div className="cells cells--two">
        <Cell
          label="Full name"
          autoComplete="off"
          placeholder="As they give it"
          value={fullName}
          disabled={submitting}
          onChange={(e) => setFullName(e.target.value)}
        />
        <Cell
          label="Phone number"
          type="tel"
          autoComplete="off"
          placeholder="03 123456"
          value={phone}
          disabled={submitting}
          error={phoneError}
          onBlur={() => setTouched(true)}
          onChange={(e) => setPhone(e.target.value)}
        />
      </div>

      <div className="cells walk-in__service">
        <Cell
          label="Treatment"
          placeholder="What they came for"
          value={serviceId}
          disabled={submitting}
          onChange={(e) => setServiceId(e.target.value)}
          /* /api/services sends { id, name, minutes } - the same shape the
             booking screen reads. */
          options={services.map((s) => ({
            value: s.id,
            label: `${s.name} · ${s.minutes} min`,
          }))}
        />
      </div>

      {formError && (
        <p className="form-error" role="alert">
          {formError}
        </p>
      )}

      <div className="desk-foot walk-in__actions">
        <button type="submit" className="btn btn--live" disabled={!ready || submitting}>
          {submitting ? 'Adding…' : 'Add to queue'}
        </button>
        <button type="button" className="btn btn--quiet" onClick={onCancel} disabled={submitting}>
          Cancel
        </button>
      </div>
    </form>
  )
}

export default WalkInForm
