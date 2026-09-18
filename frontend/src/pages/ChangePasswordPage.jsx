import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import SiteHeader from '../components/SiteHeader'
import SiteFooter from '../components/SiteFooter'
import Cell from '../components/Cell'
import { useAuth } from '../auth/useAuth'
import { clinic } from '../data/mock'
import { HOME_ROUTE_FOR_ROLE } from '../constants'
import { PASSWORD_NOTE, passwordProblem } from '../password'

/* Choosing a new password. Anyone signed in can open it; the seeded staff
   accounts are sent here by RequireAuth until they have chosen their own,
   because theirs is a known development password. */
function ChangePasswordPage() {
  const { user, changePassword } = useAuth()
  const navigate = useNavigate()
  const forced = user.mustChangePassword

  const [form, setForm] = useState({ current: '', next: '', confirm: '' })
  const [touched, setTouched] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState(null)

  const set = (key) => (e) => {
    const { value } = e.target
    setForm((f) => ({ ...f, [key]: value }))
  }

  const errors = {
    next: (touched && passwordProblem(form.next)) || undefined,
    confirm:
      form.confirm !== '' && form.confirm !== form.next
        ? 'The two passwords do not match yet'
        : undefined,
  }

  const ready =
    form.current !== '' && passwordProblem(form.next) === null && form.confirm === form.next

  async function handleSubmit(event) {
    event.preventDefault()
    if (!ready || submitting) return

    setSubmitting(true)
    setFormError(null)
    try {
      await changePassword(form.current, form.next)
      navigate(HOME_ROUTE_FOR_ROLE[user.role] ?? '/', { replace: true })
    } catch (error) {
      setFormError(error.message || 'Could not change your password. Please try again.')
      setSubmitting(false)
    }
  }

  return (
    <div className="page">
      <SiteHeader />

      <main className="auth">
        <div className="wrap" style={{ maxWidth: 760 }}>
          <div className="sheet">
            <div className="sheet__head">
              <div>
                <p className="dash__name">{clinic.name}</p>
                <p className="meta">{user.fullName}</p>
              </div>
              <p className="meta meta--right">Password</p>
            </div>

            <div className="sheet__body">
              <div className="title-band">
                <h1>{forced ? 'Choose your own password' : 'Change your password'}</h1>
                <p className="meta">
                  {forced
                    ? 'This account was set up with a temporary password. Choose your own before continuing.'
                    : 'You will sign in with the new password from now on'}
                </p>
              </div>

              <form onSubmit={handleSubmit} className="stack">
                <div className="cells cells--two">
                  <Cell
                    label="Current password"
                    type="password"
                    autoComplete="current-password"
                    value={form.current}
                    disabled={submitting}
                    onChange={set('current')}
                  />
                </div>

                <div className="cells cells--two">
                  <Cell
                    label="New password"
                    type="password"
                    autoComplete="new-password"
                    note={PASSWORD_NOTE}
                    value={form.next}
                    disabled={submitting}
                    error={errors.next}
                    onBlur={() => setTouched(true)}
                    onChange={set('next')}
                  />
                  <Cell
                    label="Confirm new password"
                    type="password"
                    autoComplete="new-password"
                    value={form.confirm}
                    disabled={submitting}
                    error={errors.confirm}
                    onChange={set('confirm')}
                  />
                </div>

                {formError && (
                  <p className="form-error" role="alert">
                    {formError}
                  </p>
                )}

                <div className="auth__actions">
                  <button
                    type="submit"
                    className="btn btn--live btn--block"
                    disabled={!ready || submitting}
                  >
                    {submitting ? 'Saving…' : 'Save new password'}
                  </button>
                  {/* A flagged account has nowhere else to go until this is
                      done, so the way out is only offered to everyone else. */}
                  {!forced && (
                    <Link to={HOME_ROUTE_FOR_ROLE[user.role] ?? '/'} className="btn btn--quiet btn--block">
                      Keep my current password
                    </Link>
                  )}
                </div>
              </form>
            </div>
          </div>
        </div>
      </main>

      <SiteFooter />
    </div>
  )
}

export default ChangePasswordPage
