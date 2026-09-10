# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

**Primary — the patient.** Two distinct situations, same person:

- *Walk-in.* Arrives at a dental clinic without an appointment during a busy period. Today they take a paper number or a verbal "you're after him" and then sit in a crowded waiting room with no idea whether their turn is in ten minutes or two hours. They cannot leave to run an errand because they might lose their place. Their job: know where they stand and how long they have.
- *Scheduled patient.* Wants to book a slot without phoning the clinic during opening hours, then check in against that booking on arrival.

**Operator — the receptionist.** Runs the live queue from the front desk during peak rush while also handling walk-ups, phone calls, and payments. Any interaction that slows the physical desk is a failure.

**Payer — the clinic owner.** Buys the product to reduce phone interruptions, no-shows, and waiting-room overcrowding. Does not use the queue screen day to day.

## Product Purpose

QCare is a queue and appointment management system for dental clinics in North Lebanon. It combines online appointment booking with a QR-based digital walk-in queue that shows patients their live position and estimated wait, plus a receptionist dashboard for running the queue.

Success means a patient can see their place in line from their own phone without standing in the clinic, and a receptionist can advance the queue without slowing the front desk.

## Positioning

The only option in this market combining scheduled booking *and* a live walk-in queue in one system, built for walk-in-heavy clinics, bilingual Arabic/English, and compatible with cash payment.

Competitors researched: Fresha (booking marketplace, no live walk-in queue), Hjezle (calendar booking, no real-time queue), and the status quo of phone and paper. The walk-in queue is the part neighboring products do not do.

## Operating Context

- **Walk-in journey:** patient scans a QR code at the clinic → receives a digital ticket → sees live position and ETA on their own phone → is notified when their turn approaches.
- **Scheduled journey:** patient books online → arrives → checks in against the existing booking.
- **Receptionist journey:** operates the queue from a dashboard at the front desk during rush.
- **Local conditions:** Lebanon has frequent power and internet outages. The validation document flagged weak-connectivity resilience (local caching on the receptionist device, sync on reconnect) as the top local risk to mitigate. This is a known design constraint, not a shipped MVP feature.

## Capabilities and Constraints

**MVP scope, in priority order:** (1) online appointment booking, (2) digital walk-in queue via QR ticket, (3) ETA display.

**Explicitly out of MVP scope:** WhatsApp/SMS reminders and confirmations, in-app payments, advanced analytics, staff scheduling, missed-call handling, offline sync.

**Technical:** React (Vite, JavaScript) frontend; Java Spring Boot backend; MySQL; QR generated client-side. Frontend and backend live in one repository (`frontend/`, `Backend/`).

**Undecided:** the authentication contract. The backend currently has no auth endpoints — only a Spring Boot application skeleton. Login and registration API shape, session/token strategy, and role model are not yet designed.

**Roles the interface must eventually distinguish:** guest (not signed in), patient, receptionist, doctor. The exact permission boundaries are not yet settled.

## Brand Commitments

- Product name: **QCare**.
- Logo: a teal tooth outline crossed by a deep petrol-blue swoosh, on white. Currently committed at `frontend/public/logo.jpg` on the unmerged `feature/add-background` branch. The logo's two-tone teal is a binding brand asset.
- Bilingual Arabic/English is a stated product commitment, which means the interface must eventually support right-to-left layout.

## Evidence on Hand

- Product validation document (`Java_TeamC_ProjectValidation.pdf`): team decision GO, scored 24/30, submitted 2026-09-01. Source of the user research, competitor analysis, and business model recorded above.
- Business model: subscription at $10–30/month per clinic location, paid by cash or transfer. Go-to-market is direct outreach to local clinics plus university and community partnerships.

**Absences future work must not fabricate:** QCare has no live clinics, no real patients, no testimonials, no usage metrics, and no named customers. Any clinic name, doctor name, patient name, appointment, review, or statistic appearing in the interface is demonstration data and must be labeled as such. Pricing above is a plan, not a published offer.

## Product Principles

1. **The wait is the product.** The thing QCare sells a patient is knowing where they stand. Position and time are the information everything else is arranged around.
2. **The front desk sets the pace.** The receptionist is operating under pressure with a queue of physical humans. Interactions on operator surfaces are judged by whether they slow that desk down.
3. **Assume the connection drops.** Power and internet outages are normal operating conditions here, not edge cases. Interfaces should degrade legibly rather than fail blank.
4. **Bilingual is structural, not a translation pass.** Arabic/English means layout that can mirror, not text that gets swapped.
5. **Nothing is claimed that does not exist.** This is a pre-launch student product; the interface demonstrates a mechanism, it does not advertise traction.

## Accessibility & Inclusion

Right-to-left layout support is required by the bilingual commitment. The walk-in queue is read on a personal phone in a waiting room, so mobile legibility and sufficient contrast in variable lighting are functional requirements, not preferences.
