import { Link } from 'react-router-dom'
import { clinic } from '../data/mock'
import { useAuth } from '../auth/useAuth'
import { HOME_ROUTE_FOR_ROLE, ROLE } from '../constants'

function SiteFooter() {
  const { user, loading } = useAuth()
  const homeLabel = { [ROLE.PATIENT]: 'My record', [ROLE.RECEPTIONIST]: 'Front desk' }[user?.role]
  const untilKnown = loading ? { visibility: 'hidden' } : undefined

  return (
    <footer className="foot">
      <div className="wrap foot__inner">
        <div className="foot__col">
          <p className="foot__name">QCare</p>
          <p className="meta">Queue and appointments for dental clinics</p>
        </div>

        <div className="foot__col">
          <p className="meta">Clinic</p>
          <p>{clinic.name}</p>
          <p className="meta">{clinic.area}</p>
          <p className="meta">{clinic.hours}</p>
        </div>

        <div className="foot__col">
          <p className="meta">Contact</p>
          <p>
            <a href={`tel:${clinic.phone.replace(/\s/g, '')}`}>{clinic.phone}</a>
          </p>
        </div>

        {/* Same sign-in rule as the header: the links follow the session. */}
        <nav className="foot__col" aria-label="Footer">
          <p className="meta">Pages</p>
          <Link to="/">Home</Link>
          {user ? (
            <>
              {/* The same role-aware link as the header: a receptionist has no
                  patient record, and a doctor has no screen yet. */}
              {homeLabel && <Link to={HOME_ROUTE_FOR_ROLE[user.role]}>{homeLabel}</Link>}
              <Link to="/change-password">Change password</Link>
            </>
          ) : (
            <>
              <Link to="/login" style={untilKnown}>
                Sign in
              </Link>
              <Link to="/register" style={untilKnown}>
                Create a record
              </Link>
            </>
          )}
        </nav>
      </div>

      <div className="wrap foot__base">
        <p className="meta">
          Sample clinic details — QCare is a student project in development
        </p>
      </div>
    </footer>
  )
}

export default SiteFooter
