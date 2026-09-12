import { Routes, Route } from 'react-router-dom'
import GuestHomePage from './pages/GuestHomePage'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import PatientDashboardPage from './pages/PatientDashboardPage'
import RequireAuth from './auth/RequireAuth'
import './App.css'

function App() {
  return (
    <Routes>
      <Route path="/" element={<GuestHomePage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route
        path="/dashboard"
        element={
          <RequireAuth>
            <PatientDashboardPage />
          </RequireAuth>
        }
      />
    </Routes>
  )
}

export default App
