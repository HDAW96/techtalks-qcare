# QCare demo walkthrough

A script for showing QCare end to end, one role at a time. It takes about
15 minutes and uses the logins from **demo mode**, so the database starts with
something on every screen.

## Before you start

1. Follow **Running it locally** and **Demo mode** in the [README](../README.md)
   so the backend runs with `SPRING_PROFILES_ACTIVE=demo` and the frontend is
   open at **http://localhost:5173**.
2. Check the backend log says `Demo data ready`.

All four logins use the password **`Demo-Pass1!`**. The sign-in form asks for
phone number, full name and password, all three.

| Role | Phone | Full name |
| :--- | :--- | :--- |
| Receptionist | `03400009` | `Reception Desk One` |
| Doctor | `03400001` | `Dr. Rania Haddad` |
| Admin | `03400011` | `Admin One` |
| Patient | `03400020` | `Demo Patient` |

To switch role, use **Sign out** in the header and sign in as the next person.

**To run the demo again from the start**, stop the backend and start it again
with the demo profile. It puts today's sample appointments and queue back (see
"Running it again" in the README).

## What the demo database holds

- Today, Dr. Rania Haddad: five appointments (Layla Nasser 10:00 completed,
  Omar Khoury 10:30 checked in, Nour Aziz 11:00, Maya Saad 14:30 and Karl Bou
  Saab 15:00 booked) and an **open queue** with one finished ticket, one
  patient in the chair (Omar Khoury), and three waiting.
- Dr. Karim Fares has an open queue with nobody in it.
- Demo Patient has two upcoming appointments, one completed and one cancelled
  visit, and **no ticket today**.

## 1. The public home page (1 minute)

Open **http://localhost:5173** without signing in.

- The queue board shows **Now serving** and the waiting numbers for each open
  queue. Only ticket numbers appear, no names.

## 2. Receptionist (4 minutes)

Sign in as `03400009` / `Reception Desk One`. You land on **/reception**.

1. **Front desk.** Today's appointments are listed with their status
   (Completed, Checked in, Booked). The arrows step to the previous or next day.
2. **Queues.** Both doctors show **Queue open**. Choose Dr. Rania Haddad to see
   her queue: finished, in the chair, waiting. The **Clinic QR** is the code
   patients scan to join.
3. **Check in a booked patient.** On Nour Aziz's row press **Check in**. She
   goes to the back of Dr. Haddad's queue with a ticket linked to her booking.
4. **Add a walk-in.** In **Add walk-in** enter a full name, a phone number
   (for example `03 555 1212`) and a treatment, then **Add to queue**. They go
   to the back of the chosen queue.
5. **Find patient.** Open **Find patient** and type `Layla` or `03400101`
   (any common Lebanese phone format works). The matching patient's name and
   phone number are listed.
6. **Call in.** The chair is occupied (Omar Khoury), so **Call in** is not
   available yet: only one patient is in the chair per doctor. Carry on to the
   doctor, then come back to this step.

## 3. Doctor (4 minutes)

Sign out, then sign in as `03400001` / `Dr. Rania Haddad`. You land on
**/doctor**.

1. **Today** shows her appointments, the patient **In the chair** (Omar
   Khoury, with the ticket number) and the waiting line, which shows ticket
   numbers only.
2. Press **View history** on the patient in the chair to see past visits and
   the notes from them.
3. Type notes in the box (for example "Scaling done, polish next visit") and
   press **Save and finish**. The chair is free and the next patient can be
   called.
4. Notes are optional, and they are only visible to doctors.

Back as the receptionist (Sign out, then sign in as `03400009`): the chair is
free, so **Call in** works for the next waiting ticket. **Skip** moves a
ticket aside and **Bring back** returns it. **Done** finishes a visit from
the desk.

## 4. Patient, booking (3 minutes)

Sign out, then sign in as `03400020` / `Demo Patient`. You land on
**/dashboard**.

1. **My appointments** lists the two upcoming bookings plus the completed and
   cancelled visits.
2. Press **Book appointment**: **Choose a doctor**, **Choose a service**, **Pick
   a day and time** (the server offers only free times), then **Confirm
   booking**.
3. On a booked appointment you can **Reschedule** it or cancel it (**Yes,
   cancel it** confirms).

## 5. Patient, walk-in queue (2 minutes)

Still as Demo Patient, or as a guest in a private window.

1. Open the **join page**. The receptionist screen has an **Open the join
   page** link next to the clinic QR, which is what a phone reaches by
   scanning the code.
2. Choose an open doctor and a treatment, then **Take a ticket**. A signed-in
   patient joins as themselves; a guest gives a name and phone number.
3. **See my ticket** opens the live ticket: number, people ahead, estimated
   wait and now serving. It refreshes every 15 seconds. **Give up my place**
   leaves the queue after a confirmation.

## 6. Admin (3 minutes)

Sign out, then sign in as `03400011` / `Admin One`. You land on **/admin**.

1. **Users.** Add, edit and remove staff accounts. A new account is given a
   temporary password to share with the user, who must choose their own at
   first sign-in.
2. **Services.** Add, edit, deactivate and delete treatments. One already used
   by an appointment or a ticket can only be deactivated.
3. **Analytics.** Pick a date range. The page shows bookings by outcome,
   walk-ins, average wait, patients per doctor, busiest services and busiest
   hours. Open **How these are counted** for the exact definitions.

## Known limits to mention

These are deliberate or not built yet; see **Known limits** in the README for
the full list.

- Notes cannot be saved for a walk-in, by design: a walk-in has no appointment,
  so there is nothing to attach them to, and they are not in patient history.
- Doctors' time off is enforced by the server but has no screen yet.
- The notifications panel on the patient dashboard shows sample entries.
- There are no SMS or WhatsApp reminders, payments, or Arabic interface.
