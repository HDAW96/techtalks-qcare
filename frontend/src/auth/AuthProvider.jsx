import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  ApiError,
  apiGet,
  apiPost,
  getToken,
  setToken,
  setUnauthorizedHandler,
} from '../api/client'
import { AuthContext } from './context'

/* Holds the signed-in user for the whole app.
   Phase 1 note: the endpoints below do not exist yet. Until they do,
   VITE_USE_MOCK_AUTH=true (the default in development) signs in locally so
   the dashboard stays reviewable. Flip it off and the exact same calls go to
   the real backend — no component changes. */

const USE_MOCK = import.meta.env.VITE_USE_MOCK_AUTH !== 'false'

/* The sample patient. patientId 44 is the patient who owns the sample queue
   ticket and appointments in data/mock.js. */
const MOCK_USER = {
  userId: 12,
  patientId: 44,
  fullName: 'Hassan El Hussein',
  email: 'hassan@example.com',
  phoneNumber: '+961 70 000 000',
  role: 'PATIENT',
}

/* Mock mode only. With a backend, /auth/me says who a token belongs to.
   Without one, the signed-in user is stored next to the token so a reload
   restores the same person, and registered accounts are remembered by email
   (never with a password) so signing out and back in finds the same record. */
const MOCK_SESSION_KEY = 'qcare.mockUser'
const MOCK_ACCOUNTS_KEY = 'qcare.mockAccounts'

function readStored(key) {
  try {
    return JSON.parse(localStorage.getItem(key))
  } catch {
    return null
  }
}

function writeStored(key, value) {
  try {
    if (value == null) localStorage.removeItem(key)
    else localStorage.setItem(key, JSON.stringify(value))
  } catch {
    // Not fatal: the mock session simply will not survive a reload.
  }
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  // Starts true so the app does not flash the signed-out state while the
  // stored token is still being checked.
  const [loading, setLoading] = useState(true)

  /* Bumped by every sign-in, register and sign-out. A session check that
     started before one of those is out of date and must not overwrite it. */
  const session = useRef(0)

  const startSession = useCallback((token, nextUser) => {
    session.current += 1
    setToken(token)
    if (USE_MOCK) writeStored(MOCK_SESSION_KEY, nextUser)
    setUser(nextUser)
    setLoading(false)
  }, [])

  const signOut = useCallback(() => {
    session.current += 1
    setToken(null)
    writeStored(MOCK_SESSION_KEY, null)
    setUser(null)
  }, [])

  /* Restore the session on load. A token in storage only means "we had one" —
     it may be expired, so the backend has the final say via /auth/me. */
  useEffect(() => {
    let cancelled = false
    const startedAt = session.current
    const stillCurrent = () => !cancelled && session.current === startedAt

    async function restore() {
      const token = getToken()
      if (!token) {
        setLoading(false)
        return
      }

      if (USE_MOCK) {
        if (stillCurrent()) {
          setUser(readStored(MOCK_SESSION_KEY) ?? MOCK_USER)
          setLoading(false)
        }
        return
      }

      try {
        const me = await apiGet('/auth/me')
        if (stillCurrent()) setUser(me)
      } catch {
        // Invalid or expired token; client.js has already cleared it.
        if (stillCurrent()) setUser(null)
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
    setUnauthorizedHandler(() => {
      session.current += 1
      setUser(null)
    })
    return () => setUnauthorizedHandler(null)
  }, [])

  const signIn = useCallback(
    async (email, password) => {
      if (USE_MOCK) {
        const accounts = readStored(MOCK_ACCOUNTS_KEY) ?? {}
        const signedIn = accounts[email.toLowerCase()] ?? { ...MOCK_USER, email }
        startSession('mock-token', signedIn)
        return signedIn
      }
      const { token, user: signedIn } = await apiPost(
        '/auth/login',
        { email, password },
        { auth: false },
      )
      startSession(token, signedIn)
      return signedIn
    },
    [startSession],
  )

  const register = useCallback(
    async (details) => {
      if (USE_MOCK) {
        const accounts = readStored(MOCK_ACCOUNTS_KEY) ?? {}
        const key = details.email.toLowerCase()
        if (accounts[key]) {
          throw new ApiError('Email already registered', 409, 'EMAIL_TAKEN')
        }
        // Named fields only: `details` also carries the password, which must
        // never end up in app state or storage.
        const created = {
          ...MOCK_USER,
          fullName: details.fullName,
          email: details.email,
          phoneNumber: details.phoneNumber,
        }
        writeStored(MOCK_ACCOUNTS_KEY, { ...accounts, [key]: created })
        startSession('mock-token', created)
        return created
      }
      const { token, user: created } = await apiPost('/auth/register', details, {
        auth: false,
      })
      startSession(token, created)
      return created
    },
    [startSession],
  )

  const value = useMemo(
    () => ({ user, loading, signIn, register, signOut, isMock: USE_MOCK }),
    [user, loading, signIn, register, signOut],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
