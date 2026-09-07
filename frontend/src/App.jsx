import { Routes, Route } from 'react-router-dom'
import NavBar from './components/NavBar'
import BookingPage from './pages/BookingPage'
import QueuePage from './pages/QueuePage'
import DashboardPage from './pages/DashboardPage'
import './App.css'

function App() {
  return (
    <>
      <NavBar />
      <Routes>
        <Route path="/" element={<BookingPage />} />
        <Route path="/queue" element={<QueuePage />} />
        <Route path="/dashboard" element={<DashboardPage />} />
      </Routes>
    </>
  )
}

export default App
