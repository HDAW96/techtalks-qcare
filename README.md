# techtalks-qcare🦷🥼

# QCare

> Localized queue and appointment management system for dental clinics.

## Overview

* **Problem:** Lack of front-desk queue coordination causes 30–60+ minute waiting room delays for patients and constant phone interruptions for staff.
* **Target Users:** Dental clinic owners, receptionists, walk-in patients, and scheduled clients in North Lebanon.
* **Solution:** A web app pairing online booking with a QR-based live walk-in queue, giving patients real-time position and ETA updates on their phones.

## Approved MVP Scope

| Category | Features / Details |
| :--- | :--- |
| **In Scope** | Online booking, QR walk-in queue, patient check-in, live position/ETA tracking, simple receptionist dashboard. |
| **Also built** | Doctor dashboard with clinical notes and patient history; an admin area to manage staff accounts and treatments; a clinic analytics dashboard for any date range: appointments (completed, cancelled, no-show, upcoming), walk-ins, average wait, patients per doctor, busiest treatments and busiest hours. |
| **Out of Scope** | WhatsApp/SMS reminders, in-app payments, staff scheduling, offline sync logic, arabic interface. |
| **Current Status** | Approved (TechTalks Internship 2026). Development in final stages. |

## Tech Stack

* **Frontend:** React.js
* **Backend:** Java : Spring Boot
* **Database:** MySQL
* **Utilities:** Client-side QR generation

## Team & Roles

| Member | Primary Responsibility |
| :--- | :--- |
| **Hadi Abdulhakim Awad** | Team Leader & Fullstack Developer|
| **Jouri Said** | Backend Developer |
| **Hassan El Hussein** | Fullstack Developer |
| **Mohamad Itani** | Backend Developer |
| **Mohammad Dakdouk** | Frontend Developer |

## Live demo

Link will be added after deployment.

## How we merge

No pull request is merged without **one approval from someone other than its
author**.

## Running it locally

