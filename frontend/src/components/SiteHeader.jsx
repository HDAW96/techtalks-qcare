import { useState } from 'react'
import { NavLink, Link } from 'react-router-dom'
import Logo from './Logo'
import { useAuth } from '../auth/useAuth'

function SiteHeader() {
  const [open, setOpen] = useState(false)
  const { user, loading, signOut } = useAuth()
  // Until the stored session has been checked, nobody knows which links are
  // right. The signed-out ones hold their space invisibly instead of showing
  // "Sign in" to someone who is about to turn out to be signed in.
  const untilKnown = loading ? { visibility: 'hidden' } : undefined

  const close = () => setOpen(false)

  function handleSignOut() {
    close()
    signOut()
    // No navigation here on purpose: clearing the user makes RequireAuth
    // redirect off any protected route to /login by itself. From a public
    // page the user simply stays where they are, which is correct.
  }

  return (
    <header className="head">
      <div className="wrap head__inner">
        <Logo />

        <button
          type="button"
          className="head__toggle"
          aria-expanded={open}
          aria-controls="site-nav"
          onClick={() => setOpen((v) => !v)}
        >
          {open ? 'Close' : 'Menu'}
        </button>

        <nav
          id="site-nav"
          className={`head__nav${open ? ' is-open' : ''}`}
          aria-label="Main"
        >
          <NavLink to="/" end onClick={close}>
            Home
          </NavLink>
          {/* A plain `href="#how"` anchor only works while already on the
              home page — from anywhere else it just appends the hash to the
              current URL instead of navigating there. Routing to "/#how"
              always lands on the home page first; GuestHomePage scrolls to
              the section once it mounts. */}
          <Link to="/#how" onClick={close}>
            How it works
          </Link>

          {user ? (
            <>
              <NavLink to="/dashboard" onClick={close}>
                My record
              </NavLink>
              <button type="button" className="btn btn--quiet" onClick={handleSignOut}>
                Sign out
              </button>
            </>
          ) : (
            <>
              <NavLink to="/login" onClick={close} style={untilKnown}>
                Sign in
              </NavLink>
              <Link
                to="/register"
                className="btn btn--live"
                onClick={close}
                style={untilKnown}
              >
                Create a record
              </Link>
            </>
          )}
        </nav>
      </div>
    </header>
  )
}

export default SiteHeader
