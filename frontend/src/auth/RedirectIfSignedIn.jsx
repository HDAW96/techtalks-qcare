import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from './useAuth'
import SessionLoading from './SessionLoading'
import { destinationAfterSignIn } from './destination'

/* The mirror of RequireAuth, for /login and /register: a signed-in user is
   sent on to where they were headed. This is also what moves the user on
   after a successful sign-in, so the forms themselves never navigate. */
function RedirectIfSignedIn({ children }) {
  const { user, loading } = useAuth()
  const location = useLocation()

  if (loading) return <SessionLoading />

  if (user) {
    return (
      <Navigate to={destinationAfterSignIn(user, location.state?.from)} replace />
    )
  }

  return children
}

export default RedirectIfSignedIn
