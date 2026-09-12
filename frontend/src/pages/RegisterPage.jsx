import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import SiteHeader from '../components/SiteHeader'
import SiteFooter from '../components/SiteFooter'
import Cell from '../components/Cell'
import { useAuth } from '../auth/useAuth'
import { clinic } from '../data/mock'

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
// Deliberately loose: Lebanese numbers get written many ways (+961, 03, 70…),
// so this only rejects input that clearly is not a phone number.
const PHONE_PATTERN = /^[+\d][\d\s-]{6,}$/
const MIN_PASSWORD = 8

/* Email is the primary identifier — the USERS table has email + password and
   no phone column, so email is what the backend checks for sign-in and for
   duplicate accounts. Phone still goes on the record (PATIENT.phone_number)
   but is not the login key. */
function RegisterPage() {
  const [form, setForm] = useState({
    name: '',
    email: '',
    phone: '',
    password: '',
    confirm: '',
  })
  const [touched, setTouched] = useState({})
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState(null)

  const { register } = useAuth()
  const navigate = useNavigate()

  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value })
  const blur = (key) => () => setTouched((t) => ({ ...t, [key]: true }))

  const errors = {
    name:
      touched.name && form.name.trim() === '' ? 'Enter your full name' : undefined,
    email:
      touched.email && !EMAIL_PATTERN.test(form.email.trim())
        ? 'Enter a valid email address'
        : undefined,
    phone:
      touched.phone && form.phone.trim() !== '' && !PHONE_PATTERN.test(form.phone.trim())
        ? 'Enter a valid phone number'
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
    EMAIL_PATTERN.test(form.email.trim()) &&
    form.password.length >= MIN_PASSWORD &&
    form.confirm === form.password &&
    (form.phone.trim() === '' || PHONE_PATTERN.test(form.phone.trim()))

  async function handleSubmit(event) {
    event.preventDefault()
    if (!ready || submitting) return

    setSubmitting(true)
    setFormError(null)
    try {
      await register({
        fullName: form.name.trim(),
        email: form.email.trim(),
        phoneNumber: form.phone.trim() || null,
        password: form.password,
      })
      navigate('/dashboard', { replace: true })
    } catch (error) {
      setFormError(
        error.code === 'EMAIL_TAKEN'
          ? 'An account with this email already exists. Try signing in instead.'
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
                    label="Email address"
                    type="email"
                    autoComplete="email"
                    placeholder="you@example.com"
                    note="Used to sign in"
                    value={form.email}
                    disabled={submitting}
                    error={errors.email}
                    onBlur={blur('email')}
                    onChange={set('email')}
                  />
                </div>

                <div className="cells">
                  <Cell
                    label="Phone number"
                    type="tel"
                    optional
                    autoComplete="tel"
                    placeholder="+961 …"
                    note="So the desk can reach you if the schedule shifts"
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
                    note={`At least ${MIN_PASSWORD} characters`}
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
                  Already registered? <Link to="/login">Sign in</Link>.
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
