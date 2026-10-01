import { useSearchParams } from 'react-router-dom'
import SiteHeader from '../components/SiteHeader'
import SiteFooter from '../components/SiteFooter'
import AdminSidebar from '../components/admin/AdminSidebar'
import { ADMIN_SECTIONS } from '../components/admin/adminSections'
import '../styles/admin.css'

/* The admin screen (SCRUM-83). Who may open it is decided by the route in
   App.jsx, through ROUTE_ROLES['/admin'], not here. */
function AdminPage() {
  const [params] = useSearchParams()
  const active =
    ADMIN_SECTIONS.find((s) => s.id === params.get('section')) ?? ADMIN_SECTIONS[0]

  return (
    <div className="page">
      <title>{`${active.label} - Admin - QCare`}</title>
      <SiteHeader/>
      <main className="wrap">
        <div className="admin">
          <AdminSidebar sections={ADMIN_SECTIONS} activeId={active.id} />
          <div className="admin__main">{active.element}</div>
        </div>
      </main>

      <SiteFooter />
    </div>
  )
}

export default AdminPage