/* Display formats for the ISO dates the backend sends. Written out by hand
   rather than with toLocaleDateString, whose short month names differ between
   browsers ("Sep" vs "Sept"). Dates are read as local calendar days, never
   through UTC, so a day cannot shift by one near midnight. */

const WEEKDAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

/* "2026-09-17" or "2026-09-17T10:30" → "Thu 17 Sep", or "Thu 17 Sep 2026". */
export function formatDay(value, { year = false } = {}) {
  const [y, m, d] = value.slice(0, 10).split('-').map(Number)
  const weekday = WEEKDAYS[new Date(y, m - 1, d).getDay()]
  return `${weekday} ${String(d).padStart(2, '0')} ${MONTHS[m - 1]}${year ? ` ${y}` : ''}`
}

/* "2026-09-17T10:30" → "10:30". */
export const formatTime = (datetime) => datetime.slice(11, 16)

/* The clinic's record number, derived from PATIENT.patient_id.
   The backend sends the raw id (44); the prefix is display only.
   Lives here so the patient dashboard and the desk share one version. */
export const formatRecordNumber = (id) =>
  id == null ? '' : `P-${String(id).padStart(5, '0')}`

/* The desk search box accepts "P-00044", "00044" or "44". */
export const parseRecordNumber = (input) =>
  Number(String(input).trim().replace(/^P-?/i, '')) || null