import { apiDelete, apiGet, apiPatch, apiPost } from '../../api/client'

/* The admin side of the clinic's services (SCRUM-90), served by SCRUM-89:

     GET    /api/admin/services        → [{ id, name, minutes, active, inUse }]
     POST   /api/admin/services        { name, minutes }           → 201 + row
     PATCH  /api/admin/services/{id}   any of { name, minutes, active } → row
     DELETE /api/admin/services/{id}   → 204, or 409 SERVICE_IN_USE */

const PATH = '/admin/services'

export function listServices() {
  return apiGet(PATH)
}

export function createService(body) {
  return apiPost(PATH, body)
}

export function updateService(id, changes) {
  return apiPatch(`${PATH}/${id}`, changes)
}

export function deleteService(id) {
  return apiDelete(`${PATH}/${id}`)
}
