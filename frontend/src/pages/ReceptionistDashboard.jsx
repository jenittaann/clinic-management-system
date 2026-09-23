import { useState } from "react"
import { Link } from "react-router-dom"

const initialAppointments = [
    { id: 1, patient: "Maya Thomas", doctor: "Dr. Ananya Sharma", time: "09:30 AM", status: "Confirmed" },
    { id: 2, patient: "Arjun Nair", doctor: "Dr. Rahul Menon", time: "11:00 AM", status: "Pending" },
    { id: 3, patient: "Sara Williams", doctor: "Dr. Priya Nair", time: "02:30 PM", status: "Confirmed" },
]

const initialDoctors = [
    { name: "Dr. Ananya Sharma", specialty: "Cardiologist", available: true },
    { name: "Dr. Rahul Menon", specialty: "General Physician", available: false },
    { name: "Dr. Priya Nair", specialty: "Dermatologist", available: true },
]

const patients = ["Maya Thomas", "Arjun Nair", "Sara Williams", "Nikhil Das"]

function ReceptionistDashboard() {
    const [appointments, setAppointments] = useState(initialAppointments)
    const [doctors, setDoctors] = useState(initialDoctors)
    const [patientSearch, setPatientSearch] = useState("")
    const [selectedPatient, setSelectedPatient] = useState("")

    const filteredPatients = patients.filter((patient) => patient.toLowerCase().includes(patientSearch.toLowerCase()))
    const updateAppointment = (id, status) => setAppointments((current) => current.map((appointment) => appointment.id === id ? { ...appointment, status } : appointment))
    const toggleDoctor = (name) => setDoctors((current) => current.map((doctor) => doctor.name === name ? { ...doctor, available: !doctor.available } : doctor))

    return (
        <main className="role-dashboard"><div className="dashboard-shell dashboard-shell-wide">
            <div className="dashboard-kicker">CAMILLUS OPERATIONS</div><div className="dashboard-title-row"><div><h1>Receptionist / Hospital Manager Dashboard</h1><p className="dashboard-intro">Coordinate patient visits and keep the clinic moving smoothly.</p></div><Link to="/" className="dashboard-home-link">Home</Link></div>
            <div className="dashboard-grid">
                <section className="dashboard-panel dashboard-panel-wide"><div className="panel-heading"><div><span className="panel-eyebrow">Today</span><h2>Today's appointments</h2></div><span className="count-badge">{appointments.length}</span></div><div className="dashboard-list">{appointments.map((appointment) => <div className="dashboard-list-item" key={appointment.id}><div><strong>{appointment.patient}</strong><span>{appointment.doctor} · {appointment.time}</span></div><span className={`status-badge status-${appointment.status.toLowerCase()}`}>{appointment.status}</span><div className="inline-actions"><button type="button" className="text-button" onClick={() => updateAppointment(appointment.id, appointment.status === "Confirmed" ? "Completed" : "Confirmed")}>{appointment.status === "Confirmed" ? "Complete" : "Approve"}</button><button type="button" className="text-button danger-text" onClick={() => updateAppointment(appointment.id, "Cancelled")}>Cancel</button></div></div>)}</div></section>
                <section className="dashboard-panel"><div className="panel-heading"><div><span className="panel-eyebrow">Live view</span><h2>Doctor availability</h2></div></div><div className="dashboard-list">{doctors.map((doctor) => <div className="dashboard-list-item compact-list-item" key={doctor.name}><div><strong>{doctor.name}</strong><span>{doctor.specialty}</span></div><button type="button" className={`toggle-button ${doctor.available ? "toggle-on" : ""}`} onClick={() => toggleDoctor(doctor.name)}>{doctor.available ? "Available" : "Unavailable"}</button></div>)}</div></section>
                <section className="dashboard-panel"><div className="panel-heading"><div><span className="panel-eyebrow">Records</span><h2>Patient management</h2></div></div><input className="dashboard-search" placeholder="Search patients..." value={patientSearch} onChange={(event) => setPatientSearch(event.target.value)} /><div className="dashboard-list">{filteredPatients.map((patient) => <div className="dashboard-list-item compact-list-item" key={patient}><strong>{patient}</strong><button type="button" className="text-button" onClick={() => setSelectedPatient(selectedPatient === patient ? "" : patient)}>{selectedPatient === patient ? "Close" : "View details"}</button></div>)}{selectedPatient && <div className="detail-callout">Viewing record for <strong>{selectedPatient}</strong></div>}</div></section>
                <section className="dashboard-panel"><div className="panel-heading"><div><span className="panel-eyebrow">Operations</span><h2>Appointment management</h2></div></div><p className="panel-note">Use the controls in today&apos;s appointments to approve or cancel visits.</p><button type="button" className="dashboard-button" onClick={() => setAppointments((current) => current.map((appointment) => appointment.status === "Pending" ? { ...appointment, status: "Confirmed" } : appointment))}>Approve pending appointments</button><button type="button" className="dashboard-outline-button" onClick={() => setAppointments((current) => current.map((appointment) => appointment.status === "Confirmed" ? { ...appointment, status: "Rescheduled" } : appointment))}>Reschedule confirmed</button></section>
            </div>
        </div></main>
    )
}

export default ReceptionistDashboard
