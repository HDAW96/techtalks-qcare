import { useState } from 'react'
import { NavLink, Link } from 'react-router-dom'
import Logo from './Logo'
import { useAuth } from '../auth/useAuth'
import { HOME_ROUTE_FOR_ROLE, ROLE } from '../constants'

function SiteHeader() {
  const [open, setOpen] = useState(false)
  const { user, loading, signOut } = useAuth()
  // Until the stored session has been checked, nobody knows which links are
  // right. The signed-out ones hold their space invisibly instead of showing
  // "Sign in" to someone who is about to turn out to be signed in.
  const untilKnown = loading ? { visibility: 'hidden' } : undefined

  const close = () => setOpen(false)

  // One header for everyone, so the link has to say where it actually goes.
  const home = user ? (HOME_ROUTE_FOR_ROLE[user.role] ?? '/') : '/'
  const homeLabel = { [ROLE.PATIENT]: 'My Record', [ROLE.RECEPTIONIST]: 'Front Desk', [ROLE.DOCTOR]: 'Dashboard', [ROLE.ADMIN]: 'Admin Panel' }[user?.role]

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

        {user ?(
             <span className="head__who">
                Signed in as <strong>{user.fullName}</strong>
          </span> 
          ) : null
            }

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
              {homeLabel && (
                <NavLink to={home} onClick={close}>
                  {homeLabel}
                </NavLink>
              )}

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
