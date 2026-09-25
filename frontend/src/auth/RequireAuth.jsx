import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from './useAuth'
import SessionLoading from './SessionLoading'
import { HOME_ROUTE_FOR_ROLE } from '../constants'

/* Gates a route behind a signed-in user, and optionally behind a role.
   Sends visitors to /login and remembers where they were headed, so signing
   in returns them there instead of dumping them on the home page. A signed-in
   user of the wrong role goes to their own home (SCRUM-67). */
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

  // Signed in, but this area is not for them: send them to their own home.
  // Cannot loop - every role's home admits that role, and an unknown role
  // falls back to the public home page, which is not gated.
  if (roles && !roles.includes(user.role)) {
    return <Navigate to={HOME_ROUTE_FOR_ROLE[user.role] ?? '/'} replace />
  }

  return children
}

export default RequireAuth
