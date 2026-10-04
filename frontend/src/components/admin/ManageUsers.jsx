import { useEffect, useState } from 'react'
import Cell from '../Cell'
import Mark from '../Mark'
import { ROLE } from '../../constants'
import { listUsers } from './usersApi'

/* Manage Users (SCRUM-88): every account, with a search box and a role
   filter. The list comes from the server on every change, so what is shown
   is always what the server holds. Add, edit and remove come in later steps,
   once their endpoints exist. */

const SEARCH_DELAY_MS = 300

const ROLE_LABEL = {
  [ROLE.PATIENT]: 'Patient',
  [ROLE.DOCTOR]: 'Doctor',
  [ROLE.RECEPTIONIST]: 'Receptionist',
  [ROLE.ADMIN]: 'Admin',
}

const ROLE_OPTIONS = Object.entries(ROLE_LABEL).map(([value, label]) => ({ value, label }))

function messageFor(error) {
  if (error.status === 403) return 'Only an admin can see the user list.'
  return error.message || 'Something went wrong. Please try again.'
}

function ManageUsers() {
  const [search, setSearch] = useState('')
  // What the server is actually asked for: the search text, once typing pauses.
  const [query, setQuery] = useState('')
  const [role, setRole] = useState('')
  const [reloads, setReloads] = useState(0)
  const [result, setResult] = useState({ key: null, users: null, error: null })

  useEffect(() => {
    const timer = setTimeout(() => setQuery(search.trim()), SEARCH_DELAY_MS)
    return () => clearTimeout(timer)
  }, [search])

  const key = `${role}|${query}|${reloads}`

  useEffect(() => {
    let ignore = false
    listUsers({ role, search: query })
      .then((users) => {
        if (!ignore) setResult({ key, users, error: null })
      })
      .catch((error) => {
        if (!ignore) setResult({ key, users: null, error })
      })
    // A newer search makes this answer stale, so it must not overwrite it.
    return () => {
      ignore = true
    }
  }, [key, role, query])

  const loading = result.key !== key
  const { users, error } = result
  const filtered = Boolean(query || role)

  return (
    <div className="sheet">
      <div className="sheet__head">
        <div>
          <h2>Users</h2>
          {users && (
            <p className="meta">
              {users.length} {users.length === 1 ? 'account' : 'accounts'}
              {filtered ? ' match' : ''}
            </p>
          )}
        </div>
      </div>

      <div className="sheet__body">
        <div className="cells admin-form__cells">
          <Cell
            label="Search"
            type="search"
            autoComplete="off"
            placeholder="Name or phone number"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
          <Cell
            label="Role"
            placeholder="All roles"
            options={ROLE_OPTIONS}
            value={role}
            onChange={(e) => setRole(e.target.value)}
          />
        </div>
      </div>

      {error && !loading && (
        <div className="sheet__body" role="alert">
          <p className="form-error">{messageFor(error)}</p>
          <button type="button" className="btn btn--quiet" onClick={() => setReloads((n) => n + 1)}>
            Try again
          </button>
        </div>
      )}

      {!users && loading && (
        <div className="sheet__body" role="status">
          <p className="lede">Loading users…</p>
        </div>
      )}

      {users?.length === 0 && (
        <div className="sheet__body">
          <p className="lede">
            {filtered ? 'No users match these filters.' : 'There are no users yet.'}
          </p>
        </div>
      )}

      {users?.length > 0 && (
        <table className="admin-table" aria-busy={loading}>
          <thead>
            <tr>
              <th>Name</th>
              <th>Phone</th>
              <th>Role</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {users.map((u) => (
              <tr key={u.userId} className={u.active ? undefined : 'is-inactive'}>
                <td data-label="Name">
                  <span className="admin-table__name">{u.fullName}</span>
                </td>
                <td data-label="Phone">{u.phoneNumber}</td>
                <td data-label="Role">{ROLE_LABEL[u.role] ?? u.role}</td>
                <td data-label="Status">
                  <Mark status={u.active ? 'live' : 'done'} label={u.active ? 'Active' : 'Removed'} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}

export default ManageUsers