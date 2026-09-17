# QCare Backend Architecture & N-Tier Structure Documentation

## Overview
This document verifies and outlines the backend software architecture for the **QCare** system.

---

## Package & Layer Mapping

The system logic is decoupled into distinct packages inside `src/main/java`:

### 1. Presentation & Web Layer
* **`controller/`**: Exposes RESTful API endpoints for client communications (mobile/web apps). Handles incoming HTTP requests, validates input parameters, and returns response payloads.
* **`dto/`**: Data Transfer Objects used to encapsulate request and response bodies, preventing raw database entities from being exposed to clients.
* **`web/`**: Manages web-specific configurations and custom controllers/adapters for external integrations.

### 2. Business Logic Layer
* **`service/`**: Implements core application workflows, domain-specific rules (such as booking appointments, resolving free time slots, and queue management), and defines transaction boundaries (`@Transactional`).

### 3. Data Access & Persistence Layer
* **`repository/`**: Abstraction layer built on Spring Data JPA. Contains interfaces for database operations, custom query methods, and tenant-scoped data retrieval.
* **`entity/`**: Maps relational database models to Java object structures representing persistent data entities.

### 4. Security & Configuration
* **`config/`**: System-wide Spring configurations (CORS, Beans, and Application properties).
* **`security/`**: Authentication and Authorization management, enforcing JWT session verification and Phone Sign-in security filters.

### 5. Cross-Cutting Utilities
* **`util/`**: Helper methods, date/time transformers, and response formatter utilities.


### The walk-in queue

`QueueService` holds the queue rules, and `QueueController` exposes them under
`/api/queues`. Two rules shape the rest:

* **One doctor, one chair.** At most one ticket in a queue may be `IN_CHAIR`.
* **The server decides the order.** Positions and waiting times are worked out
  on the server and sent down, never calculated in the browser. Two
  receptionists on two screens must not be able to disagree about who is next.

Only these moves are allowed, and any other is refused:

`WAITING → IN_CHAIR`, `IN_CHAIR → DONE`, `WAITING → SKIPPED`, `SKIPPED → WAITING`

Every endpoint under `/api/queues` answers with patients' names and phone
numbers, so the whole path is restricted to `ROLE_RECEPTIONIST` in
`SecurityConfig`.

### Recommendations

The patient-facing queue board still needs an endpoint of its own. It cannot
reuse the desk's: a board read in a waiting room must carry ticket numbers
only, never the names sitting behind them.
