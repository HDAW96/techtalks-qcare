import { Link } from 'react-router-dom'
import SiteHeader from '../components/SiteHeader'
import SiteFooter from '../components/SiteFooter'

/* Any URL no route matches. Without this the app renders nothing at all —
   no header, no way back. */
function NotFoundPage() {
  return (
    <div className="page">
      <SiteHeader />
      <main className="wrap">
        <div className="empty stack">
          <h1>Page not found</h1>
          <p>
            There is nothing at this address. <Link to="/">Go to the home page</Link>.
          </p>
        </div>
      </main>
      <SiteFooter />
    </div>
  )
}

export default NotFoundPage
