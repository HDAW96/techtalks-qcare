import { useCallback, useMemo, useState } from 'react'
import {
  appointments as sampleAppointments,
  bookingDays,
  doctors,
  notifications as sampleNotifications,
  queue,
  services,
  takenTimes,
  times,
} from './mock'
import { formatTime } from './format'
import { APPOINTMENT_STATUS, TICKET_STATUS } from '../constants'

/* The one place screens get clinic data from. Today every hook reads the
   sample data in mock.js. Phase 2 replaces the inside of each with an API
   call — screens keep calling the same hooks and only gain loading and error
   states, instead of every screen being rewritten to stop importing mock.js. */

/* The walk-in queue. Pass the signed-in patient's id to mark their own ticket
   as `you`; without one (the public board) no ticket is anyone's. Patient ids
   never leave this hook. */
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
    }
  }, [patientId])
}

/* The signed-in patient's appointments. Held in state so a booking made on
   the dashboard shows up in the lists straight away; Phase 2 refetches after
   posting instead. */
export function useAppointments() {
  const [items, setItems] = useState(sampleAppointments)

  const add = useCallback((appointment) => {
    setItems((list) => [...list, appointment])
  }, [])

  return useMemo(() => {
    const byDate = [...items].sort((a, b) => a.datetime.localeCompare(b.datetime))
    const isUpcoming = (a) => a.status === APPOINTMENT_STATUS.BOOKED
    return {
      all: byDate,
      upcoming: byDate.filter(isUpcoming),
      // Most recent first.
      past: byDate.filter((a) => !isUpcoming(a)).reverse(),
      add,
    }
  }, [items, add])
}

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
  }
}

export function useBookingOptions() {
  return { doctors, services }
}

const toMinutes = (time) => {
  const [h, m] = time.split(':').map(Number)
  return h * 60 + m
}

/* The clinic's working sessions as [start, end) minute ranges, taken from the
   runs of back-to-back 30-minute cells in `times`. */
const SESSIONS = []
for (const time of times) {
  const start = toMinutes(time)
  const last = SESSIONS[SESSIONS.length - 1]
  if (last && last[1] === start) last[1] = start + 30
  else SESSIONS.push([start, start + 30])
}

/* For one doctor and one service length: every bookable day, with each start
   time marked open or not. A time is open only if the whole service fits
   inside one session without overlapping anything already booked — so a
   45-minute cleaning cannot start in the last half hour before lunch. The day
   card's "times free" and the time picker both read this one list, so they
   cannot disagree. */
export function useAvailability(doctorId, minutes, ownAppointments) {
  return useMemo(() => {
    if (!doctorId || !minutes) return []

    return bookingDays.map((date) => {
      const busy = [
        ...(takenTimes[doctorId]?.[date] ?? []).map((t) => [
          toMinutes(t),
          toMinutes(t) + 30,
        ]),
        ...ownAppointments
          .filter(
            (a) =>
              a.doctorId === doctorId &&
              a.status === APPOINTMENT_STATUS.BOOKED &&
              a.datetime.startsWith(date),
          )
          .map((a) => {
            const start = toMinutes(formatTime(a.datetime))
            return [start, start + a.minutes]
          }),
      ]

      const slots = times.map((time) => {
        const start = toMinutes(time)
        const end = start + minutes
        const fits = SESSIONS.some(([open, close]) => start >= open && end <= close)
        const free = busy.every(([from, to]) => end <= from || start >= to)
        return { time, open: fits && free }
      })

      return { date, slots, openCount: slots.filter((s) => s.open).length }
    })
  }, [doctorId, minutes, ownAppointments])
}
