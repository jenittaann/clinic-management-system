import { useState } from "react"
import { Link } from "react-router-dom"
import BackButton from "../components/BackButton"

const initialAppointments = [
    { id: 1, patient: "Maya Thomas", doctor: "Dr. Ananya Sharma", time: "09:30 AM", department: "Cardiology", status: "Confirmed" },
    { id: 2, patient: "Arjun Nair", doctor: "Dr. Rahul Menon", time: "11:00 AM", department: "General Medicine", status: "Pending" },
    { id: 3, patient: "Sara Williams", doctor: "Dr. Priya Nair", time: "02:30 PM", department: "Dermatology", status: "Confirmed" },
]

const initialDoctors = [
    { name: "Dr. Ananya Sharma", specialty: "Cardiologist", hours: "09:00 AM - 01:00 PM", available: true },
    { name: "Dr. Rahul Menon", specialty: "General Physician", hours: "11:00 AM - 04:00 PM", available: false },
    { name: "Dr. Priya Nair", specialty: "Dermatologist", hours: "10:00 AM - 05:00 PM", available: true },
]

const initialRequests = [
    { id: 1, patient: "Nikhil Das", doctor: "Dr. Rahul Menon", requested: "Tomorrow, 10:00 AM", status: "Pending" },
    { id: 2, patient: "Emily Joseph", doctor: "Dr. Priya Nair", requested: "Friday, 03:00 PM", status: "Pending" },
]

const patients = [
    { name: "Maya Thomas", details: "31 years · Last visit 06 Oct 2026 · Blood pressure review" },
    { name: "Arjun Nair", details: "45 years · Last visit 29 Sep 2026 · Annual cardiac screening" },
    { name: "Sara Williams", details: "27 years · Last visit 14 Sep 2026 · Follow-up consultation" },
    { name: "Nikhil Das", details: "52 years · Last visit 08 Sep 2026 · General consultation" },
]

const initialDepartments = [
    { name: "Cardiology", doctors: 3, patients: 18 },
    { name: "General Medicine", doctors: 5, patients: 26 },
    { name: "Dermatology", doctors: 2, patients: 11 },
]

const clinic = { name: "Camillus Medical Centre", address: "24 Lakeview Road, Kochi", phone: "+91 484 555 0120", hours: "Mon-Sat · 08:00 AM - 06:00 PM" }

