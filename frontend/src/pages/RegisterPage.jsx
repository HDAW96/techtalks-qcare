import { useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import SiteHeader from '../components/SiteHeader'
import SiteFooter from '../components/SiteFooter'
import Cell from '../components/Cell'
import { useAuth } from '../auth/useAuth'
import { clinic } from '../data/mock'
import { isValidPhone } from '../phone'

const MIN_PASSWORD = 8

/* An account is a phone number, a full name and a password. There is no email:
   the USERS table does not have one. The name is part of the identity rather
   than a label, because a family shares one phone number and each of them gets
   their own record. */
function RegisterPage() {
  const [form, setForm] = useState({
    name: '',
    phone: '',
    password: '',
    confirm: '',
  })
  const [touched, setTouched] = useState({})
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState(null)

  const { register } = useAuth()
  const location = useLocation()

  const set = (key) => (e) => {
    const { value } = e.target
    setForm((f) => ({ ...f, [key]: value }))
  }
  const blur = (key) => () => setTouched((t) => ({ ...t, [key]: true }))

  const errors = {
    name:
      touched.name && form.name.trim() === '' ? 'Enter your full name' : undefined,
    phone:
      touched.phone && !isValidPhone(form.phone)
        ? 'Enter a Lebanese number, for example 03 123456'
        : undefined,
    password:
      touched.password && form.password.length < MIN_PASSWORD
        ? `At least ${MIN_PASSWORD} characters`
        : undefined,
    confirm:
      form.confirm !== '' && form.confirm !== form.password
        ? 'The two passwords do not match yet'
        : undefined,
  }

  const ready =
    form.name.trim() !== '' &&
    isValidPhone(form.phone) &&
    form.password.length >= MIN_PASSWORD &&
    form.confirm === form.password

  async function handleSubmit(event) {
    event.preventDefault()
    if (!ready || submitting) return

    setSubmitting(true)
    setFormError(null)
    try {
      // No navigate here: once the user is set, RedirectIfSignedIn moves them
      // on to where they were headed.
      await register({
        fullName: form.name.trim(),
        phoneNumber: form.phone.trim(),
        password: form.password,
      })
    } catch (error) {
      setFormError(
        error.code === 'ACCOUNT_EXISTS'
          ? 'An account already exists for this name and phone number. Try signing in instead.'
          : error.message || 'Could not create your record. Please try again.',
      )
    } finally {
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
                <p className="meta">{clinic.area}</p>
              </div>
              <p className="meta meta--right">New patient record</p>
            </div>

            <div className="sheet__body">
              <div className="title-band">
                <h1>Create your record</h1>
                <p className="meta">
                  The clinic uses these details to identify you at the desk
                </p>
              </div>

              <form onSubmit={handleSubmit} className="stack">
                <div className="cells cells--two">
                  <Cell
                    label="Full name"
                    autoComplete="name"
                    value={form.name}
                    disabled={submitting}
                    error={errors.name}
                    onBlur={blur('name')}
                    onChange={set('name')}
                  />
                  <Cell
                    label="Phone number"
                    type="tel"
                    autoComplete="tel"
                    placeholder="03 123456"
                    note="You sign in with this and your name"
                    value={form.phone}
                    disabled={submitting}
                    error={errors.phone}
                    onBlur={blur('phone')}
                    onChange={set('phone')}
                  />
                </div>

                <div className="cells cells--two">
                  <Cell
                    label="Password"
                    type="password"
                    autoComplete="new-password"
                    note={`At least ${MIN_PASSWORD} characters including 1 uppercase, 1 lowercase and 1 special character.`}
                    value={form.password}
                    disabled={submitting}
                    error={errors.password}
                    onBlur={blur('password')}
                    onChange={set('password')}
                  />
                  <Cell
                    label="Confirm password"
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
                    {submitting ? 'Creating record…' : 'Create record'}
                  </button>
                </div>
              </form>

              <div className="auth__alt">
                <p>
                  Already registered?{' '}
                  <Link to="/login" state={location.state}>
                    Sign in
                  </Link>
                  .
                </p>
              </div>
            </div>

            <div className="sheet__foot">
              <p className="meta">
                Your details stay with the clinic and are not shared
              </p>
            </div>
          </div>
        </div>
      </main>

      <SiteFooter />
    </div>
  )
}

export default RegisterPage
