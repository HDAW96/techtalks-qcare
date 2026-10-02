import { apiGet } from '../../api/client'

/* The admin's numbers (SCRUM-92), served by SCRUM-91:

     GET /api/admin/analytics?from=YYYY-MM-DD&to=YYYY-MM-DD   (both inclusive)
       → { from, to,
           appointments: { total, completed, cancelled, noShow, upcoming },
           walkIns, averageWaitMinutes (null when nobody was called in),
           patientsPerDoctor: [{ doctorId, doctorName, patients }],
           busiestServices:   [{ serviceId, name, count }],
           busiestHours:      [{ hour, count }] }   only hours with a visit

   A bad range answers 400 RANGE_INVALID or RANGE_TOO_LONG. */

export const MAX_DAYS = 366

export const PRESETS = [
  { id: '7', label: 'Last 7 days', days: 7 },
  { id: '30', label: 'Last 30 days', days: 30 },
  { id: '90', label: 'Last 90 days', days: 90 },
]

export const DEFAULT_PRESET = '30'

export function fetchAnalytics({ from, to }) {
  return apiGet(`/admin/analytics?from=${from}&to=${to}`)
}

/* A calendar day as the server reads it. Built from the local date on
   purpose: toISOString() is in UTC, which in Beirut is still yesterday for
   the first two or three hours of every day. */
export function isoDay(date) {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

/* The last `days` days, today included - the same window the server uses
   when it is sent no range at all. */
export function presetRange(days, today = new Date()) {
  const start = new Date(today.getFullYear(), today.getMonth(), today.getDate() - (days - 1))
  return { from: isoDay(start), to: isoDay(today) }
}

/* "2026-09-01" as midnight UTC, so counting days never trips over a
   daylight-saving change. */
function utcDay(iso) {
  const [y, m, d] = iso.split('-').map(Number)
  return Date.UTC(y, m - 1, d)
}

/* The server's two range rules, checked before anything is sent. It checks
   again regardless. Returns the message to show, or null. */
export function rangeProblem(from, to) {
  if (!from || !to) return 'Choose both a start and an end date.'
  if (utcDay(from) > utcDay(to)) return 'The start date must be on or before the end date.'
  const days = (utcDay(to) - utcDay(from)) / 86_400_000 + 1
  if (days > MAX_DAYS) return `Please choose a range of at most ${MAX_DAYS} days.`
  return null
}

const DAY_MONTH = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', timeZone: 'UTC' })
const DAY_MONTH_YEAR = new Intl.DateTimeFormat('en-GB', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  timeZone: 'UTC',
})

/* "1 Sep – 30 Sep 2026", or "28 Dec 2025 – 3 Jan 2026" across a new year. */
export function formatRange(from, to) {
  const sameYear = from.slice(0, 4) === to.slice(0, 4)
  const start = (sameYear ? DAY_MONTH : DAY_MONTH_YEAR).format(utcDay(from))
  const end = DAY_MONTH_YEAR.format(utcDay(to))
  return from === to ? end : `${start} – ${end}`
}
