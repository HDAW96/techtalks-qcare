import { Link } from 'react-router-dom'

function NavBar() {
  return (
    <nav>
      <Link to="/">Book</Link>
      <Link to="/queue">Queue</Link>
      <Link to="/dashboard">Dashboard</Link>
    </nav>
  )
}

export default NavBar
