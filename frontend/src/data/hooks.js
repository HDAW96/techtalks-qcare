import { useCallback, useEffect, useMemo, useState } from 'react'
import { notifications as sampleNotifications } from './mock'
import { apiGet, apiPatch, apiPost } from '../api/client'
import { APPOINTMENT_STATUS, TICKET_STATUS } from '../constants'

/* The one place screens get clinic data from.

   Everything comes from the backend except notifications, which have no table
   in the ERD at all, so they are still sample data from mock.js (marked below
   so nobody mistakes them for real). */

/* ── LIVE DATA ───────────────────────────────────────────────────────
   The queue changes while people watch it, so these screens ask again every
   15 seconds (SCRUM-56 guide: polling, not WebSockets). Only while the tab is
   visible: a phone in a pocket should not keep asking. */

const POLL_MS = 15000

/* Calls `onTick` every POLL_MS while the page is visible, and at once when it
   becomes visible again: a patient unlocking their phone should see their
   place now, not up to 15 seconds later. Nothing is asked while hidden.
   `onTick` must be stable (a state setter, or wrapped in useCallback). */
function useEveryFewSeconds(onTick, enabled = true) {
  useEffect(() => {
    if (!enabled) return undefined
    const id = setInterval(() => {
      if (document.visibilityState === 'visible') onTick()
    }, POLL_MS)
    const onVisible = () => {
      if (document.visibilityState === 'visible') onTick()
    }
    document.addEventListener('visibilitychange', onVisible)
    return () => {
      clearInterval(id)
      document.removeEventListener('visibilitychange', onVisible)
    }
  }, [onTick, enabled])
}

/* GETs `path` now and every POLL_MS after, until `path` changes or becomes
   null. The answer is stored with the path it was fetched for, so a previous
   path's data is never shown under a new one. A failed refresh keeps the last
   good answer on screen and reports the error beside it: a moment without
   signal should not blank a patient's ticket. */
function usePolled(path) {
  const [result, setResult] = useState({ key: null, data: null, error: null })
  const [tick, setTick] = useState(0)
  const bump = useCallback(() => setTick((n) => n + 1), [])
  useEveryFewSeconds(bump, Boolean(path))

  useEffect(() => {
    if (!path) return undefined
    let cancelled = false
    apiGet(path)
      .then((data) => {
        if (!cancelled) setResult({ key: path, data, error: null })
      })
      .catch((error) => {
        if (cancelled) return
        setResult((prev) => ({ key: path, data: prev.key === path ? prev.data : null, error }))
      })
    return () => {
      cancelled = true
    }
  }, [path, tick])

  const reload = useCallback(() => setTick((n) => n + 1), [])
  const current = result.key === path
  return {
    data: current ? result.data : null,
    error: current ? result.error : null,
    loading: Boolean(path) && !current,
    reload,
  }
}

const NO_QUEUES = []

/* GET /api/queues/today/board: every queue open today, ticket numbers only.
   Public, so it is safe on the home page and on any patient's phone. */
export function usePublicBoard() {
  const { data, error, loading } = usePolled('/queues/today/board')
  return { queues: data ?? NO_QUEUES, error, loading }
}

/* The patient's own ticket today. `source` is { me: true } for a signed-in
   patient, { code } for someone who joined without an account, or null when
   there is nothing to look up. `ticket` is null when they are in no queue. */
export function useMyTicket(source) {
  const path = source?.me
    ? '/tickets/me'
    : source?.code
      ? `/tickets/by-code/${encodeURIComponent(source.code)}`
      : null
  const { data, error, loading } = usePolled(path)
  return { ticket: data, error, loading }
}

/* GET /api/queues/join-options: the doctors a patient can choose after
   scanning today's QR. Refreshed like the rest, so waiting counts stay true
   while the patient fills in the form. */
export function useJoinOptions(date, sig) {
  const path =
    date && sig
      ? `/queues/join-options?date=${encodeURIComponent(date)}&sig=${encodeURIComponent(sig)}`
      : null
  const { data, error, loading } = usePolled(path)
  return { options: data ?? NO_QUEUES, error, loading }
}

/* POST /api/queues/join. Sends the sign-in token when there is one, so a
   signed-in patient joins as themselves; otherwise the body carries a name
   and phone number. The answer includes the ticket's access code. */
export function joinQueue(details) {
  return apiPost('/queues/join', details)
}

/* A ticket as the patient-facing QueueBoard draws it: the ticket number and
   a wait. `yours` marks the viewer's own ticket; nobody else is named. */
export function boardEntries(entries, yours) {
  return entries.map((e) => ({
    ticket: e.ticket,
    status: e.status,
    eta: e.status === TICKET_STATUS.IN_CHAIR ? 'now' : waitLabel(e.etaMinutes),
    you: yours != null && e.ticket === yours,
  }))
}

export function waitLabel(minutes) {
  if (minutes == null) return ''
  return minutes === 0 ? 'next' : `~${minutes} min`
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

  /* Posts and refetches, like `book` and `reschedule`: the appointment moves
     from upcoming to past, and the list has to be re-read to show that. */
  const cancel = useCallback(async (id) => {
    const cancelled = await apiPost(`/appointments/${id}/cancel`)
    setReloads((n) => n + 1)
    return cancelled
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
      cancel,
    }
  }, [items, loading, error, book, reschedule, cancel])
}

/* Every appointment in the clinic on one day, for the reception desk.
   Real data: GET /api/appointments?date=YYYY-MM-DD, reception only (anyone
   else gets 403, which `error` carries). Each row is a patient's appointment
   plus `patientId`, `patientName` and `phoneNumber`.

   `reschedule` is the same call a patient makes; the server lets reception
   move anyone's appointment. */
