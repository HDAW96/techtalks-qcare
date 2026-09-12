/* Lebanese phone numbers get typed in several shapes — 03 123456,
   +961 3 123456, 00961 3 123456 — and they are all the same person.

   These rules mirror PhoneNumbers.java on the backend. Keeping them here too
   means the form can tell someone their number looks wrong before a request
   is sent, but the backend normalises again on arrival: a check that only
   happens in the browser is not a check at all. */

/** The number in local form — no spaces, leading zero — or '' if there is nothing usable. */
export function normalisePhone(raw) {
  if (!raw) return ''

  let digits = raw.replace(/[^0-9+]/g, '')
  if (digits === '') return ''

  if (digits.startsWith('+961')) digits = digits.slice(4)
  else if (digits.startsWith('00961')) digits = digits.slice(5)
  else if (digits.startsWith('961') && digits.length > 9) digits = digits.slice(3)

  digits = digits.replace(/\+/g, '')

  // Local numbers are written with a leading zero; the international form
  // drops it, so put it back.
  if (!digits.startsWith('0')) digits = `0${digits}`
  return digits
}

/** Lebanese numbers are 7 or 8 digits after the leading zero. */
export function isValidPhone(raw) {
  return /^0\d{7,8}$/.test(normalisePhone(raw))
}
