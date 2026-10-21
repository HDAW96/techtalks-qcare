import { clinic } from '../data/mock'
import { useBookingOptions } from '../data/hooks'
import { formatDay, formatTime } from '../data/format'
import { BOOKING_STEPS } from './useBookingDraft'
import SlotPicker from './SlotPicker'

/* What a list of choices shows instead of a blank grid: while it loads, when
   the server could not be reached (with a way to ask again), or when there is
   nothing to pick. Nothing at all once there is something to show. */
function OptionsNotice({ what, loading, error, empty, onRetry }) {
  if (loading) {
    return (
      <div className="empty" role="status">
        <p>Loading {what}…</p>
      </div>
    )
  }
  if (error) {
    return (
      <div className="empty" role="alert">
        <p>{error.message || `Could not load the ${what}.`}</p>
        <button type="button" className="btn btn--quiet" onClick={onRetry}>
          Try again
        </button>
      </div>
    )
  }
  if (empty) {
    return (
      <div className="empty">
        <p>No {what} are available to book right now.</p>
      </div>
    )
  }
  return null
}

/* `onConfirm` posts the booking to the backend, which has the final say - it
   may refuse a slot someone else has just taken, and `error` carries that back
   to this screen.
   Doctor comes first: the ERD scopes both APPOINTMENT and QUEUE per doctor,
   so a booking cannot exist without one, and availability depends on which
   doctor is chosen.
   The choices live in the dashboard (useBookingDraft), which also resolves
   `step` — this component only renders the step it is given. */
function BookingFlow({
  booking,
  step,
  onStep,
  availability,
  availabilityLoading,
  availabilityError,
  onReloadAvailability,
  onConfirm,
  placing,
  error,
}) {
  const {
    doctors,
    services,
    loading: optionsLoading,
    error: optionsError,
    reload: reloadOptions,
  } = useBookingOptions()
  const { doctor, service, day, time, reached, choose, placed } = booking

  if (step === 'done') {
    return (
      <div className="sheet">
        <div className="sheet__body stack" style={{ textAlign: 'center' }}>
          <h2>Booking recorded</h2>
          <p className="lede" style={{ margin: '0 auto' }}>
            {placed.service} with {placed.doctor} on {formatDay(placed.datetime)} at{' '}
            {formatTime(placed.datetime)}.
          </p>
          <p className="meta">
            Saved to your record. It is in My appointments now.
          </p>
          <div>
            <button
              type="button"
              className="btn btn--quiet"
              onClick={() => onStep('doctor')}
            >
              Book another
            </button>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="sheet">
      <div className="steps">
        {BOOKING_STEPS.map((s) => (
          <button
            key={s.id}
            type="button"
            className="step"
            aria-current={step === s.id}
            disabled={!reached[s.id]}
            onClick={() => onStep(s.id)}
          >
            {s.label}
          </button>
        ))}
      </div>

      <div className="sheet__body">
        {step === 'doctor' && (
          <>
            <div className="title-band">
              <h2>Choose a doctor</h2>
              <p className="meta">{clinic.name}</p>
            </div>
            <OptionsNotice
              what="doctors"
              loading={optionsLoading}
              error={optionsError}
              empty={doctors.length === 0}
              onRetry={reloadOptions}
            />
            <div className="picks">
              {doctors.map((d) => (
                <button
                  key={d.id}
                  type="button"
                  className="pick"
                  aria-pressed={doctor?.id === d.id}
                  onClick={() => {
                    choose.doctor(d)
                    onStep('service')
                  }}
                >
                  <span className="pick__name">{d.name}</span>
                  <span className="meta">{d.specialization}</span>
                </button>
              ))}
            </div>
          </>
        )}

        {step === 'service' && (
          <>
            <div className="title-band">
              <h2>Choose a service</h2>
              <p className="meta">{doctor.name}</p>
            </div>
            <OptionsNotice
              what="treatments"
              loading={optionsLoading}
              error={optionsError}
              empty={services.length === 0}
              onRetry={reloadOptions}
            />
            <div className="picks">
              {services.map((s) => (
                <button
                  key={s.id}
                  type="button"
                  className="pick"
                  aria-pressed={service?.id === s.id}
                  onClick={() => {
                    choose.service(s)
                    onStep('when')
                  }}
                >
                  <span className="pick__name">{s.name}</span>
                  <span className="meta">{s.minutes} minutes</span>
                </button>
              ))}
            </div>
          </>
        )}

        {step === 'when' && (
          <>
            <div className="title-band">
              <h2>Pick a day and time</h2>
              <p className="meta">
                {service.name} with {doctor.name}
              </p>
            </div>

            <OptionsNotice
              what="times"
              loading={availabilityLoading}
              error={availabilityError}
              empty={availability.length === 0}
              onRetry={onReloadAvailability}
            />

            {availability.length > 0 && (
              <SlotPicker
                availability={availability}
                minutes={service.minutes}
                day={day}
                time={time}
                onDay={choose.day}
                onTime={(t) => {
                  choose.time(t)
                  onStep('confirm')
                }}
              />
            )}
          </>
        )}

        {step === 'confirm' && (
          <>
            <div className="title-band">
              <h2>Confirm this booking</h2>
            </div>

            <dl className="rows" style={{ margin: 0 }}>
              {[
                ['Doctor', doctor.name],
                ['Service', `${service.name} · ${service.minutes} min`],
                ['Day', formatDay(day)],
                ['Time', time],
                ['Clinic', `${clinic.name}, ${clinic.area}`],
              ].map(([k, v]) => (
                <div className="row" key={k} style={{ paddingInline: 0 }}>
                  <dt className="meta">{k}</dt>
                  <dd style={{ margin: 0, fontWeight: 600 }}>{v}</dd>
                </div>
              ))}
            </dl>

            {error && (
              <p className="form-error" role="alert">
                {error}
              </p>
            )}

            <div className="auth__actions">
              <button
                type="button"
                className="btn btn--live btn--block"
                onClick={onConfirm}
                disabled={placing}
              >
                {placing ? 'Booking…' : 'Confirm booking'}
              </button>
              <button
                type="button"
                className="btn btn--quiet btn--block"
                onClick={() => onStep('when')}
              >
                Change the time
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  )
}

export default BookingFlow
