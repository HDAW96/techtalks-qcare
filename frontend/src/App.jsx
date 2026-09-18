import { Routes, Route } from 'react-router-dom'
import GuestHomePage from './pages/GuestHomePage'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import PatientDashboardPage from './pages/PatientDashboardPage'
import ReceptionPage from './pages/ReceptionPage'
import NotFoundPage from './pages/NotFoundPage'
import RequireAuth from './auth/RequireAuth'
import RedirectIfSignedIn from './auth/RedirectIfSignedIn'
import { ROUTE_ROLES } from './constants'
import './App.css'

function App() {
  return (
    <Routes>
      <Route path="/" element={<GuestHomePage />} />
      <Route
        path="/login"
        element={
          <RedirectIfSignedIn>
            <LoginPage />
          </RedirectIfSignedIn>
        }
      />
      <Route
        path="/register"
        element={
          <RedirectIfSignedIn>
            <RegisterPage />
          </RedirectIfSignedIn>
        }
      />
      <Route
        path="/dashboard"
        element={
          <RequireAuth roles={ROUTE_ROLES['/dashboard']}>
            <PatientDashboardPage />
          </RequireAuth>
        }
      />
      {/* The desk sees every patient's name and phone number, so this is
          gated on the role, not merely on being signed in. */}
      <Route
        path="/reception"
        element={
          <RequireAuth roles={ROUTE_ROLES['/reception']}>
            <ReceptionPage />
          </RequireAuth>
        }
      />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}

export default App