function ReceptionistDashboard() {
    const [appointments, setAppointments] = useState(initialAppointments)
    const [requests, setRequests] = useState(initialRequests)
    const [doctors, setDoctors] = useState(initialDoctors)
    const [departments, setDepartments] = useState(initialDepartments)
    const [patientSearch, setPatientSearch] = useState("")
    const [selectedPatient, setSelectedPatient] = useState("")

    const filteredPatients = patients.filter((patient) => patient.name.toLowerCase().includes(patientSearch.toLowerCase()))
    const updateAppointment = (id, status) => setAppointments((current) => current.map((appointment) => appointment.id === id ? { ...appointment, status } : appointment))
    const updateRequest = (id, status) => setRequests((current) => current.map((request) => request.id === id ? { ...request, status } : request))
    const toggleDoctor = (name) => setDoctors((current) => current.map((doctor) => doctor.name === name ? { ...doctor, available: !doctor.available } : doctor))

    return (
        <main className="role-dashboard">
            <div className="dashboard-shell dashboard-shell-wide">
                <div className="dashboard-kicker">CAMILLUS OPERATIONS</div>
                <div className="dashboard-title-row">
                    <div><h1>Receptionist / Hospital Manager Dashboard</h1><p className="dashboard-intro">Coordinate patient visits and keep the clinic moving smoothly.</p></div>
                    <div className="dashboard-header-links"><BackButton className="back-button-inline" /><Link to="/" className="dashboard-home-link">Home</Link><Link to="/settings" state={{ from: "/receptionist-dashboard" }} className="dashboard-home-link">Settings</Link></div>
                </div>

                <section className="dashboard-stats">
                    <div><strong>{appointments.length}</strong><span>Appointments today</span></div>
                    <div><strong>{requests.filter((request) => request.status === "Pending").length}</strong><span>Pending requests</span></div>
                    <div><strong>{patients.length}</strong><span>Registered patients</span></div>
                    <div><strong>{doctors.filter((doctor) => doctor.available).length}</strong><span>Doctors available</span></div>
                </section>

                <section className="dashboard-panel">
                    <div className="panel-heading"><div><span className="panel-eyebrow">Quick actions</span><h2>Clinic operations</h2></div></div>
                    <div className="dashboard-grid">
                        <button type="button" className="dashboard-button" onClick={() => setAppointments((current) => [...current, { id: Date.now(), patient: "Walk-in patient", doctor: "Dr. Ananya Sharma", time: "03:30 PM", department: "Cardiology", status: "Pending" }])}>Add appointment</button>
                        <a className="dashboard-button" href="#patients">View patients</a>
                        <a className="dashboard-button" href="#doctors">View doctors</a>
                        <a className="dashboard-button" href="#departments">Manage departments</a>
                    </div>
                </section>

                <div className="dashboard-grid">
                    <section className="dashboard-panel dashboard-panel-wide">
                        <div className="panel-heading"><div><span className="panel-eyebrow">Today</span><h2>Today's appointments</h2></div><span className="count-badge">{appointments.length}</span></div>
                        <div className="dashboard-list">
                            {appointments.map((appointment) => <div className="dashboard-list-item" key={appointment.id}>
                                <div><strong>{appointment.patient}</strong><span>{appointment.doctor} · {appointment.time} · {appointment.department}</span></div>
                                <span className={`status-badge status-${appointment.status.toLowerCase()}`}>{appointment.status}</span>
                                <div className="inline-actions">
                                    {appointment.status === "Pending" && <button type="button" className="text-button" onClick={() => updateAppointment(appointment.id, "Confirmed")}>Confirm</button>}
                                    {appointment.status !== "Cancelled" && <button type="button" className="text-button danger-text" onClick={() => updateAppointment(appointment.id, "Cancelled")}>Cancel</button>}
                                </div>
                            </div>)}
                        </div>
                    </section>

                    <section className="dashboard-panel">
                        <div className="panel-heading"><div><span className="panel-eyebrow">Needs review</span><h2>Appointment requests</h2></div></div>
                        <div className="dashboard-list">{requests.map((request) => <div className="dashboard-list-item compact-list-item" key={request.id}>
                            <div><strong>{request.patient}</strong><span>{request.doctor} · {request.requested}</span></div>
                            <span className={`status-badge status-${request.status.toLowerCase()}`}>{request.status}</span>
                            {request.status === "Pending" && <div className="inline-actions"><button type="button" className="text-button" onClick={() => updateRequest(request.id, "Confirmed")}>Confirm</button><button type="button" className="text-button danger-text" onClick={() => updateRequest(request.id, "Cancelled")}>Cancel</button></div>}
                        </div>)}</div>
                    </section>

                    <section className="dashboard-panel" id="patients">
                        <div className="panel-heading"><div><span className="panel-eyebrow">Records</span><h2>Patient list</h2></div><span className="count-badge">{patients.length}</span></div>
                        <input className="dashboard-search" aria-label="Search patients" placeholder="Search patients..." value={patientSearch} onChange={(event) => setPatientSearch(event.target.value)} />
                        <div className="dashboard-list">{filteredPatients.length === 0 ? <p className="empty-state">No patients found.</p> : filteredPatients.map((patient) => <div className="dashboard-list-item compact-list-item" key={patient.name}>
                            <div><strong>{patient.name}</strong><span>{patient.details}</span></div>
                            <button type="button" className="text-button" onClick={() => setSelectedPatient(selectedPatient === patient.name ? "" : patient.name)}>{selectedPatient === patient.name ? "Close" : "View details"}</button>
                        </div>)}{selectedPatient && <div className="detail-callout">Viewing record for <strong>{selectedPatient}</strong></div>}</div>
                    </section>

                    <section className="dashboard-panel" id="doctors">
                        <div className="panel-heading"><div><span className="panel-eyebrow">Live view</span><h2>Doctor availability</h2></div></div>
                        <div className="dashboard-list">{doctors.map((doctor) => <div className="dashboard-list-item compact-list-item" key={doctor.name}>
                            <div><strong>{doctor.name}</strong><span>{doctor.specialty} · {doctor.hours}</span></div>
                            <button type="button" className={`toggle-button ${doctor.available ? "toggle-on" : ""}`} onClick={() => toggleDoctor(doctor.name)}>{doctor.available ? "Available" : "Unavailable"}</button>
                        </div>)}</div>
                    </section>

                    <section className="dashboard-panel">
                        <div className="panel-heading"><div><span className="panel-eyebrow">Location</span><h2>Clinic information</h2></div></div>
                        <div className="profile-summary"><strong>{clinic.name}</strong><span>{clinic.address}</span><span>{clinic.phone}</span><span>{clinic.hours}</span></div>
                    </section>

                    <section className="dashboard-panel" id="departments">
                        <div className="panel-heading"><div><span className="panel-eyebrow">Clinical structure</span><h2>Departments</h2></div><button type="button" className="dashboard-action-button" onClick={() => setDepartments((current) => [...current, { name: "New department", doctors: 0, patients: 0 }])}>Add department</button></div>
                        <div className="dashboard-list">{departments.map((department) => <div className="dashboard-list-item compact-list-item" key={department.name}><div><strong>{department.name}</strong><span>{department.doctors} doctors · {department.patients} appointments today</span></div></div>)}</div>
                    </section>
                </div>
            </div>
        </main>
    )
}

export default ReceptionistDashboard
