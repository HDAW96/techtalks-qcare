import { useState } from 'react'
import { NavLink, Link } from 'react-router-dom'
import Logo from './Logo'

function SiteHeader() {
  const [open, setOpen] = useState(false)

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
          <NavLink to="/" end onClick={() => setOpen(false)}>
            Home
          </NavLink>
          {/* A plain `href="#how"` anchor only works while already on the
              home page — from anywhere else it just appends the hash to the
              current URL instead of navigating there. Routing to "/#how"
              always lands on the home page first; GuestHomePage scrolls to
              the section once it mounts. */}
          <Link to="/#how" onClick={() => setOpen(false)}>
            How it works
          </Link>
          {/* Phase 1 only: a reviewer's shortcut into the dashboard mockup
              while there is no real login. Remove once auth is wired up. */}
          <NavLink to="/dashboard" onClick={() => setOpen(false)}>
            Preview dashboard
          </NavLink>
          <NavLink to="/login" onClick={() => setOpen(false)}>
            Sign in
          </NavLink>
          <Link
            to="/register"
            className="btn btn--live"
            onClick={() => setOpen(false)}
          >
            Create a record
          </Link>
        </nav>
      </div>
    </header>
  )
}

export default SiteHeader
