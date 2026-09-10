import { useState } from 'react'
import { Link } from 'react-router-dom'
import SiteHeader from '../components/SiteHeader'
import SiteFooter from '../components/SiteFooter'
import Cell from '../components/Cell'
import { clinic } from '../data/mock'

/* Phase 1: layout only. The submit handler is deliberately inert until the
   backend exposes an auth endpoint.
   Login is by email: the ERD's USERS table has email + password and no
   phone column, so email is the only identifier the backend can check. */
function LoginPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  const ready = email.trim() !== '' && password !== ''

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
                <p className="meta">Use the email on your record</p>
              </div>

              <form onSubmit={(e) => e.preventDefault()}>
                <div className="cells">
                  <Cell
                    label="Email address"
                    type="email"
                    autoComplete="email"
                    placeholder="you@example.com"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                  />
                  <Cell
                    label="Password"
                    type="password"
                    autoComplete="current-password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                  />
                </div>

                <div className="auth__actions">
                  <button
                    type="submit"
                    className="btn btn--live btn--block"
                    disabled={!ready}
                  >
                    Sign in
                  </button>
                  <Link to="/dashboard" className="btn btn--quiet btn--block">
                    Preview the dashboard
                  </Link>
                </div>
              </form>

              <div className="auth__alt stack">
                <p>
                  No record yet? <Link to="/register">Create one</Link>.
                </p>
                <p>
                  Waiting at the clinic? You don’t need an account —{' '}
                  <Link to="/">check your ticket</Link> with the number on it.
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
