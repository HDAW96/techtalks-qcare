import AnalyticsDashboard from './AnalyticsDashboard'
import ManageUsers from './ManageUsers'
import ManageServices from './ManageServices'
import SectionPlaceholder from './SectionPlaceholder'

/* The admin screen sections, in sidebar order. The first one is what opens
   at /admin with no ?section=, or with one that does not exist.
   A ticket that builds a section swaps its `element` here and nothing else. */
export const ADMIN_SECTIONS = [
  { id: 'users', label: 'Users', element: <ManageUsers /> },
  { id: 'services', label: 'Services', element: <ManageServices /> },
  { id: 'subscriptions', label: 'Subscriptions', element: <SectionPlaceholder title="Subscriptions" /> },
  { id: 'analytics', label: 'Analytics', element: <AnalyticsDashboard /> },
  { id: 'reports', label: 'Reports', element: <SectionPlaceholder title="Reports" /> },
]