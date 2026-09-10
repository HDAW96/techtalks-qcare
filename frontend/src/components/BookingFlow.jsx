import { useEffect, useState } from 'react'
import { services, days, times, takenTimes, clinic } from '../data/mock'

const STEPS = [
  { id: 'service', label: 'Service' },
  { id: 'when', label: 'Date & time' },
  { id: 'confirm', label: 'Confirm' },
]

/* Phase 1: the flow is fully navigable but writes nothing. Phase 2 posts the
   assembled booking to the backend from `confirm`. */
function BookingFlow({ step, onStep }) {
  const [service, setService] = useState(null)
  const [day, setDay] = useState(null)
  const [time, setTime] = useState(null)
  const [placed, setPlaced] = useState(false)

  const reached = {
    service: true,
    when: Boolean(service),
    confirm: Boolean(service && day && time),
  }

  /* The step is held by the parent, but the selections a step needs live here,
     so a caller that cannot see those selections (the dashboard's sidebar
     submenu) can ask for a step that has no data behind it. Every requested
     step is validated here and clamped to the furthest one the current
     selections actually support. */
  const furthest = reached.confirm ? 'confirm' : reached.when ? 'when' : 'service'
  const active = reached[step] ? step : furthest

  useEffect(() => {
    if (active !== step) onStep(active)
  }, [active, step, onStep])

  const go = (id) => {
    if (reached[id]) onStep(id)
  }

  if (placed) {
    return (
      <div className="sheet">
        <div className="sheet__body stack" style={{ textAlign: 'center' }}>
          <h2>Booking recorded</h2>
          <p className="lede" style={{ margin: '0 auto' }}>
            {service.name} on {day.date} at {time}.
          </p>
          <p className="meta">
            Nothing was sent to a server — this is the Phase 1 sketch
          </p>
          <div>
            <button
              type="button"
              className="btn btn--quiet"
              onClick={() => {
                setPlaced(false)
                setService(null)
                setDay(null)
                setTime(null)
                onStep('service')
              }}
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
        {STEPS.map((s) => (
          <button
            key={s.id}
            type="button"
            className="step"
            aria-current={active === s.id}
            disabled={!reached[s.id]}
            onClick={() => go(s.id)}
          >
            {s.label}
          </button>
        ))}
      </div>

      <div className="sheet__body">
        {active === 'service' && (
          <>
            <div className="title-band">
              <h2>Choose a service</h2>
              <p className="meta">{clinic.name}</p>
            </div>
            <div className="picks">
              {services.map((s) => (
                <button
                  key={s.id}
                  type="button"
                  className="pick"
                  aria-pressed={service?.id === s.id}
                  onClick={() => {
                    setService(s)
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

        {active === 'when' && (
          <>
            <div className="title-band">
              <h2>Pick a day and time</h2>
              <p className="meta">{service?.name}</p>
            </div>

            <p className="cell__label">Day — ticks show slots already taken</p>
            <div className="picks" style={{ marginBottom: 32 }}>
              {days.map((d) => {
                const full = d.taken >= d.slots
                return (
                  <button
                    key={d.date}
                    type="button"
                    className="pick"
                    aria-pressed={day?.date === d.date}
                    disabled={full}
                    onClick={() => setDay(d)}
                  >
                    <span className="pick__name">{d.date}</span>
                    <span className="meta">
                      {full ? 'Full' : `${d.slots - d.taken} slots left`}
                    </span>
                    <span className="slots" aria-hidden="true">
                      {Array.from({ length: d.slots }, (_, i) => (
                        <span
                          key={i}
                          className={[
                            'slot',
                            i < d.taken ? 'slot--taken' : '',
                            full ? 'slot--full' : '',
                          ]
                            .filter(Boolean)
                            .join(' ')}
                        />
                      ))}
                    </span>
                  </button>
                )
              })}
            </div>

            {day && (
              <>
                <p className="cell__label">Time on {day.date}</p>
                <div className="times">
                  {times.map((t) => {
                    const taken = takenTimes.includes(t)
                    return (
                      <button
                        key={t}
                        type="button"
                        className="time"
                        aria-pressed={time === t}
                        disabled={taken}
                        onClick={() => {
                          setTime(t)
                          onStep('confirm')
                        }}
                      >
                        {t}
                      </button>
                    )
                  })}
                </div>
              </>
            )}
          </>
        )}

        {active === 'confirm' && (
          <>
            <div className="title-band">
              <h2>Confirm this booking</h2>
            </div>

            <dl className="rows" style={{ margin: 0 }}>
              {[
                ['Service', `${service.name} · ${service.minutes} min`],
                ['Day', day.date],
                ['Time', time],
                ['Clinic', `${clinic.name}, ${clinic.area}`],
              ].map(([k, v]) => (
                <div className="row" key={k} style={{ paddingInline: 0 }}>
                  <dt className="meta">{k}</dt>
                  <dd style={{ margin: 0, fontWeight: 600 }}>{v}</dd>
                </div>
              ))}
            </dl>

            <div className="auth__actions">
              <button
                type="button"
                className="btn btn--live btn--block"
                onClick={() => setPlaced(true)}
              >
                Confirm booking
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
