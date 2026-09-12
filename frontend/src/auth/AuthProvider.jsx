import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  apiGet,
  apiPost,
  getToken,
  setToken,
  setUnauthorizedHandler,
} from '../api/client'
import { AuthContext } from './context'

/* Holds the signed-in user for the whole app.
   Phase 1 note: the endpoints below do not exist yet. Until they do,
   VITE_USE_MOCK_AUTH=true (the default in development) signs in a sample
   patient locally so the dashboard stays reviewable. Flip it off and the
   exact same calls go to the real backend — no component changes. */

const USE_MOCK = import.meta.env.VITE_USE_MOCK_AUTH !== 'false'

const MOCK_USER = {
  userId: 12,
  patientId: 44,
  fullName: 'Hassan El Hussein',
  email: 'hassan@example.com',
  role: 'PATIENT',
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  // Starts true so the app does not flash the signed-out state while the
  // stored token is still being checked.
  const [loading, setLoading] = useState(true)

  const signOut = useCallback(() => {
    setToken(null)
    setUser(null)
  }, [])

  /* Restore the session on load. A token in storage only means "we had one" —
     it may be expired, so the backend has the final say via /auth/me. */
  useEffect(() => {
    let cancelled = false

    async function restore() {
      const token = getToken()
      if (!token) {
        setLoading(false)
        return
      }

      if (USE_MOCK) {
        if (!cancelled) {
          setUser(MOCK_USER)
          setLoading(false)
        }
        return
      }

      try {
        const me = await apiGet('/auth/me')
        if (!cancelled) setUser(me)
      } catch {
        // Invalid or expired token; client.js has already cleared it.
        if (!cancelled) setUser(null)
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    restore()
    return () => {
      cancelled = true
    }
  }, [])

  /* An expired token mid-session clears the user wherever it happens. */
  useEffect(() => {
    setUnauthorizedHandler(() => setUser(null))
    return () => setUnauthorizedHandler(null)
  }, [])

  const signIn = useCallback(async (email, password) => {
    if (USE_MOCK) {
      setToken('mock-token')
      setUser(MOCK_USER)
      return MOCK_USER
    }
    const { token, user: signedIn } = await apiPost('/auth/login', { email, password })
    setToken(token)
    setUser(signedIn)
    return signedIn
  }, [])

  const register = useCallback(async (details) => {
    if (USE_MOCK) {
      setToken('mock-token')
      const created = { ...MOCK_USER, ...details }
      setUser(created)
      return created
    }
    const { token, user: created } = await apiPost('/auth/register', details)
    setToken(token)
    setUser(created)
    return created
  }, [])

  const value = useMemo(
    () => ({ user, loading, signIn, register, signOut, isMock: USE_MOCK }),
    [user, loading, signIn, register, signOut],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
