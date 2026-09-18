import { HOME_ROUTE_FOR_ROLE, ROUTE_ROLES } from '../constants'

/* Where a user goes once signed in: back to the page RequireAuth turned them
   away from (query and hash included) - but only if their role may open it -
   or else their role's home.

   The check matters after a sign-out. Leaving /reception signed-out sends you
   to /login with /reception remembered as the destination; whoever signs in
   next must not be sent back there unless they are also front desk. */
export function destinationAfterSignIn(user, from) {
  if (from?.pathname && mayOpen(user, from.pathname)) {
    return `${from.pathname}${from.search ?? ''}${from.hash ?? ''}`
  }
  return HOME_ROUTE_FOR_ROLE[user.role] ?? '/'
}

function mayOpen(user, pathname) {
  const allowed = ROUTE_ROLES[pathname]
  return !allowed || allowed.includes(user.role)
}
