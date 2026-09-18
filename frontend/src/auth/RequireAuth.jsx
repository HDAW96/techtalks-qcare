import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from './useAuth'
import SessionLoading from './SessionLoading'
import SiteHeader from '../components/SiteHeader'
import SiteFooter from '../components/SiteFooter'

/* Gates a route behind a signed-in user, and optionally behind a role.
   Sends visitors to /login and remembers where they were headed, so signing
   in returns them there instead of dumping them on the home page. */
function RequireAuth({ children, roles }) {
  const { user, loading } = useAuth()
  const location = useLocation()

  if (loading) return <SessionLoading />

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location }} />
  }

  // An account still on its temporary password (the seeded staff) must
  // choose its own before it can open anything else.
  if (user.mustChangePassword && location.pathname !== '/change-password') {
    return <Navigate to="/change-password" replace />
  }

  // Signed in, but this area is not for them. Not a redirect: every role's
  // home is /dashboard until the doctor and receptionist screens exist, so
  // redirecting would loop.
  if (roles && !roles.includes(user.role)) {
    return (
      <div className="page">
        <SiteHeader />
        <main className="wrap">
          <div className="empty">
            <p>This page is not available for your account type yet.</p>
          </div>
        </main>
        <SiteFooter />
      </div>
    )
  }

  return children
}

export default RequireAuth
