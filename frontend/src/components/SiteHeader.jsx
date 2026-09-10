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
          <a href="#how" onClick={() => setOpen(false)}>
            How it works
          </a>
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
