import { useEffect, useState } from 'react'
import Cell from '../Cell'
import Mark from '../Mark'
import { ROLE } from '../../constants'
import { isValidPhone, normalisePhone } from '../../phone'
import { MIN_PASSWORD, PASSWORD_NOTE, passwordProblem } from '../../password'
import { createUser, listUsers, removeUser, updateUser } from './usersApi'

/* Manage Users (SCRUM-88, SCRUM-104): every account, with a search box and a
   role filter, and the three things an admin does to staff accounts: add,
   edit and remove. The list comes from the server on every change, so what is
   shown is always what the server holds, including after each action. */

const SEARCH_DELAY_MS = 300

const ROLE_LABEL = {
  [ROLE.PATIENT]: 'Patient',
  [ROLE.DOCTOR]: 'Doctor',
  [ROLE.RECEPTIONIST]: 'Receptionist',
  [ROLE.ADMIN]: 'Admin',
}

const ROLE_OPTIONS = Object.entries(ROLE_LABEL).map(([value, label]) => ({ value, label }))

// Patients register themselves, so an admin can only create or assign staff roles.
const STAFF_OPTIONS = [ROLE.DOCTOR, ROLE.RECEPTIONIST, ROLE.ADMIN].map((value) => ({
  value,
  label: ROLE_LABEL[value],
}))

/* What each backend error code means to the person at the screen. `field`
   puts the message under the input it is about. */
const ERRORS = {
  NAME_REQUIRED: { field: 'fullName', text: 'Enter the full name.' },
  PHONE_INVALID: { field: 'phoneNumber', text: 'Enter a valid Lebanese phone number.' },
  ACCOUNT_EXISTS: {
    field: 'phoneNumber',
    text: 'An account already exists for this name and phone number.',
  },
  ROLE_INVALID: { field: 'role', text: 'Choose a role: doctor, receptionist or admin.' },
  PASSWORD_TOO_SHORT: { field: 'password', text: `At least ${MIN_PASSWORD} characters` },
  PASSWORD_NO_UPPERCASE: { field: 'password', text: 'Add at least one uppercase letter' },
  PASSWORD_NO_LOWERCASE: { field: 'password', text: 'Add at least one lowercase letter' },
  PASSWORD_NO_SPECIAL: {
    field: 'password',
    text: 'Add at least one special character, for example ! or -',
  },
  LAST_ADMIN: {
    field: 'role',
    text: 'There must always be at least one active admin, so this one cannot be removed or given another role.',
  },
  CANNOT_DEACTIVATE_SELF: { text: 'You cannot remove your own account.' },
  USER_ALREADY_DEACTIVATED: { text: 'This user was already removed.' },
  USER_NOT_FOUND: { text: 'This user no longer exists.' },
}

// The list is out of date when one of these comes back, so it is read again.
const STALE = new Set(['USER_NOT_FOUND', 'USER_ALREADY_DEACTIVATED'])

function messageFor(error) {
  if (ERRORS[error.code]) return ERRORS[error.code].text
  if (error.status === 403) return 'Only an admin can manage users.'
  return error.message || 'Something went wrong. Please try again.'
}

/* The same rules the server applies, checked here first so a typo is caught
   before anything is sent. The server checks again regardless. When editing,
   the password may stay empty: that keeps the current one. */
function validate({ fullName, phoneNumber, role, password }, editing) {
  const problems = {}
  if (!fullName.trim()) problems.fullName = ERRORS.NAME_REQUIRED.text
  if (!isValidPhone(phoneNumber)) problems.phoneNumber = ERRORS.PHONE_INVALID.text
  if (!role) problems.role = ERRORS.ROLE_INVALID.text
  if (!editing || password) {
    const weak = passwordProblem(password)
    if (weak) problems.password = weak
  }
  return problems
}

