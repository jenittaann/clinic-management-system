import { BrowserRouter, Routes, Route } from 'react-router-dom'
import Home from './pages/Home'
import Login from './pages/Login'
import Doctors from './pages/Doctors'
import DoctorProfile from './pages/Doctorsprofile'
import BookAppointment from './pages/BookAppointment'
import './App.css'

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/login" element={<Login />} />
        <Route path="/appointments/:name" element={<BookAppointment />} />
        <Route path="/doctors" element={<Doctors />} />
        <Route path="/doctors/:name" element={<DoctorProfile />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App 