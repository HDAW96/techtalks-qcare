import { useCallback, useEffect, useMemo, useState } from 'react'
import { notifications as sampleNotifications, queue, deskToday, deskTickets } from './mock'
import { apiGet, apiPatch, apiPost } from '../api/client'
import { APPOINTMENT_STATUS, TICKET_STATUS } from '../constants'

/* The one place screens get clinic data from.

   Sign-in, doctors, treatments, free times and appointments now come from the
   backend. The queue and notifications still read the sample data in mock.js:
   the queue endpoints are not built yet, and notifications have no table in
   the ERD at all, so there is nothing to read them from. Both are marked
   below so nobody mistakes sample data for real. */

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


/* ── DESK ───────────────────────────────────────────────────────────
   SAMPLE DATA. No queue endpoints exist yet. When they do, only the
   bodies below change: the screens read these return values, not mock. */

/* GET /api/queues/today: the clinic QR and one row per doctor, with
   `queue` null until that doctor's queue is opened today. */
export function useTodaysDoctors() {
  const [doctors, setDoctors] = useState(deskToday.doctors)

  /* Local today. Tomorrow this is POST /api/queues { doctorId } and a
     refetch. Not scheduled today is a warning only: it never blocks. */
  const openQueue = useCallback(async (doctorId) => {
    setDoctors((list) =>
      list.map((d) =>
        d.doctorId === doctorId && !d.queue
          ? { ...d, queue: { queueId: 1000 + doctorId, status: 'OPEN', openedBy: 'You', waitingCount: 0 } }
          : d,
      ),
    )
  }, [])

  return useMemo(() => ({
    date: deskToday.date,
    qrUrl: deskToday.qrUrl,
    doctors,
    openQueue,
    loading: false,
    error: null,
    sample: true,
  }), [doctors, openQueue])
}

// One shared empty list, so an empty queue doesn't look "changed" every render.
const NO_TICKETS = []

/* GET /api/queues/{queueId}/tickets: one doctor's queue, with names. */
export function useDeskQueue(queueId) {
  /* Local status changes are stored with the queue they were made in, so
     switching doctors never shows the previous doctor's rows, not even for
     one render. Same idea as `useAvailability` above. */
  const [edits, setEdits] = useState({ key: null, entries: [] })
  const base = (queueId && deskTickets[queueId]?.entries) || NO_TICKETS
  const entries = edits.key === queueId ? edits.entries : base

  /* Local today. Tomorrow this posts and refetches, like `book` above:
     the server has the final say on whether the move is allowed. */
  const setStatus = useCallback(async (ticketId, status) => {
    setEdits((prev) => {
      const list = prev.key === queueId ? prev.entries : base
      return {
        key: queueId,
        entries: list.map((t) => (t.ticketId === ticketId ? { ...t, status } : t)),
      }
    })
  }, [queueId, base])

  return useMemo(() => ({
    entries,
    updatedAt: queueId ? (deskTickets[queueId]?.updatedAt ?? null) : null,
    waiting: entries.filter((e) => e.status === TICKET_STATUS.WAITING).length,
    // One doctor, one chair: the server refuses a second IN_CHAIR (409).
    chairTaken: entries.some((e) => e.status === TICKET_STATUS.IN_CHAIR),
    setStatus,
    loading: false,
    error: null,
    sample: true,
  }), [entries, queueId, setStatus])
}
