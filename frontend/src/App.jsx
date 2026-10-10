import { BrowserRouter, Routes, Route } from 'react-router-dom'
import Home from './pages/Home'
import Login from './pages/Login'
import Doctors from './pages/Doctors'
import DoctorProfile from './pages/Doctorsprofile'
import BookAppointment from './pages/BookAppointment'
import PatientDashboard from './pages/PatientDashboard'
import DoctorDashboard from './pages/DoctorDashboard'
import ReceptionistDashboard from './pages/ReceptionistDashboard'
import AdminDashboard from './pages/AdminDashboard'
import Settings from './pages/Settings'
import Hospitals from './pages/Hospitals'
import HospitalDetails from './pages/HospitalDetails'
import { useEffect, useState } from 'react'
import './App.css'

function App() {
  const [theme, setTheme] = useState(() => window.localStorage.getItem('camillus-theme') || 'light')

  useEffect(() => {
    document.documentElement.dataset.theme = theme
    window.localStorage.setItem('camillus-theme', theme)
  }, [theme])

  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/login" element={<Login />} />
        <Route path="/book-appointment" element={<BookAppointment />} />
        <Route path="/appointments/:name" element={<BookAppointment />} />
        <Route path="/doctors" element={<Doctors />} />
        <Route path="/doctors/:name" element={<DoctorProfile />} />
        <Route path="/hospitals" element={<Hospitals />} />
        <Route path="/hospitals/:hospitalId" element={<HospitalDetails />} />
        <Route path="/patient-dashboard" element={<PatientDashboard />} />
        <Route path="/doctor-dashboard" element={<DoctorDashboard />} />
        <Route path="/receptionist-dashboard" element={<ReceptionistDashboard />} />
        <Route path="/reception-dashboard" element={<ReceptionistDashboard />} />
        <Route path="/admin-dashboard" element={<AdminDashboard />} />
        <Route path="/settings" element={<Settings theme={theme} onThemeChange={setTheme} />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App 