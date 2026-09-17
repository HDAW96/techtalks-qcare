import { useMemo, useState } from 'react'
import { useAvailability } from '../data/hooks'
import { formatDay, formatTime } from '../data/format'
import SlotPicker from './SlotPicker'

/* Same doctor and treatment, new time. The server rechecks everything, so a
   time taken while this was open comes back as an error, not a double booking. */
function RescheduleAppointment({ appointment, onReschedule, onDone, onClose }) {
  const fetched = useAvailability(appointment.doctorId, appointment.serviceId, appointment.id)
  const currentDay = appointment.datetime.slice(0, 10)
  const currentTime = formatTime(appointment.datetime)

  // The current time is free to the server (it ignores this appointment) but
  // moving to it changes nothing, so it is shown as unavailable here.
  const availability = useMemo(
    () =>
      fetched.map((d) => {
        if (d.date !== currentDay) return d
        const slots = d.slots.map((s) => (s.time === currentTime ? { ...s, open: false } : s))
        return { ...d, slots, openCount: slots.filter((s) => s.open).length }
      }),
    [fetched, currentDay, currentTime],
  )

  const [day, setDay] = useState(null)
  const [time, setTime] = useState(null)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)

  async function confirm() {
    if (saving) return
    setSaving(true)
    setError(null)
    try {
      const moved = await onReschedule(appointment.id, `${day}T${time}`)
      onDone(moved)
    } catch (e) {
      setError(
        e.code === 'SLOT_TAKEN'
          ? 'That time has just been taken. Please choose another.'
          : e.message || 'Could not move the appointment. Please try again.',
      )
      setSaving(false)
    }
  }

  return (
    <div className="sheet">
      <div className="sheet__body">
        <div className="title-band">
          <h2>Move this appointment</h2>
          <p className="meta">
            {appointment.service} with {appointment.doctor} · now{' '}
            {formatDay(appointment.datetime)} at {currentTime}
          </p>
        </div>

        <SlotPicker
          availability={availability}
          minutes={appointment.minutes}
          day={day}
          time={time}
          onDay={(d) => {
            setDay(d)
            setTime(null)
          }}
          onTime={setTime}
        />

        {error && (
          <p className="form-error" role="alert">
            {error}
          </p>
        )}

        <div className="auth__actions">
          <button
            type="button"
            className="btn btn--live btn--block"
            onClick={confirm}
            disabled={!day || !time || saving}
          >
            {saving
              ? 'Moving…'
              : day && time
                ? `Move to ${formatDay(day)} at ${time}`
                : 'Choose a new day and time'}
          </button>
          <button type="button" className="btn btn--quiet btn--block" onClick={onClose}>
            Keep the current time
          </button>
        </div>
      </div>
    </div>
  )
}

export default RescheduleAppointment
