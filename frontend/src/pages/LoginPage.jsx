import { useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import SiteHeader from '../components/SiteHeader'
import SiteFooter from '../components/SiteFooter'
import Cell from '../components/Cell'
import { useAuth } from '../auth/useAuth'
import { clinic } from '../data/mock'
import { isValidPhone } from '../phone'

/* Sign-in is phone number + full name + password. A family shares one phone
   number, so the number alone does not say which of them is signing in — the
   backend looks up the pair. */
function LoginPage() {
  const [phone, setPhone] = useState('')
  const [fullName, setFullName] = useState('')
  const [password, setPassword] = useState('')
  const [touched, setTouched] = useState({})
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState(null)

  const { signIn, isMock } = useAuth()
  // Carries where RequireAuth turned the user away from, so it survives a
  // detour through the register page too.
  const location = useLocation()

  const phoneError =
    touched.phone && !isValidPhone(phone)
      ? 'Enter a Lebanese number, for example 03 123456'
      : undefined

  const ready = isValidPhone(phone) && fullName.trim() !== '' && password !== ''

  async function handleSubmit(event) {
    event.preventDefault()
    if (!ready || submitting) return

    setSubmitting(true)
    setFormError(null)
    try {
      // No navigate here: once the user is set, RedirectIfSignedIn moves them
      // on to where they were headed.
      await signIn(phone.trim(), fullName.trim(), password)
    } catch (error) {
      setFormError(
        error.status === 401
          ? 'Those details do not match a record. Check the name is spelled as it was registered.'
          : error.message || 'Could not sign in. Please try again.',
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="page">
      <SiteHeader />

      <main className="auth">
        <div className="wrap wrap--narrow">
          <div className="sheet">
            <div className="sheet__head">
              <div>
                <p className="dash__name">{clinic.name}</p>
                <p className="meta">{clinic.area}</p>
              </div>
              <p className="meta meta--right">Patient access</p>
            </div>

            <div className="sheet__body">
              <div className="title-band">
                <h1>Sign in</h1>
                <p className="meta">Use the phone number and name on your record</p>
              </div>

              <form onSubmit={handleSubmit}>
                <div className="cells">
                  <Cell
                    label="Phone number"
                    type="tel"
                    autoComplete="tel"
                    placeholder="03 123456"
                    value={phone}
                    disabled={submitting}
                    error={phoneError}
                    onBlur={() => setTouched((t) => ({ ...t, phone: true }))}
                    onChange={(e) => setPhone(e.target.value)}
                  />
                  <Cell
                    label="Full name"
                    autoComplete="name"
                    placeholder="As written on your record"
                    value={fullName}
                    disabled={submitting}
                    onChange={(e) => setFullName(e.target.value)}
                  />
                  <Cell
                    label="Password"
                    type="password"
                    autoComplete="current-password"
                    value={password}
                    disabled={submitting}
                    onChange={(e) => setPassword(e.target.value)}
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
                    {submitting ? 'Signing in…' : 'Sign in'}
                  </button>
                </div>

                {isMock && (
                  <p className="meta" style={{ marginTop: 12 }}>
                    <span className="synthetic">Sample sign-in</span> The backend is
                    switched off, so any password is accepted.
                  </p>
                )}
              </form>

              <div className="auth__alt stack">
                <p>
                  No record yet?{' '}
                  <Link to="/register" state={location.state}>
                    Create one
                  </Link>
                  .
                </p>
                <p>
                  Waiting at the clinic? You don’t need an account —{' '}
                  <Link to="/#how">see how walk-in tickets work</Link>.
                </p>
              </div>
            </div>
          </div>
        </div>
      </main>

      <SiteFooter />
    </div>
  )
}

export default LoginPage
