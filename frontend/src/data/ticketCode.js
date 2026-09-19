/* The access code of a ticket taken without an account. It is the only way
   back to that ticket, so the browser keeps it - with the day it was taken,
   because yesterday's code opens nothing today. Storage can be blocked
   (private browsing), so every access is guarded. */

const KEY = 'qcare.ticket'

function today() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

export function saveTicketCode(code) {
  try {
    localStorage.setItem(KEY, JSON.stringify({ code, date: today() }))
  } catch {
    // Not fatal: the ticket screen link still carries the code.
  }
}

export function readTicketCode() {
  try {
    const saved = JSON.parse(localStorage.getItem(KEY) ?? 'null')
    return saved?.date === today() ? saved.code : null
  } catch {
    return null
  }
}

export function clearTicketCode() {
  try {
    localStorage.removeItem(KEY)
  } catch {
    // Nothing to clear.
  }
}
