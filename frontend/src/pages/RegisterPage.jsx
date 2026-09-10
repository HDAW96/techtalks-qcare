import { useState } from 'react'
import { Link } from 'react-router-dom'
import SiteHeader from '../components/SiteHeader'
import SiteFooter from '../components/SiteFooter'
import Cell from '../components/Cell'
import { clinic } from '../data/mock'

/* Registration is the clinic's intake sheet. Phone is the primary identifier
   because that is what clinics here actually record; email is optional.
   Phase 1: the only validation is the local password match, so the error state
   is visible to review. Nothing is submitted anywhere yet. */
function RegisterPage() {
  const [form, setForm] = useState({
    name: '',
    phone: '',
    email: '',
    password: '',
    confirm: '',
  })

  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value })

  const mismatch = form.confirm !== '' && form.confirm !== form.password
  const ready =
    form.name.trim() !== '' &&
    form.phone.trim() !== '' &&
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
                    label="Phone number"
                    type="tel"
                    autoComplete="tel"
                    placeholder="+961 …"
                    note="Used to find your record and to call you if the schedule shifts"
                    value={form.phone}
                    onChange={set('phone')}
                  />
                </div>

                <div className="cells">
                  <Cell
                    label="Email address"
                    type="email"
                    optional
                    autoComplete="email"
                    value={form.email}
                    onChange={set('email')}
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
