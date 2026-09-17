import { useCallback, useEffect, useMemo, useState } from 'react'
import { notifications as sampleNotifications, queue } from './mock'
import { apiGet, apiPatch, apiPost } from '../api/client'
import { APPOINTMENT_STATUS, TICKET_STATUS } from '../constants'

/* The one place screens get clinic data from.

   Sign-in, doctors, treatments, free times, appointments and the desk's queue
   now come from the backend. Two things are still sample data from mock.js:
   the patient-facing queue board, which has no endpoint of its own yet (the
   desk endpoints carry names and are receptionist-only), and notifications,
   which have no table in the ERD at all. Both are marked below so nobody
   mistakes sample data for real. */

/* The walk-in queue. SAMPLE DATA — no queue endpoints exist yet.
   Pass the signed-in patient's id to mark their own ticket as `you`; without
   one (the public board) no ticket is anyone's. Patient ids never leave this
   hook. */
export function useQueue(patientId) {
  return useMemo(() => {
    const entries = queue.entries.map(({ patientId: owner, ...entry }) => ({
      ...entry,
      you: patientId != null && owner === patientId,
    }))
    const yours = entries.findIndex((e) => e.you)

    return {
      updatedAt: queue.updatedAt,
      entries,
      nowServing:
        entries.find((e) => e.status === TICKET_STATUS.IN_CHAIR)?.ticket ?? null,
      yourTicket: yours === -1 ? null : entries[yours],
      // Everyone in front of you, the person in the chair included.
      ahead: yours === -1 ? 0 : yours,
      sample: true,
    }
  }, [patientId])
}

/* The signed-in patient's own appointments, from the server.

   `book` posts and then refetches rather than adding the new appointment to
   the list by hand: the server decides the id and may refuse the slot, so
   asking it again is the only way the screen and the database agree. */
