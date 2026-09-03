# techtalks-qcare

# QCare

> Localized queue and appointment management system for dental clinics in North Lebanon[cite: 7].

## Overview

* **Problem:** Lack of front-desk queue coordination causes 30–60+ minute waiting room delays for patients and constant phone interruptions for staff.
* **Target Users:** Dental clinic owners, receptionists, walk-in patients, and scheduled clients in North Lebanon.
* **Solution:** A web app pairing online booking with a QR-based live walk-in queue, giving patients real-time position and ETA updates on their phones.

## Approved MVP Scope

| Category | Features / Details |
| :--- | :--- |
| **In Scope** | Online booking, QR walk-in queue, patient check-in, live position/ETA tracking, simple receptionist dashboard. |
| **Out of Scope** | WhatsApp/SMS reminders, in-app payments, advanced analytics, staff scheduling, offline sync logic. |
| **Current Status** | Approved with Conditions (TechTalks Internship 2026)[cite: 7]. Development on hold pending implementation guidelines; on-ground pilot outreach active. |

## Tech Stack

* **Frontend:** React.js
* **Backend:** Java Spring Boot
* **Database:** MySQL
* **Utilities:** Client-side QR generation

## Team & Roles

| Member | Primary Responsibility |
| :--- | :--- |
| **Hadi Abdulhakim Awad** | Team Leader / Proposal, Evaluation & Owner Interviews |
| **Jouri Said** | Customer Research & Evidence Collection |
| **Hassan El Hussein** | Market Research & Competitor Analysis |
| **Mohammed Itani** | Customer Interviews & Scope Definition |
| **Mohammed Dakdouk** | Customer Interviews & Visual Evidence |

## Quick Start

```bash
# Clone project
git clone [https://github.com/your-org/qcare.git](https://github.com/your-org/qcare.git)
cd qcare

# Run Backend (Spring Boot)
./mvnw spring-boot:run

# Run Frontend (React)
cd frontend
npm install && npm start
