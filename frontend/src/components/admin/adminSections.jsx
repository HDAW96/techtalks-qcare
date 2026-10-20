import AnalyticsDashboard from './AnalyticsDashboard'
import ManageUsers from './ManageUsers'
import ManageServices from './ManageServices'

/* The admin screen sections, in sidebar order. The first one is what opens
   at /admin with no ?section=, or with one that does not exist.
   Subscriptions and Reports are left out until they are built (SCRUM-115):
   an empty page looks unfinished in a demo. To add a section back, add its
   entry here and nothing else. */
export const ADMIN_SECTIONS = [
  { id: 'users', label: 'Users', element: <ManageUsers /> },
  { id: 'services', label: 'Services', element: <ManageServices /> },
  { id: 'analytics', label: 'Analytics', element: <AnalyticsDashboard /> },
]