export function useAppointments() {
  const [items, setItems] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  /* Bumped after a booking. Changing it re-runs the fetch below, which is how
     a new appointment reaches the list without being added to it by hand. */
  const [reloads, setReloads] = useState(0)

  useEffect(() => {
    let cancelled = false
    apiGet('/appointments/me')
      .then((list) => {
        if (cancelled) return
        setItems(list)
        setError(null)
      })
      .catch((e) => {
        if (!cancelled) setError(e)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [reloads])

  /* Posts and then asks the server again, rather than adding the appointment
     to the list here: the server decides the id and may refuse the slot, so
     refetching is the only way the screen and the database agree. */
  const book = useCallback(async (choice) => {
    const created = await apiPost('/appointments', choice)
    setReloads((n) => n + 1)
    return created
  }, [])

  const reschedule = useCallback(async (id, datetime) => {
    const moved = await apiPatch(`/appointments/${id}`, { datetime })
    setReloads((n) => n + 1)
    return moved
  }, [])

  return useMemo(() => {
    const byDate = [...items].sort((a, b) => a.datetime.localeCompare(b.datetime))
    const isUpcoming = (a) => a.status === APPOINTMENT_STATUS.BOOKED
    return {
      all: byDate,
      upcoming: byDate.filter(isUpcoming),
      // Most recent first.
      past: byDate.filter((a) => !isUpcoming(a)).reverse(),
      loading,
      error,
      book,
      reschedule,
    }
  }, [items, loading, error, book, reschedule])
}

/* SAMPLE DATA — the ERD has no notifications table, so there is nothing to
   read these from. They stay local until the team adds one. */
export function useNotifications() {
  const [items, setItems] = useState(sampleNotifications)

  const markAllRead = useCallback(() => {
    setItems((list) =>
      list.some((n) => !n.read) ? list.map((n) => ({ ...n, read: true })) : list,
    )
  }, [])

  return {
    items,
    unread: items.filter((n) => !n.read).length,
    markAllRead,
    sample: true,
  }
}

/* The doctors and treatments the clinic offers. Public — the booking screen
   shows them before anyone signs in. */
export function useBookingOptions() {
  const [doctors, setDoctors] = useState([])
  const [services, setServices] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    let cancelled = false
    Promise.all([apiGet('/doctors'), apiGet('/services')])
      .then(([doctorList, serviceList]) => {
        if (cancelled) return
        setDoctors(doctorList)
        setServices(serviceList)
      })
      .catch((e) => {
        if (!cancelled) setError(e)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  return { doctors, services, loading, error }
}

/* For one doctor and one treatment: every bookable day, with each start time
   marked open or not.

   The server decides this, not the browser. It knows the doctor's working
   hours and everyone else's bookings — which this screen must never see. The
   day card's "times free" and the time picker both read this one list, so
   they cannot disagree. */
/* `ignoringId` is the appointment being moved, so its own current time does
   not block the times next to it. `days` asks for a longer window than the
   server's default five (it allows up to 30).

   Returns `{ days, error, reload }`. `reload` asks again, for when the server
   has just said a time shown as free is taken. */
export function useAvailability(doctorId, serviceId, ignoringId, days) {
  /* The answer is stored with the request it was fetched for. Comparing that
     against what is selected now means a previous doctor's times are never
     shown while a new request is still in flight. */
  const [result, setResult] = useState({ key: null, days: [], error: null })
  const [reloads, setReloads] = useState(0)
  const key =
    doctorId && serviceId
      ? `${doctorId}:${serviceId}:${ignoringId ?? ''}:${days ?? ''}:${reloads}`
      : null

  useEffect(() => {
    if (!key) return undefined
    let cancelled = false
    const ignoring = ignoringId ? `&ignoring=${ignoringId}` : ''
    const span = days ? `&days=${days}` : ''
    apiGet(`/doctors/${doctorId}/availability?serviceId=${serviceId}${ignoring}${span}`)
      .then((list) => {
        if (!cancelled) setResult({ key, days: list, error: null })
      })
      .catch((error) => {
        // No times are open either way. Booking treats that as the honest
        // answer; rescheduling reads `error` too, because asking about one
        // appointment can fail in ways booking cannot (not yours, gone).
        if (!cancelled) setResult({ key, days: [], error })
      })
    return () => {
      cancelled = true
    }
  }, [key, doctorId, serviceId, ignoringId, days])

  const reload = useCallback(() => setReloads((n) => n + 1), [])

  return result.key === key
    ? { days: result.days, error: result.error, reload }
    : { days: [], error: null, reload }
}


/* ── DESK ─────────────────────────────────────────────
   Real, against /api/queues. Every endpoint below is receptionist-only, so
   these hooks are for the desk screens and nothing else.

   The pattern throughout: an action posts, and the screen is redrawn from what
   the server sends back. Never from what the button assumed would happen. Two
   receptionists work the same queue from two screens, and the one who pressed
   the button is not automatically right about the result. */

// Shared empty lists, so an empty result does not look "changed" every render.
const NO_DOCTORS = []
const NO_TICKETS = []

/* GET /api/queues/today: the clinic QR and one row per doctor, with
   `queue` null until that doctor's queue is opened today. */
export function useTodaysDoctors() {
  const [today, setToday] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  /* Bumped after opening a queue, which re-runs the fetch below. */
  const [reloads, setReloads] = useState(0)

  useEffect(() => {
    let cancelled = false
    apiGet('/queues/today')
      .then((data) => {
        if (cancelled) return
        setToday(data)
        setError(null)
      })
      .catch((e) => {
        if (!cancelled) setError(e)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [reloads])

  /* Not being on today's schedule is a warning, never a block: a doctor who
     came in on their day off is a normal Saturday. The server treats opening
     an already-open queue as opening it, so pressing twice is harmless. */
  const openQueue = useCallback(async (doctorId) => {
    const opened = await apiPost('/queues', { doctorId })
    setReloads((n) => n + 1)
    return opened
  }, [])

  return useMemo(() => ({
    date: today?.date ?? null,
    qrUrl: today?.qrUrl ?? null,
    doctors: today?.doctors ?? NO_DOCTORS,
    openQueue,
    loading,
    error,
  }), [today, openQueue, loading, error])
}

/* GET /api/queues/{queueId}/tickets: one doctor's queue, with names.

   `setStatus` sends the move and replaces the board with the server's answer.
   The server may refuse it — the chair is taken, someone else already called
   that person in — and when it does, the screen must show what is true rather
   than what was pressed. */
export function useDeskQueue(queueId) {
  /* The board, the queue it was fetched for, and any failure, all in one
     value. Comparing that queue against the one selected now means a previous
     doctor's rows are never shown while a new request is still in flight, and
     that an error about one queue cannot linger over another. Same idea as
     `useAvailability` above. */
  const [result, setResult] = useState({ key: null })

  useEffect(() => {
    if (!queueId) return undefined
    let cancelled = false
    apiGet(`/queues/${queueId}/tickets`)
      .then((data) => {
        if (!cancelled) setResult({ key: queueId, data })
      })
      .catch((error) => {
        if (!cancelled) setResult({ key: queueId, error })
      })
    return () => {
      cancelled = true
    }
  }, [queueId])

  const current = result.key === queueId ? result : null

  /* The server returns the whole board, not the one ticket, because calling
     someone in changes the waiting time of everyone behind them. */
  const setStatus = useCallback(async (ticketId, status) => {
    const updated = await apiPatch(`/queues/${queueId}/tickets/${ticketId}`, { status })
    setResult({ key: queueId, data: updated })
  }, [queueId])

  /* Adds a walk-in, then refetches. The post answers with the new ticket
     alone, and the desk needs every row's waiting time recalculated. */
  const addWalkIn = useCallback(async (details) => {
    const created = await apiPost(`/queues/${queueId}/tickets`, details)
    const refreshed = await apiGet(`/queues/${queueId}/tickets`)
    setResult({ key: queueId, data: refreshed })
    return created
  }, [queueId])

  return useMemo(() => {
    const entries = current?.data?.entries ?? NO_TICKETS
    return {
      entries,
      updatedAt: current?.data?.updatedAt ?? null,
      waiting: entries.filter((e) => e.status === TICKET_STATUS.WAITING).length,
      // One doctor, one chair: the server refuses a second IN_CHAIR (409).
      chairTaken: entries.some((e) => e.status === TICKET_STATUS.IN_CHAIR),
      setStatus,
      addWalkIn,
      // Derived, not stored: a queue is loading exactly while it has neither
      // an answer nor a failure of its own yet.
      loading: queueId != null && current == null,
      error: current?.error ?? null,
    }
  }, [current, queueId, setStatus, addWalkIn])
}
