/* The single place the frontend talks to the backend.
   Everything about how a request is authenticated lives here, so switching
   from a bearer token to session cookies later means changing this file and
   nothing else. */

const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'

const TOKEN_KEY = 'qcare.token'

const REQUEST_TIMEOUT_MS = 15000

export function getToken() {
  try {
    return localStorage.getItem(TOKEN_KEY)
  } catch {
    // Private browsing and blocked site data both throw on access.
    return null
  }
}

export function setToken(token) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token)
    else localStorage.removeItem(TOKEN_KEY)
  } catch {
    // Not fatal: the session simply will not survive a reload.
  }
}

/* Thrown for any non-2xx response. `status` lets callers react to specific
   cases (409 already-taken, 401 expired) and `code` carries the backend's
   machine-readable error string when it sends one. */
export class ApiError extends Error {
  constructor(message, status, code) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

let onUnauthorized = null

/* The app registers a callback here so an expired token can clear the session
   and bounce the user to sign-in, without this module importing React. */
export function setUnauthorizedHandler(handler) {
  onUnauthorized = handler
}

let onPasswordChangeRequired = null

/* The server refuses everything but /auth/me and /auth/change-password from an
   account still on a temporary password (403 PASSWORD_CHANGE_REQUIRED). That
   can start mid-session, when an admin resets the password, so the app
   registers a callback here to send the user to the change-password screen. */
export function setPasswordChangeRequiredHandler(handler) {
  onPasswordChangeRequired = handler
}

/* `auth: false` is for sign-in and register: they must not carry an old token,
   and a 401 from them means wrong credentials, not an expired session. */
async function request(method, path, body, { auth = true } = {}) {
  const token = auth ? getToken() : null

  let res
  try {
    res = await fetch(`${BASE_URL}${path}`, {
      method,
      headers: {
        ...(body ? { 'Content-Type': 'application/json' } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      ...(body ? { body: JSON.stringify(body) } : {}),
      // Without a limit, a server that accepts the connection and never
      // answers leaves every "Saving…" button spinning forever.
      signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    })
  } catch (e) {
    if (e?.name === 'TimeoutError') {
      throw new ApiError('The server took too long to answer. Please try again.', 0, 'TIMEOUT')
    }
    // fetch only rejects when the request never completed — server down,
    // DNS failure, or CORS refusing the response.
    throw new ApiError(
      'Cannot reach the server. Check your connection and try again.',
      0,
      'NETWORK_ERROR',
    )
  }

  // Only a request that sent a token can find its session expired. Without
  // one, the 401 falls through to the ordinary error below.
  if (res.status === 401 && token) {
    // End only the session this request was made with. If the user signed in
    // again while it was in flight, that newer token must survive.
    if (getToken() === token) {
      setToken(null)
      onUnauthorized?.()
    }
    throw new ApiError('Your session has expired. Please sign in again.', 401, 'UNAUTHORIZED')
  }

  if (res.status === 204) return null

  const payload = await res.json().catch(() => null)

  if (!res.ok) {
    // A 403, not a 401: the session stays, only the destination changes.
    if (res.status === 403 && payload?.error === 'PASSWORD_CHANGE_REQUIRED') {
      onPasswordChangeRequired?.()
    }
    throw new ApiError(
      payload?.message || `Request failed (${res.status})`,
      res.status,
      payload?.error,
    )
  }

  return payload
}

export const apiGet = (path) => request('GET', path)
export const apiPost = (path, body, options) => request('POST', path, body, options)
export const apiPatch = (path, body) => request('PATCH', path, body)
export const apiPut = (path, body) => request('PUT', path, body)
export const apiDelete = (path) => request('DELETE', path)