function ManageUsers() {
  const [search, setSearch] = useState('')
  // What the server is actually asked for: the search text, once typing pauses.
  const [query, setQuery] = useState('')
  const [role, setRole] = useState('')
  const [reloads, setReloads] = useState(0)
  const [result, setResult] = useState({ key: null, users: null, error: null })
  // What is open: null, { kind: 'add' }, { kind: 'edit', id } or { kind: 'remove', id }.
  const [open, setOpen] = useState(null)
  const [busyId, setBusyId] = useState(null)
  const [rowError, setRowError] = useState(null)
  const [notice, setNotice] = useState(null)

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

  const reload = () => setReloads((n) => n + 1)

  function openPanel(next) {
    setOpen(next)
    setRowError(null)
    setNotice(null)
  }

  async function handleAdd(body) {
    const user = await createUser(body)
    setOpen(null)
    setNotice(
      `${user.fullName} added as ${ROLE_LABEL[user.role] ?? user.role}. They choose their own password the first time they sign in.`,
    )
    reload()
  }

  async function handleEdit(user, body) {
    try {
      const saved = await updateUser(user.userId, body)
      setOpen(null)
      setNotice(
        body.password
          ? `${saved.fullName} saved. They must choose their own password the next time they sign in.`
          : `${saved.fullName} saved.`,
      )
      reload()
    } catch (e) {
      // Someone else changed or removed it meanwhile. The form is about to be
      // replaced by the refreshed row, so say so here rather than inside it.
      if (STALE.has(e.code)) {
        setOpen(null)
        setNotice(messageFor(e))
        reload()
        return
      }
      throw e
    }
  }

  async function handleRemove(user) {
    setBusyId(user.userId)
    setRowError(null)
    setNotice(null)
    try {
      await removeUser(user.userId)
      setOpen(null)
      setNotice(`${user.fullName} removed. They can no longer sign in.`)
      reload()
    } catch (e) {
      setOpen(null)
      setRowError({ id: user.userId, text: messageFor(e) })
      if (STALE.has(e.code)) reload()
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="sheet">
      <div className="sheet__head admin-services__head">
        <div>
          <h2>Users</h2>
          {users && (
            <p className="meta">
              {users.length} {users.length === 1 ? 'account' : 'accounts'}
              {filtered ? ' match' : ''}
            </p>
          )}
        </div>
        {open?.kind !== 'add' && (
          <button type="button" className="btn btn--live" onClick={() => openPanel({ kind: 'add' })}>
            Add user
          </button>
        )}
      </div>

      {open?.kind === 'add' && (
        <UserForm
          title="New staff account"
          submitLabel="Add user"
          busyLabel="Adding…"
          onSubmit={handleAdd}
          onCancel={() => setOpen(null)}
        />
      )}

      {notice && (
        <p className="admin-services__notice" role="status">
          {notice}
        </p>
      )}

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
          <button type="button" className="btn btn--quiet" onClick={reload}>
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
              <th className="admin-table__actions">Actions</th>
            </tr>
          </thead>
          <tbody>
            {users.map((u) =>
              open?.kind === 'edit' && open.id === u.userId ? (
                <tr key={u.userId} className="admin-table__editing">
                  <td colSpan={5}>
                    <UserForm
                      title={`Edit ${u.fullName}`}
                      user={u}
                      submitLabel="Save changes"
                      busyLabel="Saving…"
                      onSubmit={(body) => handleEdit(u, body)}
                      onCancel={() => setOpen(null)}
                    />
                  </td>
                </tr>
              ) : (
                <UserRow
                  key={u.userId}
                  user={u}
                  busy={busyId === u.userId}
                  confirming={open?.kind === 'remove' && open.id === u.userId}
                  error={rowError?.id === u.userId ? rowError.text : null}
                  onEdit={() => openPanel({ kind: 'edit', id: u.userId })}
                  onAskRemove={() => openPanel({ kind: 'remove', id: u.userId })}
                  onRemove={() => handleRemove(u)}
                  onKeep={() => setOpen(null)}
                />
              ),
            )}
          </tbody>
        </table>
      )}
    </div>
  )
}

function UserRow({ user, busy, confirming, error, onEdit, onAskRemove, onRemove, onKeep }) {
  return (
    <tr className={user.active ? undefined : 'is-inactive'}>
      <td data-label="Name">
        <span className="admin-table__name">{user.fullName}</span>
      </td>
      <td data-label="Phone">{user.phoneNumber}</td>
      <td data-label="Role">{ROLE_LABEL[user.role] ?? user.role}</td>
      <td data-label="Status">
        <Mark status={user.active ? 'live' : 'done'} label={user.active ? 'Active' : 'Removed'} />
      </td>
      <td className="admin-table__actions">
        {user.active &&
          (confirming ? (
            <div className="admin-table__confirm">
              <span>Remove {user.fullName}? They will be signed out and cannot sign in again.</span>
              <div className="admin-table__buttons">
                <button type="button" className="btn admin-btn admin-btn--miss" onClick={onRemove} disabled={busy}>
                  {busy ? 'Removing…' : 'Remove'}
                </button>
                <button type="button" className="btn btn--quiet admin-btn" onClick={onKeep} disabled={busy}>
                  Keep
                </button>
              </div>
            </div>
          ) : (
            <div className="admin-table__buttons">
              <button type="button" className="btn btn--quiet admin-btn" onClick={onEdit} disabled={busy}>
                Edit
              </button>
              <button
                type="button"
                className="btn btn--quiet admin-btn admin-btn--miss-text"
                onClick={onAskRemove}
                disabled={busy}
              >
                Remove
              </button>
            </div>
          ))}
        {error && (
          <p className="cell__note cell__note--error" role="alert">
            {error}
          </p>
        )}
      </td>
    </tr>
  )
}

