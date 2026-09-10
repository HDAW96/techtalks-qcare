import { useState } from 'react'
import { Link } from 'react-router-dom'
import SiteHeader from '../components/SiteHeader'
import SiteFooter from '../components/SiteFooter'
import Cell from '../components/Cell'
import { clinic } from '../data/mock'

/* Registration is the clinic's intake sheet. Email is the primary identifier —
   the ERD's USERS table has email + password and no phone column, so email is
   what the backend will check for sign-in and duplicate accounts. Phone still
   goes on the record (PATIENT.phone_number), but it is not the login key.
   Phase 1: the only validation is the local password match, so the error state
   is visible to review. Nothing is submitted anywhere yet. */
function RegisterPage() {
  const [form, setForm] = useState({
    name: '',
    email: '',
    phone: '',
    password: '',
    confirm: '',
  })

  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value })

  const mismatch = form.confirm !== '' && form.confirm !== form.password
  const ready =
    form.name.trim() !== '' &&
    form.email.trim() !== '' &&
    form.password !== '' &&
    !mismatch &&
    form.confirm !== ''

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

              <form onSubmit={(e) => e.preventDefault()} className="stack">
                <div className="cells cells--two">
                  <Cell
                    label="Full name"
                    autoComplete="name"
                    value={form.name}
                    onChange={set('name')}
                  />
                  <Cell
                    label="Email address"
                    type="email"
                    autoComplete="email"
                    placeholder="you@example.com"
                    note="Used to sign in"
                    value={form.email}
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
                    onChange={set('phone')}
                  />
                </div>

                <div className="cells cells--two">
                  <Cell
                    label="Password"
                    type="password"
                    autoComplete="new-password"
                    note="At least 8 characters"
                    value={form.password}
                    onChange={set('password')}
                  />
                  <Cell
                    label="Confirm password"
                    type="password"
                    autoComplete="new-password"
                    value={form.confirm}
                    onChange={set('confirm')}
                    error={
                      mismatch ? 'The two passwords do not match yet' : undefined
                    }
                  />
                </div>

                <div className="auth__actions">
                  <button
                    type="submit"
                    className="btn btn--live btn--block"
                    disabled={!ready}
                  >
                    Create record
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
