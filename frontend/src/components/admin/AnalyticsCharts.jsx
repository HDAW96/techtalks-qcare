/* The two chart shapes the analytics page needs, drawn with plain elements -
   a handful of bars does not need a chart library.

   Both use one hue: every bar in --live-bright, the tallest in the darker
   --live so the peak is the first thing read. Every value is also written as
   text (a direct label, a tooltip, or the numbers table), so nothing depends
   on colour or on hovering. */

const plural = (n, one, many) => `${n.toLocaleString()} ${n === 1 ? one : many}`

const pad = (hour) => String(hour).padStart(2, '0')

/* The clinic's day, so a quiet afternoon still shows as empty columns
   rather than vanishing. Widened when visits fall outside it. */
const DAY_START = 9
const DAY_END = 17

/* Visits by the hour they started. `hours` lists only hours that had a
   visit; every hour in between is filled in with zero here. */
export function HourColumns({ hours }) {
  const counts = new Map(hours.map((h) => [h.hour, h.count]))
  const first = Math.min(DAY_START, ...hours.map((h) => h.hour))
  const last = Math.max(DAY_END, ...hours.map((h) => h.hour))
  const columns = []
  for (let hour = first; hour <= last; hour++) {
    columns.push({ hour, count: counts.get(hour) ?? 0 })
  }

  const max = Math.max(0, ...columns.map((c) => c.count))
  if (max === 0) return <p className="analytics-chart__empty">No visits in this range.</p>

  const peaks = columns.filter((c) => c.count === max)
  // Past a dozen columns a label on every hour crowds a phone screen.
  const everyOther = columns.length > 12

  return (
    <figure className="analytics-chart">
      <div className="analytics-hours" role="list" aria-label="Visits by hour">
        {columns.map(({ hour, count }, i) => {
          const isPeak = count === max
          const label = `${pad(hour)}:00, ${plural(count, 'visit', 'visits')}`
          return (
            <div
              key={hour}
              className={`analytics-hours__col${isPeak ? ' is-peak' : ''}`}
              role="listitem"
              tabIndex={0}
              aria-label={label}
            >
              <span className="analytics-hours__tip" aria-hidden="true">
                <strong>{count.toLocaleString()}</strong> at {pad(hour)}:00
              </span>
              <span className="analytics-hours__track" aria-hidden="true">
                {isPeak && <span className="analytics-hours__value">{count}</span>}
                <span className="analytics-hours__bar" style={{ height: `${(count / max) * 100}%` }} />
              </span>
              <span className="analytics-hours__hour" aria-hidden="true">
                {everyOther && i % 2 === 1 ? '' : pad(hour)}
              </span>
            </div>
          )
        })}
      </div>
      <figcaption className="analytics-chart__caption">
        Busiest: {peaks.map((p) => `${pad(p.hour)}:00`).join(', ')} · {plural(max, 'visit', 'visits')}
        {peaks.length > 1 ? ' each' : ''}
      </figcaption>
      <details className="analytics-chart__numbers">
        <summary>Show numbers</summary>
        <table>
          <thead>
            <tr>
              <th scope="col">Hour</th>
              <th scope="col">Visits</th>
            </tr>
          </thead>
          <tbody>
            {columns.map(({ hour, count }) => (
              <tr key={hour}>
                <td>{pad(hour)}:00</td>
                <td>{count.toLocaleString()}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </figure>
  )
}

/* A ranked list with a bar under each name: services, doctors. Every row
   prints its own number, so the list doubles as its own table. `rows` is
   [{ key, label, value }], already in the order to show. */
export function BarList({ rows, unit, emptyText, ariaLabel }) {
  const max = Math.max(0, ...rows.map((r) => r.value))
  if (rows.length === 0) return <p className="analytics-chart__empty">{emptyText}</p>

  return (
    <ol className="analytics-bars" aria-label={ariaLabel}>
      {rows.map(({ key, label, value }) => (
        <li key={key} className={`analytics-bars__row${max > 0 && value === max ? ' is-peak' : ''}`}>
          <span className="analytics-bars__label">{label}</span>
          <span className="analytics-bars__value">
            {value.toLocaleString()}
            <span className="analytics-bars__unit"> {value === 1 ? unit[0] : unit[1]}</span>
          </span>
          <span className="analytics-bars__track" aria-hidden="true">
            <span className="analytics-bars__bar" style={{ width: max > 0 ? `${(value / max) * 100}%` : 0 }} />
          </span>
        </li>
      ))}
    </ol>
  )
}
