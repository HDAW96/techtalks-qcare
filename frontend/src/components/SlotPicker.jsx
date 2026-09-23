import { formatDay } from '../data/format'

/* Day strip plus the times on the chosen day. Shared by booking and
   rescheduling so both always show free times the same way. */
function SlotPicker({ availability, minutes, day, time, onDay, onTime }) {
  const openTimes = availability.find((d) => d.date === day)?.slots ?? []

  return (
    <>
      <p className="cell__label">
        Day — ticks show times this {minutes}-minute visit cannot start
      </p>
      <div className="picks" style={{ marginBottom: 32 }}>
        {availability.map((d) => {
          /* Two different reasons a day can't be booked, and patients read
             them very differently: the doctor holds no clinic that day, or
             the day is real but every time is taken. */
          const noClinic = d.slots.length === 0
          const full = d.openCount === 0
          return (
            <button
              key={d.date}
              type="button"
              className="pick"
              aria-pressed={day === d.date}
              disabled={full}
              onClick={() => onDay(d.date)}
            >
              <span className="pick__name">{formatDay(d.date)}</span>
              <span className="meta">
                {noClinic
                  ? 'Not working'
                  : full
                    ? 'Fully booked'
                    : `${d.openCount} ${d.openCount === 1 ? 'time' : 'times'} free`}
              </span>
              <span className="slots" aria-hidden="true">
                {d.slots.map((s) => (
                  <span
                    key={s.time}
                    className={[
                      'slot',
                      s.open ? '' : 'slot--taken',
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
          <p className="cell__label">Time on {formatDay(day)}</p>
          <div className="times">
            {openTimes.map((s) => (
              <button
                key={s.time}
                type="button"
                className="time"
                aria-pressed={time === s.time}
                disabled={!s.open}
                onClick={() => onTime(s.time)}
              >
                {s.time}
              </button>
            ))}
          </div>
        </>
      )}
    </>
  )
}

export default SlotPicker
