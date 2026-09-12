/* The single place the frontend talks to the backend.
   Everything about how a request is authenticated lives here, so switching
   from a bearer token to session cookies later means changing this file and
   nothing else. */

const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'

const TOKEN_KEY = 'qcare.token'

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

async function request(method, path, body) {
  const token = getToken()

  let res
  try {
    res = await fetch(`${BASE_URL}${path}`, {
      method,
      headers: {
        ...(body ? { 'Content-Type': 'application/json' } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      ...(body ? { body: JSON.stringify(body) } : {}),
    })
  } catch {
    // fetch only rejects when the request never completed — server down,
    // DNS failure, or CORS refusing the response.
    throw new ApiError(
      'Cannot reach the server. Check your connection and try again.',
      0,
      'NETWORK_ERROR',
    )
  }

  if (res.status === 401) {
    setToken(null)
    onUnauthorized?.()
    throw new ApiError('Your session has expired. Please sign in again.', 401, 'UNAUTHORIZED')
  }

  if (res.status === 204) return null

  const payload = await res.json().catch(() => null)

  if (!res.ok) {
    throw new ApiError(
      payload?.message || `Request failed (${res.status})`,
      res.status,
      payload?.error,
    )
  }

  return payload
}

export const apiGet = (path) => request('GET', path)
export const apiPost = (path, body) => request('POST', path, body)
export const apiPatch = (path, body) => request('PATCH', path, body)
export const apiDelete = (path) => request('DELETE', path)