Want to see the whole app with sample data? Do steps 1-5 below, then follow
[Demo mode](#demo-mode) and the [demo walkthrough](docs/demo-walkthrough.md).

### What you need first

| | Version | Notes |
| :--- | :--- | :--- |
| **Java** | 25 | `java -version` to check |
| **Node.js** | 24 (at least 22.12) | `node -v` to check. 24 is what CI tests on; Vite 8 refuses older versions |
| **MySQL** | 8.4 | Must be running before the backend starts |

Maven is **not** needed — the project ships with the `mvnw` wrapper.

### 1. Clone

```bash
git clone https://github.com/HDAW96/techtalks-qcare.git
cd techtalks-qcare
```

### 2. Create the database

The backend creates the tables itself on first run, but the empty database has
to exist first:

```sql
CREATE DATABASE qcare_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

`utf8mb4` matters — it is what stores Arabic text correctly.

### 3. Tell the backend your MySQL password

The password is **not** in the repository, so each of us sets it once on our own
machine. Use whatever password your local MySQL root account has.

**Windows (PowerShell):**

```powershell
[Environment]::SetEnvironmentVariable('QCARE_DB_PASSWORD','your-password','User')
```

**macOS / Linux:** add to `~/.zshrc` or `~/.bashrc`:

```bash
export QCARE_DB_PASSWORD='your-password'
```

Open a new terminal afterwards, or the variable will not be visible yet.

> Without this the backend stops at startup with
> `Access denied for user 'root'@'localhost'`.

That is the only setting a laptop needs. Everything else has a local default;
see [Configuration](#configuration).

### 4. Run the backend

```bash
cd Backend
./mvnw spring-boot:run
```

**Windows (PowerShell):**

```powershell
cd Backend
.\mvnw.cmd spring-boot:run
```

Starts on **http://localhost:8080**. On first run it creates the tables and
adds the clinic's treatments, doctors, receptionists and an admin, so there is
something to book and someone to sign in as (see
[Seeded accounts](#seeded-accounts)).

It logs one warning about development defaults. That is expected on a laptop.

**If it stops at startup** with `Communications link failure` or
`Unable to determine Dialect`, MySQL is not running. Start the MySQL service
(on Windows: Services → MySQL → Start) and run the backend again.

### 5. Run the frontend

In a second terminal:

```bash
cd frontend
npm install
npm run dev
```

Open **http://localhost:5173**.

### After every `git pull`

`git pull` brings new code but does not install new libraries. If someone
added one, the frontend stops with an error like
`Failed to resolve import "qrcode"`. Run this after pulling:

```bash
cd frontend
npm install
```

It is safe to run every time; it does nothing when nothing changed. The
backend needs no extra step — `mvnw` fetches new libraries by itself.

### The three ports

| Port | What |
| :--- | :--- |
| 5173 | Frontend — the pages you see |
| 8080 | Backend — the API the pages call |
| 3306 | MySQL — where everything is stored |

Both servers need to be running. The frontend talks to `localhost:8080` by
default; set `VITE_API_BASE_URL` if yours is elsewhere.

### Looking at the screens without a backend

**Windows (PowerShell):**

```powershell
$env:VITE_USE_MOCK_AUTH='true'; npm run dev
```

**macOS / Linux:**

```bash
VITE_USE_MOCK_AUTH=true npm run dev
```

Signs in locally with sample data and accepts any password. Useful for working
on a screen's appearance; nothing is saved anywhere.

### Scanning the QR code with a real phone

The QR code links to `QCARE_FRONTEND_URL` (default `http://localhost:5173`),
which a phone cannot reach. To try it on a phone on the same Wi-Fi, use the
laptop's network address (shown by `ipconfig`, e.g. `192.168.1.20`) in three places:

```powershell
# backend: where the QR points, and which frontend may call the API
$env:QCARE_FRONTEND_URL = 'http://192.168.1.20:5173'
$env:QCARE_CORS_ORIGINS = 'http://localhost:5173,http://192.168.1.20:5173'
# frontend: where the phone finds the API, and listen on the network
$env:VITE_API_BASE_URL = 'http://192.168.1.20:8080/api'
npm run dev -- --host
```

If the phone still cannot open the page, Windows Firewall may be blocking
ports 5173 and 8080 on a network marked "Public".

## What currently works

**Done** means built end to end, backend and screen. **Partial** means it works
with a limit, given in the notes. **Not built** means it does not exist yet.

### Patient

| Feature | Status | Notes |
| :--- | :--- | :--- |
| Register and sign in | Done | Phone number + full name + password (at least 8 characters, with an uppercase letter, a lowercase letter and a special character). A family can share one phone. |
| Book an appointment | Done | Pick a doctor, a treatment and a free time; the server works out the slots |
| Reschedule and cancel | Done | |
| My appointments | Done | On the dashboard |
| Join the queue by QR (`/join`) | Done | Pick an open doctor and a treatment; with or without an account; one ticket per person per day |
| Live ticket (`/ticket`, and the dashboard) | Done | Number, people ahead, wait, now serving; refreshes every 15 seconds and at once when the phone is unlocked |
| Give up my place | Done | Leaves the queue after a confirmation |
| Profile | Done | Read-only; name and phone number are changed at the desk |
| Notifications | Not built | The dashboard shows sample notifications; there is no notifications table yet |

### Receptionist (`/reception`)

| Feature | Status | Notes |
| :--- | :--- | :--- |
| Front desk | Done | A day's appointments and the walk-in queues on one screen |
| Open, close and reopen a queue | Done | One queue per doctor per day; shows the signed daily QR code |
| Add walk-ins | Done | |
| Check in a booked patient | Done | Puts them in their doctor's queue, linked to the booking |
| Find patient | Done | By name or phone number, in any common Lebanese format |
| Call in, skip, finish | Done | One patient in the chair per doctor; the server decides the order |
| Reschedule any appointment | Done | Any day, any patient |

### Doctor (`/doctor`)

| Feature | Status | Notes |
| :--- | :--- | :--- |
| Dashboard | Done | Today's appointments, the patient in the chair and the waiting line (ticket numbers only) |
| Patient in the chair | Done | Booking or walk-in |
| Patient history | Done | Past completed visits with the doctor's notes |
| Complete visit with notes | Done | Notes are optional; only doctors can read them |
| Walk-in visits | Partial | A walk-in can be completed, but notes cannot be saved for it and it does not appear in patient history, because it has no appointment |
| Time off | Partial | The server blocks the days and stops them being booked (`/api/doctors/me/leave`), but there is no screen for it yet |

### Admin (`/admin`)

| Feature | Status | Notes |
| :--- | :--- | :--- |
| Manage users | Done | Add, edit and remove staff accounts. Removing deactivates the account rather than deleting it, so its history stays |
| Manage services | Done | Add, edit, deactivate and delete treatments. One already used by an appointment or a queue ticket can only be deactivated |
| Analytics dashboard | Partial | Works for any date range. A booking is counted as a no-show once its time has passed while it is still booked (or marked missed). A patient who has been checked in is not counted as a no-show |
| Subscriptions and Reports | Not built | Left out of the admin sidebar until they are built |

### Everyone

| Feature | Status | Notes |
| :--- | :--- | :--- |
| Public queue board (home page) | Done | Ticket numbers and waits only, no names |
| Change password | Done | From the footer ("Change password") |
| Forced first-time password change | Done | Accounts created by the seeder or by an admin must choose their own password at first sign-in. The server enforces it, not only the screen |

## Known limits

What QCare does not do, so nobody is surprised in a demo or a review. Most of
these are the approved scope; the rest are noted in the tables above.

- **Walk-in visits have no notes and no history.** A walk-in has no
  appointment to attach notes to, so the doctor cannot save notes for one and
  the visit does not appear in patient history. This is by design.
- **Doctors' time off has no screen.** The server enforces it
  (`/api/doctors/me/leave`) but it can only be set through the API.
- **Notifications are sample data.** The patient dashboard panel is not backed
  by a table, and there are no SMS or WhatsApp reminders.
- **English interface only.** Arabic names and text are stored correctly
  (`utf8mb4`), but there is no Arabic interface.
- **The profile is read-only.** A name or phone number is changed at the desk.
- **No payments, staff scheduling, subscriptions or reports.** Subscriptions
  and Reports are left out of the admin sidebar.
- **"Today" is the server's date.** If the backend runs in another time zone
  from the clinic, start it with
  `-Dspring-boot.run.jvmArguments=-Duser.timezone=Asia/Beirut`.
- **Scanning the QR with a real phone needs extra setup.** See
  [Scanning the QR code with a real phone](#scanning-the-qr-code-with-a-real-phone).
- **Demo mode is for fresh, local databases only.** It overwrites the demo
  accounts' passwords with a public one and refuses to run in production.
- **The frontend tests are not in CI yet.** Run `npm test` yourself before
  opening a pull request.
- **Not deployed.** The live demo link above is empty until it is.

## Seeded accounts

There is one sign-in form for everyone. The role on the account decides where
it takes you: a patient lands on their record, a receptionist on the front desk
at `/reception`, a doctor on `/doctor` and an admin on `/admin`.

Registering online always creates a patient. Staff accounts are made by an
admin under **Manage users**, or by the seeder on first run. The seeder creates
these:

| Role | Phone | Full name |
| :--- | :--- | :--- |
| Admin | `03400011` | `Admin One` |
| Doctor | `03400001` | `Dr. Rania Haddad` |
| Doctor | `03400002` | `Dr. Karim Fares` |
| Receptionist | `03400009` | `Reception Desk One` |
| Receptionist | `03400010` | `Reception Desk Two` |

All of them start with the password set in `QCARE_SEED_PASSWORD`. On a laptop
that is not set, so the password is **`changeme123`** — for local development
only.

Every seeded account must **choose its own password at first sign-in**. The
website takes you straight to that screen, and the server enforces it too:
until the password is changed, every request other than restoring the session
and changing the password is refused with `403 PASSWORD_CHANGE_REQUIRED`. After
that, sign in with the password you chose.

Each block is added only if the database has none of that role yet, so an
existing database keeps the accounts and passwords it already has.

## Demo mode

> **Demo only.** The password below is public and the demo overwrites the
> passwords of the five demo accounts. Never use demo mode on a real or shared
> database.

Demo mode fills a **fresh** database with one known login per role, today's
appointments, upcoming appointments and an open queue with sample tickets, so
every screen has something to show.

### Run it

1. Create an empty `qcare_db` (step 2 above), start MySQL and set
   `QCARE_DB_PASSWORD` (step 3 above).
2. Start the backend with the `demo` profile:

   ```bash
   cd Backend
   SPRING_PROFILES_ACTIVE=demo ./mvnw spring-boot:run
   ```

   **Windows (PowerShell):**

   ```powershell
   cd Backend
   $env:SPRING_PROFILES_ACTIVE = 'demo'
   .\mvnw.cmd spring-boot:run
   ```

3. Start the frontend as in step 5 (`cd frontend && npm install && npm run dev`)
   and open **http://localhost:5173**.

### Demo accounts

All five accounts use the same demo-only password: **`Demo-Pass1!`**
(override it with the `QCARE_DEMO_PASSWORD` environment variable before starting).
Sign in with phone number, full name and password. None of them is asked to
change the password.

| Role | Phone | Full name | Password | Lands on |
| :--- | :--- | :--- | :--- | :--- |
| Receptionist | `03400009` | `Reception Desk One` | `Demo-Pass1!` | `/reception` |
| Doctor | `03400001` | `Dr. Rania Haddad` | `Demo-Pass1!` | `/doctor` |
| Admin | `03400011` | `Admin One` | `Demo-Pass1!` | `/admin` |
| Patient | `03400020` | `Demo Patient` | `Demo-Pass1!` | `/dashboard` |

The table lists one login per role. The fifth account, a second doctor
(`03400002` / `Dr. Karim Fares`), gets the same password.

### Walkthrough

A step-by-step script for showing each role (receptionist, doctor, patient,
admin) is in [docs/demo-walkthrough.md](docs/demo-walkthrough.md).

### What it creates

- Today: five appointments across both doctors (one completed, one checked in,
  three booked) and an **open queue** for Dr. Haddad with one finished, one in
  the chair and three waiting tickets. Dr. Fares has an open, empty queue.
- Upcoming: booked appointments over the next days, two of them for Demo Patient;
  Demo Patient also has a completed and a cancelled past visit. Demo Patient has no
  ticket today, so they can join the queue through the `/join` QR flow.
- Sample patients use the reserved phones `03400101`-`03400108`.

### Running it again

Restarting with the demo profile is safe: nothing is duplicated. It restores the
demo passwords (even if you changed one), clears the *must change password* flag,
and rebuilds only the sample patients' appointments and tickets relative to
today's date. Other people's data is left alone.

The demo refuses to start when `QCARE_ENV=production`, and against a MySQL
server that is not `localhost` (override for the host check only:
`qcare.demo.allow-remote=true`; nothing overrides the production check).

"Today" is the backend machine's date. If it runs in another time zone from the
clinic, start it with `-Dspring-boot.run.jvmArguments=-Duser.timezone=Asia/Beirut`.

## Configuration

Everything is set through environment variables. Only `QCARE_DB_PASSWORD` is
needed on a laptop; a deployed server needs every variable marked "Yes".

| Variable | What it does | Required in production | Local default |
| :--- | :--- | :--- | :--- |
| `QCARE_DB_USER` | MySQL user name | Recommended | `root` |
| `QCARE_DB_PASSWORD` | MySQL password | Yes | none — must be set |
| `SPRING_DATASOURCE_URL` | Full JDBC address of the database. Overrides the one in `application.properties` without a code change | Yes, unless MySQL is on the same machine | `jdbc:mysql://localhost:3306/qcare_db?...` |
| `QCARE_JWT_SECRET` | Signs sign-in tokens. At least 32 characters | Yes | a public development value |
| `QCARE_QR_SECRET` | Signs the clinic's daily QR code | Yes | a public development value |
| `QCARE_SEED_PASSWORD` | Password the seeder gives the staff accounts it creates | Yes | `changeme123` |
| `QCARE_ENV` | Set to `production` on a deployed server to turn on the startup check (see [Security](#security)) | Yes | not set |
| `QCARE_FRONTEND_URL` | Where the QR code's link points: the frontend's address | Yes | `http://localhost:5173` |
| `QCARE_CORS_ORIGINS` | Which frontend addresses may call the API, comma-separated | Yes | `http://localhost:5173` |
| `QCARE_SHOW_SQL` | `true` prints every SQL query to the log, for debugging | No | `false` |
| `VITE_API_BASE_URL` | Frontend: where the API is. Read when the frontend is built | Yes | `http://localhost:8080/api` |
| `VITE_USE_MOCK_AUTH` | Frontend: `true` signs in with sample data and no backend | No — never in production | not set |

## Security

* **Passwords** are stored as BCrypt hashes, never as text.
* **Sign-in** returns a signed JWT that the browser sends with every request.
  Each request re-reads the account, so deactivating an account locks it out at
  once. Tokens expire after 12 hours.
* **Role-based access**: patients, receptionists, doctors and admins each reach
  only their own endpoints, checked on the server.
* **Signed daily QR codes**: the clinic QR carries a signature for that day, so
  an old or edited code cannot be used to join a queue.
* **Clinical notes are for doctors only.** Patients do not see them in their
  own appointments, and receptionists cannot reach patient history.
* **The public queue board shows ticket numbers only**, never names.
* **Accounts are deactivated, not deleted**, so history stays intact and a
  removed account can no longer sign in.
* **Forced password change** for seeded and admin-created accounts, enforced by
  the server.
* **Secrets live only in environment variables.** The repository holds
  development defaults only.
* **Production startup check**: with `QCARE_ENV=production`, the backend
  refuses to start while `QCARE_JWT_SECRET`, `QCARE_QR_SECRET` or
  `QCARE_SEED_PASSWORD` still has its development default, and names the
  variable to set. Anywhere else it logs one warning instead.

## Testing

**Backend** — 114 tests, against an in-memory database, so MySQL does not need
to be running. The demo profile has its own isolated test
(`DemoDataSeederTest`):

```bash
cd Backend
./mvnw test
```

**Windows (PowerShell):** `.\mvnw.cmd test`

**Frontend** — 27 tests:

```bash
cd frontend
npm test
```

CI runs on every pull request and on `main` after a merge. It runs the backend
tests, and the frontend lint and build (`npm run lint`, `npm run build`). The
frontend tests are not part of CI yet, so run `npm test` yourself before
opening a pull request.
