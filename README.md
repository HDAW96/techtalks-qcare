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
| **Out of Scope** | WhatsApp/SMS reminders, in-app payments, advanced analytics, staff scheduling, offline sync logic. |
| **Current Status** | Approved with Conditions (TechTalks Internship 2026)[cite: 7]. Development ongoing. |

## Tech Stack

* **Frontend:** React.js
* **Backend:** Java Spring Boot
* **Database:** MySQL
* **Utilities:** Client-side QR generation

## Team & Roles

| Member | Primary Responsibility |
| :--- | :--- |
| **Hadi Abdulhakim Awad** | Team Leader & Fullstack Developer|
| **Jouri Said** | Backend Developer |
| **Hassan El Hussein** | Fullstack Developer |
| **Mohammed Itani** | Backend Developer |
| **Mohammed Dakdouk** | Frontend Developer |

## How we merge

No pull request is merged without **one approval from someone other than its
author**.

## Running it locally

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

Starts on **http://localhost:8080**. On first run it creates the nine tables and
adds the clinic's doctors and treatments, so there is something to book.

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

## What currently works

| Area | Status |
| :--- | :--- |
| Register, sign in, session | Real — phone number + full name + password (at least 8 characters, with an uppercase letter, a lowercase letter and a special character) |
| Doctors and treatments | Real — from the database |
| Free times and booking | Real — the server works out the slots |
| A patient's appointments | Real — including reschedule and cancel |
| Reception: a day's appointments | Real — list any day, reschedule any appointment |
| The desk's walk-in queue | Real — open, close and reopen a queue, add walk-ins, call in, skip, finish; live position and wait time; refreshes itself every 15 seconds |
| Receptionist sign-in and dashboard | Real — the same sign-in form, at `/reception` |
| Joining the queue by QR (`/join`) | Real — the desk shows a signed daily QR code; a patient picks an open doctor and a treatment and joins, with or without an account; one ticket per person per day |
| A patient's own ticket (`/ticket`, and the dashboard) | Real — number, people ahead, wait, now serving; refreshes every 15 seconds and at once when the phone is unlocked |
| Public queue board (home page) | Real — ticket numbers and waits only, no names |
| Notifications | **Sample data** — no table exists in the ERD yet |
| Profile | Read-only — name and phone number are changed at the desk |
| Check in booked patient / Find patient / Give up my place | Shown disabled — not built yet |
| Doctor screens | Not built |

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

### Signing in as the receptionist

There is one sign-in form for everyone. The role on the account decides where
it takes you: a patient lands on their record, a receptionist lands on the
front desk at `/reception`.

Registering online always creates a patient, so a desk account cannot be made
through the app. The seeder creates two, and unlike the doctors it checks for
them separately - a database that already has doctors would otherwise skip
them:

| Phone | Full name | Password |
| :--- | :--- | :--- |
| `03400009` | `Reception Desk One` | `Changeme123!` |
| `03400010` | `Reception Desk Two` | `changeme123` |

That is a development password on a development database. Both are flagged
`must_change_password`, so **the first sign-in on the website asks you to
choose your own password** before the desk opens. After that, sign in with the
password you chose. `changeme123` keeps working through the API until then,
which is what the automated tests use. Anyone signed in can change their
password again from the footer ("Change password").
