/* Values shared with the backend.
   These are NOT settled yet — the team has not agreed the status vocabulary,
   and the backend currently defaults an appointment to "SCHEDULED" while this
   proposal uses "BOOKED". Whatever gets agreed, change it here only: every
   screen maps status through these, so nothing else needs touching. */

export const APPOINTMENT_STATUS = {
  BOOKED: 'BOOKED',
  COMPLETED: 'COMPLETED',
  CANCELLED: 'CANCELLED',
  MISSED: 'MISSED',
}

export const TICKET_STATUS = {
  WAITING: 'WAITING',
  IN_CHAIR: 'IN_CHAIR',
  DONE: 'DONE',
  SKIPPED: 'SKIPPED',
}

export const ROLE = {
  PATIENT: 'PATIENT',
  DOCTOR: 'DOCTOR',
  RECEPTIONIST: 'RECEPTIONIST',
}

/* Which dashboard a signed-in user lands on. Only the patient dashboard
   exists so far; the other two fall back to it until they are built. */
export const HOME_ROUTE_FOR_ROLE = {
  [ROLE.PATIENT]: '/dashboard',
  [ROLE.DOCTOR]: '/dashboard',
  [ROLE.RECEPTIONIST]: '/dashboard',
}

/* Maps a backend status onto the four marks the UI legend defines
   (live / wait / miss / done). Keeping this mapping in one place means a
   change to the agreed strings does not touch any component. */
export const STATUS_MARK = {
  [APPOINTMENT_STATUS.BOOKED]: 'wait',
  [APPOINTMENT_STATUS.COMPLETED]: 'done',
  [APPOINTMENT_STATUS.CANCELLED]: 'miss',
  [APPOINTMENT_STATUS.MISSED]: 'miss',
  [TICKET_STATUS.WAITING]: 'wait',
  [TICKET_STATUS.IN_CHAIR]: 'live',
  [TICKET_STATUS.DONE]: 'done',
  [TICKET_STATUS.SKIPPED]: 'miss',
}
