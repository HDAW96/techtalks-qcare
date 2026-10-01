import { useCallback, useEffect, useState } from 'react'
import Cell from '../Cell'
import Mark from '../Mark'
import {
  USE_SAMPLE_SERVICES,
  createService,
  deleteService,
  listServices,
  updateService,
} from './servicesApi'

/* Manage Services (SCRUM-90): every treatment the clinic offers, with add,
   edit and deactivate.

   A service that has ever been booked or queued for cannot be deleted, only
   turned off, because old appointments still point at it. The server says
   which ones those are (`inUse`), so Delete is only offered where it can
   work. Turning a service off removes it from booking and the walk-in form;
   it stays here so it can be turned back on. */

const MAX_NAME = 100
const MAX_MINUTES = 480

/* What each backend error code means to the person at the screen. `field`
   puts the message under the input it is about. */
const ERRORS = {
  NAME_REQUIRED: { field: 'name', text: 'Give the service a name.' },
  NAME_TOO_LONG: { field: 'name', text: `Keep the name to ${MAX_NAME} characters or fewer.` },
  SERVICE_EXISTS: { field: 'name', text: 'There is already a service with this name.' },
  DURATION_INVALID: { field: 'minutes', text: `Enter whole minutes, from 1 to ${MAX_MINUTES}.` },
  SERVICE_NOT_FOUND: { text: 'This service no longer exists. The list has been refreshed.' },
  SERVICE_IN_USE: { text: 'This service has appointments, so it can only be deactivated.' },
}

function messageFor(error) {
  if (ERRORS[error.code]) return ERRORS[error.code].text
  if (error.status === 403) return 'Only an admin can change the clinic’s services.'
  return error.message || 'Something went wrong. Please try again.'
}

/* The same rules the server applies, checked here first so a typo is caught
   before anything is sent. The server checks again regardless. */
function validate(name, minutes) {
  const problems = {}
  const trimmed = name.trim()
  if (!trimmed) problems.name = ERRORS.NAME_REQUIRED.text
  else if (trimmed.length > MAX_NAME) problems.name = ERRORS.NAME_TOO_LONG.text
  const n = Number(minutes)
  if (minutes === '' || !Number.isInteger(n) || n < 1 || n > MAX_MINUTES) {
    problems.minutes = ERRORS.DURATION_INVALID.text
  }
  return problems
}

