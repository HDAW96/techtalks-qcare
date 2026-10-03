import { apiGet } from '../../api/client'

/* The admin side of the clinic accounts (SCRUM-84, shown by SCRUM-88):

     GET /api/admin/users?role=DOCTOR&search=ran
       → [{ userId, fullName, phoneNumber, role }]

   Both filters are optional. An empty value must be left out of the URL,
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