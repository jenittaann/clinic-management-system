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
import './App.css'

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/login" element={<Login />} />
        <Route path="/book-appointment" element={<BookAppointment />} />
        <Route path="/appointments/:name" element={<BookAppointment />} />
        <Route path="/doctors" element={<Doctors />} />
        <Route path="/doctors/:name" element={<DoctorProfile />} />
        <Route path="/patient-dashboard" element={<PatientDashboard />} />
        <Route path="/doctor-dashboard" element={<DoctorDashboard />} />
        <Route path="/receptionist-dashboard" element={<ReceptionistDashboard />} />
        <Route path="/reception-dashboard" element={<ReceptionistDashboard />} />
        <Route path="/admin-dashboard" element={<AdminDashboard />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App 