function ManageServices() {
  const [services, setServices] = useState(null)
  const [loadError, setLoadError] = useState(null)
  // What is open: null, { kind: 'add' }, { kind: 'edit', id } or { kind: 'delete', id }.
  const [open, setOpen] = useState(null)
  const [busyId, setBusyId] = useState(null)
  const [rowError, setRowError] = useState(null)
  const [notice, setNotice] = useState(null)

  const load = useCallback(() => {
    return listServices()
      .then((list) => {
        setServices(list)
        setLoadError(null)
      })
      .catch(setLoadError)
  }, [])

  useEffect(() => {
    load()
  }, [load])

  function openPanel(next) {
    setOpen(next)
    setRowError(null)
    setNotice(null)
  }

  // Replaces one row with what the server sent back, keeping name order.
  function putRow(row) {
    setServices((list) =>
      [...list.filter((s) => s.id !== row.id), row].sort((a, b) => a.name.localeCompare(b.name)),
    )
  }

  async function handleAdd(body) {
    const row = await createService(body)
    putRow(row)
    setOpen(null)
    setNotice(`${row.name} added. Patients can book it now.`)
  }

  async function handleEdit(id, body) {
    try {
      const row = await updateService(id, body)
      putRow(row)
      setOpen(null)
      setNotice(`${row.name} saved.`)
    } catch (error) {
      // Someone else removed it meanwhile. The row is about to vanish with
      // the refresh, so say so here rather than inside a form that is gone.
      if (error.code === 'SERVICE_NOT_FOUND') {
        setOpen(null)
        setNotice(ERRORS.SERVICE_NOT_FOUND.text)
        load()
        return
      }
      throw error
    }
  }

  async function runRowAction(service, action, done) {
    setBusyId(service.id)
    setRowError(null)
    setNotice(null)
    try {
      await action()
      setOpen(null)
      setNotice(done)
    } catch (error) {
      setOpen(null)
      setRowError({ id: service.id, text: messageFor(error) })
      // The list was out of date; show the truth rather than the old row.
      if (error.code === 'SERVICE_NOT_FOUND' || error.code === 'SERVICE_IN_USE') load()
    } finally {
      setBusyId(null)
    }
  }

  function toggleActive(service) {
    const active = !service.active
    runRowAction(
      service,
      async () => putRow(await updateService(service.id, { active })),
      active
        ? `${service.name} is active again. Patients can book it.`
        : `${service.name} deactivated. It no longer appears in booking or the walk-in form.`,
    )
  }

  function remove(service) {
    runRowAction(
      service,
      async () => {
        await deleteService(service.id)
        setServices((list) => list.filter((s) => s.id !== service.id))
      },
      `${service.name} deleted.`,
    )
  }

  const activeCount = services?.filter((s) => s.active).length ?? 0
  const inactiveCount = (services?.length ?? 0) - activeCount

  return (
    <div className="sheet admin-services">
      <div className="sheet__head admin-services__head">
        <div>
          <h2>Services</h2>
          {services && (
            <p className="meta">
              {activeCount} active · {inactiveCount} inactive
            </p>
          )}
        </div>
        {services && open?.kind !== 'add' && (
          <button type="button" className="btn btn--live" onClick={() => openPanel({ kind: 'add' })}>
            Add service
          </button>
        )}
      </div>

      {USE_SAMPLE_SERVICES && (
        <p className="admin-services__sample" role="note">
          Sample data. The services API (SCRUM-89) is not connected yet, so changes here are not saved.
        </p>
      )}

      {open?.kind === 'add' && (
        <ServiceForm
          title="New service"
          submitLabel="Add service"
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

      {loadError && (
        <div className="sheet__body" role="alert">
          <p className="form-error admin-services__load-error">{messageFor(loadError)}</p>
          <button type="button" className="btn btn--quiet" onClick={load}>
            Try again
          </button>
        </div>
      )}
      {!loadError && !services && (
        <div className="sheet__body">
          <p className="lede">Loading services…</p>
        </div>
      )}
      {services?.length === 0 && (
        <div className="sheet__body">
          <p className="lede">No services yet. Add the first treatment the clinic offers.</p>
        </div>
      )}

      {services?.length > 0 && (
        <table className="admin-table">
          <thead>
            <tr>
              <th>Service</th>
              <th>Duration</th>
              <th>Status</th>
              <th className="admin-table__actions">Actions</th>
            </tr>
          </thead>
          <tbody>
            {services.map((s) =>
              open?.kind === 'edit' && open.id === s.id ? (
                <tr key={s.id} className="admin-table__editing">
                  <td colSpan={4}>
                    <ServiceForm
                      title={`Edit ${s.name}`}
                      initial={s}
                      submitLabel="Save changes"
                      busyLabel="Saving…"
                      onSubmit={(body) => handleEdit(s.id, body)}
                      onCancel={() => setOpen(null)}
                    />
                  </td>
                </tr>
              ) : (
                <ServiceRow
                  key={s.id}
                  service={s}
                  busy={busyId === s.id}
                  confirming={open?.kind === 'delete' && open.id === s.id}
                  error={rowError?.id === s.id ? rowError.text : null}
                  onEdit={() => openPanel({ kind: 'edit', id: s.id })}
                  onToggle={() => toggleActive(s)}
                  onAskDelete={() => openPanel({ kind: 'delete', id: s.id })}
                  onDelete={() => remove(s)}
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

function ServiceRow({ service, busy, confirming, error, onEdit, onToggle, onAskDelete, onDelete, onKeep }) {
  return (
    <tr className={service.active ? undefined : 'is-inactive'}>
      <td data-label="Service">
        <span className="admin-table__name">{service.name}</span>
        {service.inUse && <p className="meta">Has appointments</p>}
      </td>
      <td data-label="Duration">{service.minutes} min</td>
      <td data-label="Status">
        <Mark status={service.active ? 'live' : 'done'} label={service.active ? 'Active' : 'Inactive'} />
      </td>
      <td className="admin-table__actions">
        {confirming ? (
          <div className="admin-table__confirm">
            <span>Delete {service.name} for good?</span>
            <div className="admin-table__buttons">
              <button type="button" className="btn admin-btn admin-btn--miss" onClick={onDelete} disabled={busy}>
                {busy ? 'Deleting…' : 'Delete'}
              </button>
              <button type="button" className="btn btn--quiet admin-btn" onClick={onKeep} disabled={busy}>
                Keep it
              </button>
            </div>
          </div>
        ) : (
          <div className="admin-table__buttons">
            <button type="button" className="btn btn--quiet admin-btn" onClick={onEdit} disabled={busy}>
              Edit
            </button>
            <button type="button" className="btn btn--quiet admin-btn" onClick={onToggle} disabled={busy}>
              {busy ? 'Saving…' : service.active ? 'Deactivate' : 'Reactivate'}
            </button>
            {!service.inUse && (
              <button type="button" className="btn btn--quiet admin-btn admin-btn--miss-text" onClick={onAskDelete} disabled={busy}>
                Delete
              </button>
            )}
          </div>
        )}
        {error && (
          <p className="cell__note cell__note--error" role="alert">
            {error}
          </p>
        )}
      </td>
    </tr>
  )
}

/* Add and edit are the same two fields, so they are one form. Only the
   fields that changed are sent on an edit. */
function ServiceForm({ title, initial, submitLabel, busyLabel, onSubmit, onCancel }) {
  const [name, setName] = useState(initial?.name ?? '')
  const [minutes, setMinutes] = useState(initial ? String(initial.minutes) : '')
  const [touched, setTouched] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [serverError, setServerError] = useState(null)

  const problems = validate(name, minutes)
  const shown = touched ? problems : {}
  const fieldError = (field) =>
    shown[field] ?? (serverError && ERRORS[serverError.code]?.field === field ? messageFor(serverError) : undefined)
  const formError = serverError && !ERRORS[serverError.code]?.field ? messageFor(serverError) : null

  async function handleSubmit(event) {
    event.preventDefault()
    setTouched(true)
    if (Object.keys(problems).length > 0 || submitting) return

    const body = { name: name.trim(), minutes: Number(minutes) }
    if (initial) {
      if (body.name === initial.name) delete body.name
      if (body.minutes === initial.minutes) delete body.minutes
      if (Object.keys(body).length === 0) {
        onCancel()
        return
      }
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
          label="Name"
          autoFocus
          autoComplete="off"
          placeholder="For example, Cleaning"
          maxLength={MAX_NAME + 20}
          value={name}
          disabled={submitting}
          error={fieldError('name')}
          onChange={(e) => {
            setName(e.target.value)
            setServerError(null)
          }}
        />
        <Cell
          label="Duration (minutes)"
          type="number"
          inputMode="numeric"
          min={1}
          max={MAX_MINUTES}
          step={1}
          placeholder="30"
          note={fieldError('minutes') ? undefined : 'Used to work out booking slots and waiting times.'}
          value={minutes}
          disabled={submitting}
          error={fieldError('minutes')}
          onChange={(e) => {
            setMinutes(e.target.value)
            setServerError(null)
          }}
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

export default ManageServices
