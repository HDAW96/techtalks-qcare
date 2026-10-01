import { ApiError, apiDelete, apiGet, apiPatch, apiPost } from '../../api/client'

/* The admin side of the clinic's services (SCRUM-90), built against the
   contract agreed for SCRUM-89:

     GET    /api/admin/services        → [{ id, name, minutes, active, inUse }]
     POST   /api/admin/services        { name, minutes }           → 201 + row
     PATCH  /api/admin/services/{id}   any of { name, minutes, active } → row
     DELETE /api/admin/services/{id}   → 204, or 409 SERVICE_IN_USE

   ── SAMPLE DATA ─ REMOVE BEFORE MERGE ──────────────────────────────
   SCRUM-89 is not on main yet, so while this is true the page talks to the
   in-memory list below instead of the server. It answers with the same
   shapes and error codes, so turning it off is the only change needed once
   the endpoints exist. */
export const USE_SAMPLE_SERVICES = true

const PATH = '/admin/services'

export function listServices() {
  return USE_SAMPLE_SERVICES ? sample.list() : apiGet(PATH)
}

export function createService(body) {
  return USE_SAMPLE_SERVICES ? sample.create(body) : apiPost(PATH, body)
}

export function updateService(id, changes) {
  return USE_SAMPLE_SERVICES ? sample.update(id, changes) : apiPatch(`${PATH}/${id}`, changes)
}

export function deleteService(id) {
  return USE_SAMPLE_SERVICES ? sample.remove(id) : apiDelete(`${PATH}/${id}`)
}

/* ── SAMPLE DATA ─ REMOVE BEFORE MERGE ─────────────────────────────── */

let rows = [
  { id: 1, name: 'Consultation', minutes: 15, active: true, inUse: true },
  { id: 2, name: 'Cleaning', minutes: 30, active: true, inUse: true },
  { id: 3, name: 'Filling', minutes: 45, active: true, inUse: true },
  { id: 4, name: 'Root canal', minutes: 90, active: true, inUse: false },
  { id: 5, name: 'Teeth whitening', minutes: 60, active: false, inUse: true },
  { id: 6, name: 'Braces check', minutes: 20, active: false, inUse: false },
]
let nextId = 7

const wait = (value) => new Promise((resolve) => setTimeout(() => resolve(value), 250))
const fail = (status, code, message) =>
  new Promise((_, reject) => setTimeout(() => reject(new ApiError(message, status, code)), 250))

function check({ name, minutes }, id) {
  if (name !== undefined) {
    const trimmed = String(name).trim()
    if (!trimmed) return [400, 'NAME_REQUIRED', 'Name is required.']
    if (trimmed.length > 100) return [400, 'NAME_TOO_LONG', 'Name is too long.']
    const clash = rows.some((r) => r.id !== id && r.name.toLowerCase() === trimmed.toLowerCase())
    if (clash) return [409, 'SERVICE_EXISTS', 'A service with this name already exists.']
  }
  if (minutes !== undefined && !(Number.isInteger(minutes) && minutes > 0 && minutes <= 480)) {
    return [400, 'DURATION_INVALID', 'Duration must be between 1 and 480 minutes.']
  }
  return null
}

const byName = (a, b) => a.name.localeCompare(b.name)

const sample = {
  list: () => wait(rows.map((r) => ({ ...r })).sort(byName)),

  create(body) {
    const problem = check(body)
    if (problem) return fail(...problem)
    const row = { id: nextId++, name: body.name.trim(), minutes: body.minutes, active: true, inUse: false }
    rows = [...rows, row]
    return wait({ ...row })
  },

  update(id, changes) {
    const row = rows.find((r) => r.id === id)
    if (!row) return fail(404, 'SERVICE_NOT_FOUND', 'Service not found.')
    const problem = check(changes, id)
    if (problem) return fail(...problem)
    const updated = { ...row, ...changes, ...(changes.name ? { name: changes.name.trim() } : {}) }
    rows = rows.map((r) => (r.id === id ? updated : r))
    return wait({ ...updated })
  },

  remove(id) {
    const row = rows.find((r) => r.id === id)
    if (!row) return fail(404, 'SERVICE_NOT_FOUND', 'Service not found.')
    if (row.inUse) return fail(409, 'SERVICE_IN_USE', 'This service has appointments.')
    rows = rows.filter((r) => r.id !== id)
    return wait(null)
  },
}
