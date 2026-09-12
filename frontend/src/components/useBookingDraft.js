import { useCallback, useMemo, useState } from 'react'

export const BOOKING_STEPS = [
  { id: 'doctor', label: 'Doctor' },
  { id: 'service', label: 'Service' },
  { id: 'when', label: 'Date & time' },
  { id: 'confirm', label: 'Confirm' },
]

const EMPTY = { doctor: null, service: null, day: null, time: null }

/* A booking in progress. Owned by the dashboard rather than by BookingFlow,
   so the sidebar and the flow read the same choices, and the choices survive
   switching to another dashboard section and back. */
export function useBookingDraft() {
  const [draft, setDraft] = useState(EMPTY)
  const [placed, setPlaced] = useState(null)

  const reached = useMemo(
    () => ({
      doctor: true,
      service: Boolean(draft.doctor),
      when: Boolean(draft.doctor && draft.service),
      confirm: Boolean(draft.doctor && draft.service && draft.day && draft.time),
    }),
    [draft],
  )

  /* The URL or the sidebar can ask for a step the current choices do not
     support yet. Such a request is clamped to the furthest supported step,
     and no request at all resumes there. */
  const stepFor = useCallback(
    (requested) => {
      if (requested && reached[requested]) return requested
      return [...BOOKING_STEPS].reverse().find((s) => reached[s.id]).id
    },
    [reached],
  )

  /* Each choice clears the later ones that depended on it: open times belong
     to one doctor, have to fit the service's length, and exist on one day.
     Re-picking the same value keeps everything. */
  const choose = useMemo(
    () => ({
      doctor: (doctor) =>
        setDraft((d) => (d.doctor?.id === doctor.id ? d : { ...EMPTY, doctor })),
      service: (service) =>
        setDraft((d) =>
          d.service?.id === service.id ? d : { ...d, service, day: null, time: null },
        ),
      day: (day) => setDraft((d) => (d.day === day ? d : { ...d, day, time: null })),
      time: (time) => setDraft((d) => ({ ...d, time })),
    }),
    [],
  )

  /* Records a confirmed booking and empties the draft, so the slot that was
     just taken cannot be confirmed a second time. */
  const place = useCallback((appointment) => {
    setPlaced(appointment)
    setDraft(EMPTY)
  }, [])

  return { ...draft, reached, stepFor, choose, placed, place }
}
