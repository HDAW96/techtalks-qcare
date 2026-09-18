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
import { normalisePhone } from '../phone'

/* Holds the signed-in user for the whole app.

   These calls now reach the real backend. VITE_USE_MOCK_AUTH=true signs in
   locally instead, which is only useful for looking at screens with the
   backend stopped — it accepts any password and stores nothing on a server. */

const USE_MOCK = import.meta.env.VITE_USE_MOCK_AUTH === 'true'

/* Mock mode only. */
const MOCK_USER = {
  userId: 12,
  patientId: 44,
  fullName: 'Hassan El Hussein',
  phoneNumber: '03000000',
  role: 'PATIENT',
}

/* Mock mode only. With the backend running, /auth/me says who a token belongs
   to. Without one, the signed-in user is stored next to the token so a reload
   restores the same person, and registered accounts are remembered by phone
   and name (never with a password) so signing out and back in finds the same
   record. */
const MOCK_SESSION_KEY = 'qcare.mockUser'
const MOCK_ACCOUNTS_KEY = 'qcare.mockAccounts'

function readStored(key) {
  try {
    return JSON.parse(localStorage.getItem(key))
  } catch {
    return null
  }
}

/* Mock accounts are keyed the way the real table is: phone plus name. */
function mockKey(phoneNumber, fullName) {
  return `${normalisePhone(phoneNumber)}|${fullName.trim().replace(/[ ]+/g, ' ').toLowerCase()}`
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

  /* Phone number and full name together, because a family shares one phone:
     the number alone does not say which of them is signing in. */
  const signIn = useCallback(
    async (phoneNumber, fullName, password) => {
      if (USE_MOCK) {
        const accounts = readStored(MOCK_ACCOUNTS_KEY) ?? {}
        const signedIn =
          accounts[mockKey(phoneNumber, fullName)] ?? { ...MOCK_USER, fullName, phoneNumber }
        startSession('mock-token', signedIn)
        return signedIn
      }
      const { token, user: signedIn } = await apiPost(
        '/auth/login',
        { phoneNumber, fullName, password },
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
        const key = mockKey(details.phoneNumber, details.fullName)
        if (accounts[key]) {
          throw new ApiError(
            'An account already exists for this name and phone number',
            409,
            'ACCOUNT_EXISTS',
          )
        }
        // Named fields only: `details` also carries the password, which must
        // never end up in app state or storage.
        const created = {
          ...MOCK_USER,
          fullName: details.fullName,
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

  /* The server answers with the updated user, so `mustChangePassword` clears
     here at once and RequireAuth stops sending them back to the form. The
     token stays valid: it names the user, not the password. */
  const changePassword = useCallback(async (currentPassword, newPassword) => {
    if (USE_MOCK) {
      setUser((u) => ({ ...u, mustChangePassword: false }))
      return
    }
    const updated = await apiPost('/auth/change-password', { currentPassword, newPassword })
    setUser(updated)
  }, [])

  const value = useMemo(
    () => ({ user, loading, signIn, register, signOut, changePassword, isMock: USE_MOCK }),
    [user, loading, signIn, register, signOut, changePassword],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
