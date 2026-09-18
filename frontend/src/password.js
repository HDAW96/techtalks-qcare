/* The rules every new password must meet. The same four AuthService checks on
   the server (requireAcceptablePassword), checked here too so a form waits
   for a password the server will accept instead of failing after the press.
   Shared by the register and change-password forms. */

export const MIN_PASSWORD = 8

/* The first rule the password breaks, or null once it meets them all. */
export function passwordProblem(password) {
  if (password.length < MIN_PASSWORD) return `At least ${MIN_PASSWORD} characters`
  if (!/[A-Z]/.test(password)) return 'Add at least one uppercase letter'
  if (!/[a-z]/.test(password)) return 'Add at least one lowercase letter'
  if (!/[^a-zA-Z0-9]/.test(password)) return 'Add at least one special character, for example ! or -'
  return null
}

export const PASSWORD_NOTE =
  `At least ${MIN_PASSWORD} characters including 1 uppercase, 1 lowercase and 1 special character.`
