import { Link } from 'react-router-dom'

/* The section lives in the URL (?section=users), so a refresh or a shared
   link comes back to the same section. NavLink is not used: it matches on
   the path only and would mark every link active. */
function AdminSidebar({ sections, activeId }) {
  return (
    <nav className="admin__nav" aria-label="Admin sections">
      <ul className="admin__list">
        {sections.map((s) => {
          const active = s.id === activeId
          return (
            <li key={s.id}>
              <Link
                to={`/admin?section=${s.id}`}
                className={active ? 'admin__link is-active' : 'admin__link'}
                aria-current={active ? 'page' : undefined}
              >
                {s.label}
              </Link>
            </li>
          )
        })}
      </ul>
    </nav>
  )
}

export default AdminSidebar