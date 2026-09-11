import { HOME_ROUTE_FOR_ROLE } from '../constants'

/* Where a user goes once signed in: back to the page RequireAuth turned them
   away from (query and hash included), or else their role's home. */
export function destinationAfterSignIn(user, from) {
  if (from?.pathname) {
    return `${from.pathname}${from.search ?? ''}${from.hash ?? ''}`
  }
  return HOME_ROUTE_FOR_ROLE[user.role] ?? '/'
}
