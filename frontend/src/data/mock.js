/* Demonstration data for the Phase 1 sketch screens.
   None of this is real: QCare has no live clinics, patients or bookings yet.
   Every screen that renders it also shows a "sample data" marker. Phase 2
   replaces this module with calls to the Spring Boot API. */

export const clinic = {
  name: 'Dr. Rania Haddad Dental Clinic',
  area: 'Mina, Tripoli',
  phone: '+961 6 000 000',
  hours: 'Mon–Sat, 09:00 – 18:00',
}

export const patient = {
  name: 'Hassan El Hussein',
  phone: '+961 70 000 000',
  recordNo: 'P-04812',
}

/* The live walk-in queue. Position 1 is in the chair. */
export const queue = {
  nowServing: 'A-14',
  updatedAt: '12:41',
  entries: [
    { ticket: 'A-14', name: 'In the chair', status: 'live', eta: 'now' },
    { ticket: 'A-15', name: 'M. Dakdouk', status: 'wait', eta: '~8 min' },
    { ticket: 'A-16', name: 'You', status: 'wait', eta: '~21 min', you: true },
    { ticket: 'A-17', name: 'J. Said', status: 'wait', eta: '~34 min' },
    { ticket: 'A-18', name: 'R. Karam', status: 'wait', eta: '~45 min' },
  ],
}

export const appointments = {
  upcoming: [
    {
      id: 'AP-2291',
      service: 'Cleaning and scaling',
      doctor: 'Dr. Rania Haddad',
      date: 'Thu 18 Sep 2026',
      time: '10:30',
      status: 'wait',
    },
    {
      id: 'AP-2304',
      service: 'Filling — upper left',
      doctor: 'Dr. Rania Haddad',
      date: 'Tue 06 Oct 2026',
      time: '15:00',
      status: 'wait',
    },
  ],
  past: [
    {
      id: 'AP-2187',
      service: 'Consultation',
      doctor: 'Dr. Rania Haddad',
      date: 'Mon 11 Aug 2026',
      time: '11:15',
      status: 'done',
    },
    {
      id: 'AP-2140',
      service: 'X-ray review',
      doctor: 'Dr. Rania Haddad',
      date: 'Wed 23 Jul 2026',
      time: '09:45',
      status: 'miss',
    },
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

/* A clinic day has a fixed capacity, and you can see how full it is. */
export const days = [
  { date: 'Mon 15 Sep', slots: 12, taken: 12 },
  { date: 'Tue 16 Sep', slots: 12, taken: 9 },
  { date: 'Wed 17 Sep', slots: 12, taken: 4 },
  { date: 'Thu 18 Sep', slots: 12, taken: 7 },
  { date: 'Fri 19 Sep', slots: 12, taken: 2 },
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

export const takenTimes = ['09:30', '11:00', '14:30']

export const notifications = [
  {
    id: 'N-1',
    status: 'live',
    text: 'Your turn is approaching — ticket A-16, about 21 minutes.',
    at: 'Today 12:41',
  },
  {
    id: 'N-2',
    status: 'wait',
    text: 'Appointment confirmed for Thu 18 Sep at 10:30.',
    at: 'Today 09:02',
  },
  {
    id: 'N-3',
    status: 'miss',
    text: 'You missed your appointment on Wed 23 Jul.',
    at: '23 Jul 2026',
  },
]
