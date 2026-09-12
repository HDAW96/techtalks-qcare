import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from './useAuth'

/* Gates a route behind a signed-in user.
   Sends visitors to /login and remembers where they were headed, so signing
   in returns them there instead of dumping them on the home page. */
function RequireAuth({ children }) {
  const { user, loading } = useAuth()
  const location = useLocation()

  // Still checking the stored token — rendering either branch now would
  // briefly flash the wrong one.
  if (loading) {
    return (
      <div className="page">
        <div className="empty" role="status">
          <p>Loading…</p>
        </div>
      </div>
    )
  }

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location }} />
  }

  return children
}

export default RequireAuth
