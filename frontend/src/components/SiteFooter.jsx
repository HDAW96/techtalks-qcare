import { Link } from 'react-router-dom'
import { clinic } from '../data/mock'

function SiteFooter() {
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

        <nav className="foot__col" aria-label="Footer">
          <p className="meta">Pages</p>
          <Link to="/">Home</Link>
          <Link to="/login">Sign in</Link>
          <Link to="/register">Create a record</Link>
          <Link to="/dashboard">Dashboard</Link>
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