/* One form for adding and for editing a staff account. Without `user` it adds;
   with one it edits, and sends only what changed. A password set here is
   temporary: the server flags the account so its owner must choose their own
   at next sign-in. */
function UserForm({ user, title, submitLabel, busyLabel, onSubmit, onCancel }) {
  const editing = Boolean(user)
  // A patient's role is fixed: only staff roles can be given or taken.
  const roleLocked = editing && user.role === ROLE.PATIENT

  const [values, setValues] = useState({
    fullName: user?.fullName ?? '',
    phoneNumber: user?.phoneNumber ?? '',
    role: user?.role ?? '',
    specialization: '',
    password: '',
  })
  const [touched, setTouched] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [serverError, setServerError] = useState(null)
  const [nothingChanged, setNothingChanged] = useState(false)

  const problems = validate(values, editing)
  const shown = touched ? problems : {}
  const fieldError = (field) =>
    shown[field] ??
    (serverError && ERRORS[serverError.code]?.field === field ? messageFor(serverError) : undefined)
  const formError = nothingChanged
    ? 'Nothing has changed yet.'
    : serverError && !ERRORS[serverError.code]?.field
      ? messageFor(serverError)
      : null

  const set = (field) => (e) => {
    setValues((v) => ({ ...v, [field]: e.target.value }))
    setServerError(null)
    setNothingChanged(false)
  }

  /* Adding sends everything. Editing sends only the fields that differ from
     the saved user, because the server changes only the fields it receives. */
  function buildBody() {
    const name = values.fullName.trim()
    if (!editing) {
      const body = {
        fullName: name,
        phoneNumber: values.phoneNumber.trim(),
        password: values.password,
        role: values.role,
      }
      // Only a doctor has a specialization.
      if (values.role === ROLE.DOCTOR && values.specialization.trim()) {
        body.specialization = values.specialization.trim()
      }
      return body
    }

    const body = {}
    if (name !== user.fullName) body.fullName = name
    if (normalisePhone(values.phoneNumber) !== user.phoneNumber) {
      body.phoneNumber = values.phoneNumber.trim()
    }
    if (!roleLocked && values.role !== user.role) body.role = values.role
    if (values.password) body.password = values.password
    return body
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setTouched(true)
    if (Object.keys(problems).length > 0 || submitting) return

    const body = buildBody()
    if (editing && Object.keys(body).length === 0) {
      setNothingChanged(true)
      return
    }

    setSubmitting(true)
    setServerError(null)
    try {
      await onSubmit(body)
    } catch (error) {
      setServerError(error)
      setSubmitting(false)
    }
  }

  return (
    <form className="sheet__body admin-form" onSubmit={handleSubmit} noValidate>
      <h3 className="admin-form__title">{title}</h3>
      <div className="cells admin-form__cells">
        <Cell
          label="Full name"
          autoFocus
          autoComplete="off"
          placeholder="For example, Dr. Maya Khoury"
          value={values.fullName}
          disabled={submitting}
          error={fieldError('fullName')}
          onChange={set('fullName')}
        />
        <Cell
          label="Role"
          placeholder="Choose a role"
          options={roleLocked ? ROLE_OPTIONS : STAFF_OPTIONS}
          value={values.role}
          disabled={submitting || roleLocked}
          error={fieldError('role')}
          onChange={set('role')}
        />
        <Cell
          label="Phone number"
          type="tel"
          inputMode="tel"
          autoComplete="off"
          placeholder="03 123456"
          value={values.phoneNumber}
          disabled={submitting}
          error={fieldError('phoneNumber')}
          onChange={set('phoneNumber')}
        />
        {!editing && values.role === ROLE.DOCTOR && (
          <Cell
            label="Specialization"
            optional
            autoComplete="off"
            placeholder="For example, Orthodontics"
            value={values.specialization}
            disabled={submitting}
            onChange={set('specialization')}
          />
        )}
        <Cell
          label={editing ? 'New temporary password' : 'Temporary password'}
          optional={editing}
          autoComplete="off"
          placeholder={editing ? 'Leave empty to keep the current one' : 'Share it with the user'}
          note={fieldError('password') ? undefined : PASSWORD_NOTE}
          value={values.password}
          disabled={submitting}
          error={fieldError('password')}
          onChange={set('password')}
        />
      </div>

      {formError && (
        <p className="form-error" role="alert">
          {formError}
        </p>
      )}

      <div className="admin-form__actions">
        <button type="submit" className="btn btn--live" disabled={submitting}>
          {submitting ? busyLabel : submitLabel}
        </button>
        <button type="button" className="btn btn--quiet" onClick={onCancel} disabled={submitting}>
          Cancel
        </button>
      </div>
    </form>
  )
}

export default ManageUsers