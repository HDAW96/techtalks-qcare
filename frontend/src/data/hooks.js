import { useCallback, useEffect, useMemo, useState } from 'react'
import { notifications as sampleNotifications, queue, deskDoctors, deskQueues  } from './mock'
import { apiGet, apiPost } from '../api/client'
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
    }
  }, [items, loading, error, book])
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
export function useAvailability(doctorId, serviceId) {
  /* The answer is stored with the doctor and treatment it was fetched for.
     Comparing that against what is selected now means a previous doctor's
     times are never shown while a new request is still in flight. */
  const [result, setResult] = useState({ key: null, days: [] })
  const key = doctorId && serviceId ? `${doctorId}:${serviceId}` : null

  useEffect(() => {
    if (!key) return undefined
    let cancelled = false
    apiGet(`/doctors/${doctorId}/availability?serviceId=${serviceId}`)
      .then((days) => {
        if (!cancelled) setResult({ key, days })
      })
      .catch(() => {
        // An unreachable server leaves no times open, which is the honest
        // thing to show: nothing here can be booked right now.
        if (!cancelled) setResult({ key, days: [] })
      })
    return () => {
      cancelled = true
    }
  }, [key, doctorId, serviceId])

  return result.key === key ? result.days : []
}


/* ── DESK ───────────────────────────────────────────────────────────
   SAMPLE DATA. No queue endpoints exist yet. When they do, only the
   bodies below change: the screens read these return values, not mock. */

export function useTodaysDoctors() {
  return useMemo(
    () => ({ doctors: deskDoctors, loading: false, error: null, sample: true }),
    [],
  )
}

export function useDeskQueue(doctorId) {
  const [entries, setEntries] = useState([])

  /* Stored with the doctor it was fetched for, so a previous doctor's
     queue is never shown while a new request is in flight. */
  useEffect(() => {
    setEntries(doctorId ? (deskQueues[doctorId]?.entries ?? []) : [])
  }, [doctorId])

  /* Local today. Tomorrow this posts and refetches, like `book` above:
     the server has the final say on whether the move is allowed. */
  const setStatus = useCallback(async (ticketId, status) => {
    setEntries((list) =>
      list.map((t) => (t.ticketId === ticketId ? { ...t, status } : t)),
    )
  }, [])

  const addWalkIn = useCallback(async (draft) => {
    setEntries((list) => {
      const n = list.length + 13
      return [...list, {
        ticketId: Date.now(),
        label: `R-${n}`,
        patientId: 900 + list.length,
        patientName: draft.fullName,
        service: draft.service,
        source: 'WALK_IN',
        status: TICKET_STATUS.WAITING,
        eta: 45,
        publicCode: 'sample00',
      }]
    })
  }, [])

  return useMemo(() => ({
    entries,
    updatedAt: doctorId ? deskQueues[doctorId]?.updatedAt : null,
    waiting: entries.filter((e) => e.status === TICKET_STATUS.WAITING).length,
    setStatus,
    addWalkIn,
    loading: false,
    error: null,
    sample: true,
  }), [entries, doctorId, setStatus, addWalkIn])
}