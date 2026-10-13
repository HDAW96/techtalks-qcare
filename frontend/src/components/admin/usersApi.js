import { apiDelete, apiGet, apiPost, apiPut } from '../../api/client'

/* The admin side of the clinic accounts (SCRUM-84, 85, 86 and 87), shown by
   SCRUM-88 and SCRUM-104:

     GET    /api/admin/users?role=DOCTOR&search=ran
              → [{ userId, fullName, phoneNumber, role, active }]
     POST   /api/admin/users
              { fullName, phoneNumber, password, role, specialization? }
              → 201 + { userId, fullName, phoneNumber, role, active }
     PUT    /api/admin/users/{id}
              any of { fullName, phoneNumber, role, password }: only the fields
              sent are changed
              → { userId, fullName, phoneNumber, role }
     DELETE /api/admin/users/{id}
              → 204. The account is kept but switched off (active: false).

   role is DOCTOR, RECEPTIONIST or ADMIN: patients register themselves, and a
   patient's role cannot be changed. A password set here is temporary, its
   owner must choose their own at next sign-in.

   Both list filters are optional. An empty value must be left out of the URL,
   not sent as `role=`: the server would look for a role called "". */

const PATH = '/admin/users'

/* People type a number as "03 400 001" but it is stored as 03400001, and the
   server matches the text as typed. So a search made only of digits, spaces,
   dashes and plus signs is sent without the spaces and dashes. */
const PHONE_LIKE = /^[\d\s+-]+$/

export function listUsers({ role, search }) {
  const params = new URLSearchParams()
  if (role) params.set('role', role)
  if (search) params.set('search', PHONE_LIKE.test(search) ? search.replace(/[\s-]/g, '') : search)
  const query = params.toString()
  return apiGet(query ? `${PATH}?${query}` : PATH)
}

export function createUser(body) {
  return apiPost(PATH, body)
}

export function updateUser(id, body) {
  return apiPut(`${PATH}/${id}`, body)
}

export function removeUser(id) {
  return apiDelete(`${PATH}/${id}`)
}