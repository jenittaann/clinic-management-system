import { useState } from "react"
import { Link } from "react-router-dom"
import BackButton from "../components/BackButton"

const initialAppointments = [
    { id: 1, patient: "Maya Thomas", time: "09:30 AM", type: "Follow-up", status: "Confirmed" },
    { id: 2, patient: "Arjun Nair", time: "11:00 AM", type: "Consultation", status: "Pending" },
    { id: 3, patient: "Sara Williams", time: "02:30 PM", type: "Review", status: "Confirmed" },
]

const initialRequests = [
    { id: 1, patient: "Nikhil Das", requested: "Tomorrow, 10:00 AM", status: "Pending" },
    { id: 2, patient: "Emily Joseph", requested: "Friday, 03:00 PM", status: "Pending" },
]

const patients = [
    { name: "Maya Thomas", age: 31, lastVisit: "06 Oct 2026", concern: "Blood pressure review" },
    { name: "Arjun Nair", age: 45, lastVisit: "29 Sep 2026", concern: "Annual cardiac screening" },
    { name: "Sara Williams", age: 27, lastVisit: "14 Sep 2026", concern: "Follow-up consultation" },
]

function DoctorDashboard() {
    const [profile, setProfile] = useState({ name: "Dr. Ananya Sharma", specialty: "Cardiologist", clinic: "Camillus Medical Centre", hours: "09:00 AM - 04:00 PM", contact: "+91 98765 43210" })
    const [draftProfile, setDraftProfile] = useState(profile)
    const [editingProfile, setEditingProfile] = useState(false)
    const [appointments, setAppointments] = useState(initialAppointments)
    const [requests, setRequests] = useState(initialRequests)
    const [patientSearch, setPatientSearch] = useState("")
    const [selectedPatient, setSelectedPatient] = useState(null)
    const [selectedAppointment, setSelectedAppointment] = useState(null)
    const [available, setAvailable] = useState(true)
    const [slot, setSlot] = useState("09:00 AM - 04:00 PM")

    const filteredPatients = patients.filter((patient) => patient.name.toLowerCase().includes(patientSearch.toLowerCase()))

    function updateAppointment(id, status) {
        setAppointments((current) => current.map((appointment) => appointment.id === id ? { ...appointment, status } : appointment))
    }

    function updateRequest(id, status) {
        setRequests((current) => current.map((request) => request.id === id ? { ...request, status } : request))
    }

    function saveProfile(event) {
        event.preventDefault()
        setProfile(draftProfile)
        setEditingProfile(false)
    }

    return (
        <main className="role-dashboard">
            <div className="dashboard-shell dashboard-shell-wide">
                <div className="dashboard-kicker">CAMILLUS CLINIC TEAM</div>
                <div className="dashboard-title-row"><div><h1>Doctor Dashboard</h1><p className="dashboard-intro">Manage your clinic schedule and patient care from one place.</p></div><div className="dashboard-header-links"><BackButton className="back-button-inline" /><Link to="/" className="dashboard-home-link">Home</Link><Link to="/settings" state={{ from: "/doctor-dashboard" }} className="dashboard-home-link">Settings</Link></div></div>

                <div className="dashboard-grid">
                    <section className="dashboard-panel">
                        <div className="panel-heading"><div><span className="panel-eyebrow">Professional details</span><h2>Doctor profile</h2></div><button type="button" className="dashboard-action-button" onClick={() => setEditingProfile(!editingProfile)}>{editingProfile ? "Close" : "View/Edit Profile"}</button></div>
                        {editingProfile ? <form className="dashboard-form" onSubmit={saveProfile}>{Object.entries(draftProfile).map(([key, value]) => <label key={key}>{key === "clinic" ? "Hospital / clinic" : key.charAt(0).toUpperCase() + key.slice(1)}<input value={value} onChange={(event) => setDraftProfile({ ...draftProfile, [key]: event.target.value })} /></label>)}<button className="dashboard-button" type="submit">Save profile</button></form> : <div className="profile-summary"><strong>{profile.name}</strong><span>{profile.specialty} · {profile.clinic}</span><span>{profile.hours} · {profile.contact}</span></div>}
                    </section>

                    <section className="dashboard-panel"><div className="panel-heading"><div><span className="panel-eyebrow">Clinic schedule</span><h2>Availability</h2></div><button type="button" className={`toggle-button ${available ? "toggle-on" : ""}`} onClick={() => setAvailable(!available)}>{available ? "Available" : "Unavailable"}</button></div><p className="panel-note">Patients can request appointments during your selected slot.</p><select className="dashboard-select" value={slot} onChange={(event) => setSlot(event.target.value)}><option>09:00 AM - 04:00 PM</option><option>10:00 AM - 06:00 PM</option><option>Not accepting slots</option></select></section>

                    <section className="dashboard-panel dashboard-panel-wide"><div className="panel-heading"><div><span className="panel-eyebrow">Today</span><h2>Today's appointments</h2></div><span className="count-badge">{appointments.length}</span></div><div className="dashboard-list">{appointments.map((appointment) => <div className="dashboard-list-item" key={appointment.id}><div><strong>{appointment.patient}</strong><span>{appointment.time} · {appointment.type}</span></div><span className={`status-badge status-${appointment.status.toLowerCase()}`}>{appointment.status}</span><div className="inline-actions"><button type="button" className="text-button" onClick={() => setSelectedAppointment(selectedAppointment?.id === appointment.id ? null : appointment)}>View</button>{appointment.status !== "Completed" && <button type="button" className="text-button" onClick={() => updateAppointment(appointment.id, appointment.status === "Confirmed" ? "Completed" : "Confirmed")}>{appointment.status === "Confirmed" ? "Complete" : "Accept"}</button>}<button type="button" className="text-button danger-text" onClick={() => updateAppointment(appointment.id, "Cancelled")}>Cancel</button></div></div>)}{selectedAppointment && <div className="detail-callout"><strong>{selectedAppointment.patient}</strong><span>{selectedAppointment.time} · {selectedAppointment.type} · {selectedAppointment.status}</span></div>}</div></section>

                    <section className="dashboard-panel"><div className="panel-heading"><div><span className="panel-eyebrow">Care team</span><h2>Patient list</h2></div></div><input className="dashboard-search" placeholder="Search patients..." value={patientSearch} onChange={(event) => setPatientSearch(event.target.value)} /><div className="dashboard-list">{filteredPatients.length === 0 ? <p className="empty-state">No patients found.</p> : filteredPatients.map((patient) => <div className="dashboard-list-item compact-list-item" key={patient.name}><div><strong>{patient.name}</strong><span>Age {patient.age} · Last visit {patient.lastVisit}</span></div><button type="button" className="text-button" onClick={() => setSelectedPatient(selectedPatient?.name === patient.name ? null : patient)}>{selectedPatient?.name === patient.name ? "Close" : "View Details"}</button></div>)}{selectedPatient && <div className="detail-callout"><strong>{selectedPatient.name}</strong><span>{selectedPatient.concern}</span></div>}</div></section>

                    <section className="dashboard-panel"><div className="panel-heading"><div><span className="panel-eyebrow">Needs review</span><h2>Appointment requests</h2></div></div><div className="dashboard-list">{requests.map((request) => <div className="dashboard-list-item compact-list-item" key={request.id}><div><strong>{request.patient}</strong><span>{request.requested}</span></div><span className={`status-badge status-${request.status.toLowerCase()}`}>{request.status}</span><div className="inline-actions"><button type="button" className="text-button" onClick={() => updateRequest(request.id, "Accepted")}>Accept</button><button type="button" className="text-button danger-text" onClick={() => updateRequest(request.id, "Rejected")}>Reject</button></div></div>)}</div></section>
                </div>
            </div>
        </main>
    )
}

export default DoctorDashboard
