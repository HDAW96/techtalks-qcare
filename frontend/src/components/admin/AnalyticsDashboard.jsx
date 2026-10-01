import { useEffect, useState } from 'react'
import Cell from '../Cell'
import { BarList, HourColumns } from './AnalyticsCharts'
import {
  DEFAULT_PRESET,
  PRESETS,
  fetchAnalytics,
  formatRange,
  presetRange,
  rangeProblem,
} from './analyticsApi'
import '../../styles/analytics.css'

/* System Analytics (SCRUM-92): the clinic's numbers for a date range, from
   SCRUM-91's API. One range scopes the whole page, so every tile and chart
   always describes the same days. */

function messageFor(error) {
  if (error.status === 403) return 'Only an admin can see the clinic’s numbers.'
  return error.message || 'Something went wrong. Please try again.'
}

function startingRange() {
  const preset = PRESETS.find((p) => p.id === DEFAULT_PRESET)
  return { preset: preset.id, ...presetRange(preset.days) }
}

function AnalyticsDashboard() {
  const [range, setRange] = useState(startingRange)
  // Asking again for the same range, after a failure.
  const [attempt, setAttempt] = useState(0)
  // The last answer, tagged with the request it answers. Anything not yet
  // answered for the current request is still loading.
  const [result, setResult] = useState({ key: null, data: null, error: null })

  const key = `${range.from}|${range.to}|${attempt}`
  const loading = result.key !== key
  const data = result.data
  const error = loading ? null : result.error

  useEffect(() => {
    let cancelled = false
    fetchAnalytics(range)
      .then((answer) => {
        if (!cancelled) setResult({ key, data: answer, error: null })
      })
      .catch((e) => {
        // Keep the last good numbers; the error decides what is shown.
        if (!cancelled) setResult((last) => ({ key, data: last.data, error: e }))
      })
    return () => {
      cancelled = true
    }
  }, [range, key])

  return (
    <div className="sheet analytics">
      <div className="sheet__head">
        <div>
          <h2>Analytics</h2>
          <p className="meta">{formatRange(range.from, range.to)}</p>
        </div>
      </div>

      <RangePicker range={range} onChange={setRange} />

      {error && (
        <div className="sheet__body" role="alert">
          <p className="form-error analytics__error">{messageFor(error)}</p>
          <button type="button" className="btn btn--quiet" onClick={() => setAttempt((n) => n + 1)}>
            Try again
          </button>
        </div>
      )}
      {!error && !data && (
        <div className="sheet__body">
          <p className="lede">Loading the numbers…</p>
        </div>
      )}

      {!error && data && (
        // While a new range loads, the old numbers stay put, faded, instead
        // of the page jumping to a loading message and back.
        <div className={`analytics__body${loading ? ' is-loading' : ''}`} aria-busy={loading}>
          <Tiles data={data} />

          <div className="analytics__charts">
            <section className="analytics__card analytics__card--wide" aria-labelledby="analytics-hours">
              <h3 id="analytics-hours" className="analytics__card-title">Busiest hours</h3>
              <p className="analytics__card-note">Booked visits by start time, walk-ins by arrival.</p>
              <HourColumns hours={data.busiestHours} />
            </section>

            <section className="analytics__card" aria-labelledby="analytics-services">
              <h3 id="analytics-services" className="analytics__card-title">Busiest services</h3>
              <p className="analytics__card-note">Kept bookings and walk-ins, by treatment.</p>
              <BarList
                ariaLabel="Visits by service"
                rows={data.busiestServices.map((s) => ({ key: s.serviceId, label: s.name, value: s.count }))}
                unit={['visit', 'visits']}
                emptyText="No visits in this range."
              />
            </section>

            <section className="analytics__card" aria-labelledby="analytics-doctors">
              <h3 id="analytics-doctors" className="analytics__card-title">Patients per doctor</h3>
              <p className="analytics__card-note">Different patients each doctor finished a visit with.</p>
              <BarList
                ariaLabel="Patients per doctor"
                rows={data.patientsPerDoctor.map((d) => ({
                  key: d.doctorId,
                  label: d.doctorName ?? 'Unnamed doctor',
                  value: d.patients,
                }))}
                unit={['patient', 'patients']}
                emptyText="No doctors yet."
              />
            </section>
          </div>
        </div>
      )}

      <div className="sheet__foot analytics__foot">
        <p>
          <strong>How these are counted.</strong> A <em>no-show</em> is a booking whose time has passed
          without being completed, so a visit the doctor forgot to mark as done counts as one too.
          A <em>walk-in</em> is a queue visit by a patient with no booking with that doctor that day.
          Cancelled bookings are left out of the charts.
        </p>
      </div>
    </div>
  )
}

