import { APPOINTMENT_STATUS, TICKET_STATUS } from '../constants'

/* Demonstration data for the Phase 1 sketch screens.
   None of this is real: QCare has no live clinics, patients or bookings yet.
   Every screen that renders it also shows a "sample data" marker.
   Screens do not import this file directly — they go through data/hooks.js,
   which is what Phase 2 rewires to the Spring Boot API. The one exception is
   `clinic`: QCare is single-clinic for now, by design. */

export const clinic = {
  name: 'Dr. Rania Haddad Dental Clinic',
  area: 'Mina, Tripoli',
  phone: '+961 6 000 000',
  hours: 'Mon–Sat, 09:00 – 18:00',
}

/* The live walk-in queue, front first. Patient-facing screens get ticket
   numbers only — never other patients' names. patientId 44 is the sample
   signed-in patient (see auth/AuthProvider.jsx). */
export const queue = {
  updatedAt: '12:41',
  entries: [
    { ticket: 'A-14', patientId: 51, status: TICKET_STATUS.IN_CHAIR, eta: 'now' },
    { ticket: 'A-15', patientId: 38, status: TICKET_STATUS.WAITING, eta: '~8 min' },
    { ticket: 'A-16', patientId: 44, status: TICKET_STATUS.WAITING, eta: '~21 min' },
    { ticket: 'A-17', patientId: 62, status: TICKET_STATUS.WAITING, eta: '~34 min' },
    { ticket: 'A-18', patientId: 17, status: TICKET_STATUS.WAITING, eta: '~45 min' },
  ],
}

/* Appointments and queues are per-doctor in the ERD, so booking has to pick
   one — there is no clinic-wide slot. */
export const doctors = [
  { id: 'haddad', name: 'Dr. Rania Haddad', specialization: 'General dentistry' },
  { id: 'fares', name: 'Dr. Karim Fares', specialization: 'Orthodontics' },
]

export const services = [
  { id: 'consult', name: 'Consultation', minutes: 20 },
  { id: 'clean', name: 'Cleaning and scaling', minutes: 45 },
  { id: 'filling', name: 'Filling', minutes: 40 },
  { id: 'extraction', name: 'Extraction', minutes: 30 },
]

/* The sample patient's own appointments, in the shape the backend returns:
   an ISO date-time and an agreed status string. */
export const appointments = [
  {
    id: 'AP-2291',
    doctorId: 'haddad',
    doctor: 'Dr. Rania Haddad',
    service: 'Cleaning and scaling',
    minutes: 45,
    datetime: '2026-09-17T10:30',
    status: APPOINTMENT_STATUS.BOOKED,
  },
  {
    id: 'AP-2304',
    doctorId: 'haddad',
    doctor: 'Dr. Rania Haddad',
    service: 'Filling — upper left',
    minutes: 40,
    datetime: '2026-10-06T15:00',
    status: APPOINTMENT_STATUS.BOOKED,
  },
  {
    id: 'AP-2187',
    doctorId: 'haddad',
    doctor: 'Dr. Rania Haddad',
    service: 'Consultation',
    minutes: 20,
    datetime: '2026-08-10T11:15',
    status: APPOINTMENT_STATUS.COMPLETED,
  },
  {
    id: 'AP-2140',
    doctorId: 'haddad',
    doctor: 'Dr. Rania Haddad',
    service: 'X-ray review',
    minutes: 20,
    datetime: '2026-07-22T09:45',
    status: APPOINTMENT_STATUS.MISSED,
  },
]

/* Bookable days and the clinic's 30-minute grid. The gap between 11:30 and
   14:00 is the lunch break, so a service cannot run across it. */
export const bookingDays = [
  '2026-09-14',
  '2026-09-15',
  '2026-09-16',
  '2026-09-17',
  '2026-09-18',
]

export const times = [
  '09:00',
  '09:30',
  '10:00',
  '10:30',
  '11:00',
  '11:30',
  '14:00',
  '14:30',
  '15:00',
  '15:30',
]

/* Grid cells other patients have already booked, per doctor per day. The
   sample patient's own bookings are added on top of these (see hooks.js). */
export const takenTimes = {
  haddad: {
    '2026-09-14': times,
    '2026-09-15': ['09:00', '09:30', '10:00', '11:00', '11:30', '14:00', '14:30'],
    '2026-09-16': ['09:30', '14:00'],
    '2026-09-17': ['09:00', '14:30'],
    '2026-09-18': ['11:00'],
  },
  fares: {
    '2026-09-14': ['09:00', '09:30'],
    '2026-09-15': ['09:00', '09:30', '10:00', '10:30', '11:00', '11:30', '14:00', '14:30'],
    '2026-09-16': times,
    '2026-09-17': ['10:00', '10:30'],
    '2026-09-18': [],
  },
}

/* Notifications are UI-only for now (no table in the ERD), so they carry a
   legend mark directly rather than a backend status. */
export const notifications = [
  {
    id: 'N-1',
    mark: 'live',
    read: false,
    text: 'Your turn is approaching — ticket A-16, about 21 minutes.',
    at: 'Today 12:41',
  },
  {
    id: 'N-2',
    mark: 'wait',
    read: false,
    text: 'Appointment confirmed for Thu 17 Sep at 10:30.',
    at: 'Today 09:02',
  },
  {
    id: 'N-3',
    mark: 'miss',
    read: true,
    text: 'You missed your appointment on Wed 22 Jul.',
    at: '22 Jul 2026',
  },
]
