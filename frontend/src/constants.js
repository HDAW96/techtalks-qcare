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

/* Which screen a signed-in user lands on. One sign-in form serves everyone,
   so this is what makes it open the right thing: the role on the account
   decides, not a separate staff login. The doctor screen is not built yet and
   falls back to the patient record until it is. */
export const HOME_ROUTE_FOR_ROLE = {
  [ROLE.PATIENT]: '/dashboard',
  [ROLE.DOCTOR]: '/dashboard',
  [ROLE.RECEPTIONIST]: '/reception',
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