export function useDayAppointments(date) {
  /* Stored with the day it was fetched for, so another day's rows never
     show while the new day is loading. Same idea as `useAvailability`. */
  const [result, setResult] = useState({ key: null, items: [], error: null })
  const [reloads, setReloads] = useState(0)
  const key = `${date}:${reloads}`

  useEffect(() => {
    let cancelled = false
    apiGet(`/appointments?date=${date}`)
      .then((items) => {
        if (!cancelled) setResult({ key, items, error: null })
      })
      .catch((error) => {
        if (!cancelled) setResult({ key, items: [], error })
      })
    return () => {
      cancelled = true
    }
  }, [key, date])

  const reload = useCallback(() => setReloads((n) => n + 1), [])

  const reschedule = useCallback(async (id, datetime) => {
    const moved = await apiPatch(`/appointments/${id}`, { datetime })
    setReloads((n) => n + 1)
    return moved
  }, [])

  // A refetch of the same day keeps its rows on screen until the new ones arrive.
  const sameDay = result.key?.startsWith(`${date}:`)
  return {
    items: sameDay ? result.items : [],
    loading: result.key !== key,
    error: sameDay ? result.error : null,
    reload,
    reschedule,
  }
}

/* Finishing a visit: marks the appointment COMPLETED, saves the doctor's
   clinical notes, and frees the chair (SCRUM-80).

   Not a hook, because it is a one-off action rather than something a screen
   subscribes to. The caller reloads the schedule afterwards, so the chair and
   the day list both come back from the server rather than being guessed at
   here - the ticket only leaves the chair because the server said so. */
export function completeVisit(appointmentId, clinicalNotes) {
  return apiPost(`/appointments/${appointmentId}/complete`, {
    clinicalNotes: clinicalNotes?.trim() ? clinicalNotes.trim() : null,
  })
}

/* The signed-in doctor's own day: today's appointments and whoever the desk
   has called into their chair (GET /api/doctors/me/schedule, SCRUM-65).

   Doctor-only. A patient or receptionist reaching this gets 403 from the
   server, which is surfaced as `error` rather than an empty schedule, so the
   screen says something went wrong instead of quietly showing no work.

   Polled like the queue screens: the desk calls the next patient in from
   another room, and the doctor should see who is coming without reloading.
   A failed refresh keeps the last schedule on screen with `error` beside it. */
export function useDoctorSchedule() {
  const { data, error, loading, reload } = usePolled('/doctors/me/schedule')
  return { schedule: data, error, loading, reload }
}

/* One patient's finished treatments, newest first, for the doctor.
   Real data: GET /api/doctors/patients/{id}/history, doctors only (anyone
   else gets 403, which `error` carries). Asked once, not polled: a history
   does not change while the doctor reads it. Pass null to ask nothing, e.g.
   while the panel is closed. */
export function usePatientHistory(patientId) {
  // Stored with the patient it was fetched for, so one patient's visits are
  // never shown under another's name.
  const [result, setResult] = useState({ key: null, data: null, error: null })
  const [reloads, setReloads] = useState(0)
  const key = patientId == null ? null : `${patientId}:${reloads}`

  useEffect(() => {
    if (key == null) return undefined
    let cancelled = false
    apiGet(`/doctors/patients/${patientId}/history`)
      .then((data) => {
        if (!cancelled) setResult({ key, data, error: null })
      })
      .catch((error) => {
        if (!cancelled) setResult({ key, data: null, error })
      })
    return () => {
      cancelled = true
    }
  }, [key, patientId])

  const reload = useCallback(() => setReloads((n) => n + 1), [])
  const current = key != null && result.key === key
  return {
    history: current ? result.data : null,
    error: current ? result.error : null,
    loading: key != null && !current,
    reload,
  }
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
  /* Bumped after opening or closing a queue, and every POLL_MS, which
     re-runs the fetch below. The poll is what shows one receptionist what
     the other just did. */
  const [reloads, setReloads] = useState(0)
  const bump = useCallback(() => setReloads((n) => n + 1), [])
  useEveryFewSeconds(bump)

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

  /* Nobody new can join a closed queue; those already in it can still be
     called in. Opening it again (openQueue) reopens the same queue. */
  const closeQueue = useCallback(async (queueId) => {
    const closed = await apiPost(`/queues/${queueId}/close`)
    setReloads((n) => n + 1)
    return closed
  }, [])

  return useMemo(() => ({
    date: today?.date ?? null,
    qrUrl: today?.qrUrl ?? null,
    doctors: today?.doctors ?? NO_DOCTORS,
    openQueue,
    closeQueue,
    loading,
    error,
  }), [today, openQueue, closeQueue, loading, error])
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
  /* Bumped every POLL_MS so walk-ins added at the other desk, and patients
     joining by QR, appear without anyone pressing anything. */
  const [refresh, setRefresh] = useState(0)
  const bump = useCallback(() => setRefresh((n) => n + 1), [])
  useEveryFewSeconds(bump, Boolean(queueId))

  useEffect(() => {
    if (!queueId) return undefined
    let cancelled = false
    apiGet(`/queues/${queueId}/tickets`)
      .then((data) => {
        if (!cancelled) setResult({ key: queueId, data })
      })
      .catch((error) => {
        // A failed refresh keeps the board on screen; only a queue that has
        // never loaded shows the error instead of rows.
        if (cancelled) return
        setResult((prev) =>
          prev.key === queueId && prev.data ? prev : { key: queueId, error },
        )
      })
    return () => {
      cancelled = true
    }
  }, [queueId, refresh])

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