/* The presets most people want, and a custom range behind its own button. */
function RangePicker({ range, onChange }) {
  const [custom, setCustom] = useState(null)

  function choosePreset(preset) {
    setCustom(null)
    onChange({ preset: preset.id, ...presetRange(preset.days) })
  }

  function applyCustom(event) {
    event.preventDefault()
    const problem = rangeProblem(custom.from, custom.to)
    if (problem) {
      setCustom({ ...custom, problem })
      return
    }
    setCustom(null)
    onChange({ preset: 'custom', from: custom.from, to: custom.to })
  }

  return (
    <div className="analytics__range">
      <div className="analytics__presets" role="group" aria-label="Date range">
        {PRESETS.map((preset) => (
          <button
            key={preset.id}
            type="button"
            className="analytics__preset"
            aria-pressed={range.preset === preset.id}
            onClick={() => choosePreset(preset)}
          >
            {preset.label}
          </button>
        ))}
        <button
          type="button"
          className="analytics__preset"
          aria-pressed={range.preset === 'custom'}
          aria-expanded={custom !== null}
          onClick={() => setCustom(custom ? null : { from: range.from, to: range.to, problem: null })}
        >
          Custom
        </button>
      </div>

      {custom && (
        <form className="analytics__custom" onSubmit={applyCustom} noValidate>
          <div className="cells analytics__custom-cells">
            <Cell
              label="From"
              type="date"
              value={custom.from}
              onChange={(e) => setCustom({ ...custom, from: e.target.value, problem: null })}
            />
            <Cell
              label="To"
              type="date"
              value={custom.to}
              onChange={(e) => setCustom({ ...custom, to: e.target.value, problem: null })}
            />
          </div>
          <div className="analytics__custom-actions">
            <button type="submit" className="btn btn--live">
              Show
            </button>
            <button type="button" className="btn btn--quiet" onClick={() => setCustom(null)}>
              Cancel
            </button>
          </div>
          {custom.problem && (
            <p className="form-error analytics__custom-error" role="alert">
              {custom.problem}
            </p>
          )}
        </form>
      )}
    </div>
  )
}

/* The headline numbers. Each tile is the figure plus one line that says
   what it counts. */
function Tiles({ data }) {
  const { appointments, walkIns, averageWaitMinutes } = data
  const tiles = [
    {
      key: 'booked',
      label: 'Booked',
      value: appointments.total.toLocaleString(),
      note: `${appointments.cancelled.toLocaleString()} cancelled · ${appointments.upcoming.toLocaleString()} upcoming`,
    },
    {
      key: 'completed',
      label: 'Completed',
      value: appointments.completed.toLocaleString(),
      note: 'Bookings the doctor finished',
    },
    {
      key: 'no-shows',
      label: 'No-shows',
      value: appointments.noShow.toLocaleString(),
      note: 'Past bookings never completed',
    },
    {
      key: 'walk-ins',
      label: 'Walk-ins',
      value: walkIns.toLocaleString(),
      note: 'Queue visits without a booking',
    },
    {
      key: 'wait',
      label: 'Average wait',
      value: averageWaitMinutes === null ? '—' : `${averageWaitMinutes.toLocaleString()} min`,
      note: averageWaitMinutes === null ? 'No queue visits in this range' : 'From joining the queue to the chair',
    },
  ]

  return (
    <dl className="analytics__tiles">
      {tiles.map((tile) => (
        <div key={tile.key} className="analytics__tile">
          <dt className="analytics__tile-label">{tile.label}</dt>
          <dd className="analytics__tile-value">{tile.value}</dd>
          <dd className="analytics__tile-note">{tile.note}</dd>
        </div>
      ))}
    </dl>
  )
}

export default AnalyticsDashboard
