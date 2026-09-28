import { useState } from "react"
import { Link } from "react-router-dom"
import BackButton from "../components/BackButton"

const initialUsers = [
    { name: "Maya Thomas", email: "maya@example.com", role: "Patient", active: true },
    { name: "Dr. Ananya Sharma", email: "ananya@camillus.com", role: "Doctor", active: true },
    { name: "Priya Menon", email: "priya@camillus.com", role: "Receptionist", active: true },
    { name: "Admin Team", email: "admin@camillus.com", role: "Super Admin", active: true },
]

const initialDoctors = [
    { name: "Dr. Ananya Sharma", specialty: "Cardiologist", enabled: true },
    { name: "Dr. Rahul Menon", specialty: "General Physician", enabled: true },
    { name: "Dr. Priya Nair", specialty: "Dermatologist", enabled: false },
]

const initialClinics = ["Camillus Medical Centre", "City Care Hospital", "Wellness Point Clinic"]
const initialDepartments = [{ name: "Cardiology", doctors: 3 }, { name: "General Medicine", doctors: 5 }, { name: "Dermatology", doctors: 2 }]
const initialAppointments = [
    { id: 1, patient: "Maya Thomas", clinic: "Camillus Medical Centre", doctor: "Dr. Ananya Sharma", date: "Today, 09:30 AM", status: "Confirmed" },
    { id: 2, patient: "Arjun Nair", clinic: "City Care Hospital", doctor: "Dr. Rahul Menon", date: "Today, 11:00 AM", status: "Pending" },
    { id: 3, patient: "Sara Williams", clinic: "Wellness Point Clinic", doctor: "Dr. Priya Nair", date: "Today, 02:30 PM", status: "Confirmed" },
]
const recentActivity = [
    { action: "New clinic registered", detail: "Wellness Point Clinic · 18 minutes ago" },
    { action: "Doctor profile updated", detail: "Dr. Ananya Sharma · 1 hour ago" },
    { action: "Appointment completed", detail: "Maya Thomas · 2 hours ago" },
    { action: "New patient account created", detail: "Nikhil Das · Today, 08:14 AM" },
]

function AdminDashboard() {
    const [users, setUsers] = useState(initialUsers)
    const [doctors, setDoctors] = useState(initialDoctors)
    const [clinics, setClinics] = useState(initialClinics)
    const [departments, setDepartments] = useState(initialDepartments)
    const [appointments, setAppointments] = useState(initialAppointments)
    const [search, setSearch] = useState("")
    const [selected, setSelected] = useState("")

    const filteredUsers = users.filter((user) => `${user.name} ${user.email} ${user.role}`.toLowerCase().includes(search.toLowerCase()))
    const toggleDoctor = (name) => setDoctors((current) => current.map((doctor) => doctor.name === name ? { ...doctor, enabled: !doctor.enabled } : doctor))
    const toggleUser = (email) => setUsers((current) => current.map((user) => user.email === email ? { ...user, active: !user.active } : user))
    const updateAppointment = (id, status) => setAppointments((current) => current.map((appointment) => appointment.id === id ? { ...appointment, status } : appointment))

    return (
        <main className="role-dashboard">
            <div className="dashboard-shell dashboard-shell-wide">
                <div className="dashboard-kicker">CAMILLUS ADMINISTRATION</div>
                <div className="dashboard-title-row">
                    <div><h1>Super Admin Dashboard</h1><p className="dashboard-intro">Review the platform&apos;s people, places, and clinical structure.</p></div>
                    <div className="dashboard-header-links"><BackButton className="back-button-inline" /><Link to="/" className="dashboard-home-link">Home</Link><Link to="/settings" state={{ from: "/admin-dashboard" }} className="dashboard-home-link">Settings</Link></div>
                </div>

                <section className="dashboard-stats">
                    <div><strong>{clinics.length}</strong><span>Hospitals / clinics</span></div>
                    <div><strong>{doctors.length}</strong><span>Total doctors</span></div>
                    <div><strong>1,248</strong><span>Total patients</span></div>
                    <div><strong>86</strong><span>Total appointments</span></div>
                </section>

                <section className="dashboard-panel">
                    <div className="panel-heading"><div><span className="panel-eyebrow">Quick actions</span><h2>System management</h2></div></div>
                    <div className="dashboard-grid">
                        <button type="button" className="dashboard-button" onClick={() => setClinics((current) => [...current, `New clinic ${current.length + 1}`])}>Add hospital</button>
                        <a className="dashboard-button" href="#users">Manage users</a>
                        <a className="dashboard-button" href="#doctors">Manage doctors</a>
                        <a className="dashboard-button" href="#hospitals">Manage hospitals</a>
                        <a className="dashboard-button" href="#appointments">View appointments</a>
                    </div>
                </section>

                <div className="dashboard-grid">
                    <section className="dashboard-panel dashboard-panel-wide" id="users">
                        <div className="panel-heading"><div><span className="panel-eyebrow">Accounts</span><h2>Doctor and user management</h2></div><span className="count-badge">{users.length}</span></div>
                        <input className="dashboard-search" aria-label="Search users" placeholder="Search users by name, email, or role..." value={search} onChange={(event) => setSearch(event.target.value)} />
                        <div className="dashboard-list">{filteredUsers.length === 0 ? <p className="empty-state">No users found.</p> : filteredUsers.map((user) => <div className="dashboard-list-item compact-list-item" key={user.email}>
                            <div><strong>{user.name}</strong><span>{user.email} · {user.role} · {user.active ? "Active" : "Disabled"}</span></div>
                            <div className="inline-actions"><button type="button" className="text-button" onClick={() => setSelected(selected === user.email ? "" : user.email)}>{selected === user.email ? "Close" : "View"}</button><button type="button" className={`text-button ${user.active ? "danger-text" : ""}`} onClick={() => toggleUser(user.email)}>{user.active ? "Disable" : "Enable"}</button></div>
                        </div>)}</div>
                        {selected && !selected.startsWith("doctor:") && !selected.startsWith("edit:") && <div className="detail-callout">Selected account: <strong>{users.find((user) => user.email === selected)?.name}</strong></div>}
                    </section>

                    <section className="dashboard-panel" id="doctors">
                        <div className="panel-heading"><div><span className="panel-eyebrow">Clinical team</span><h2>Doctor management</h2></div></div>
                        <div className="dashboard-list">{doctors.map((doctor) => <div className="dashboard-list-item compact-list-item" key={doctor.name}>
                            <div><strong>{doctor.name}</strong><span>{doctor.specialty} · {doctor.enabled ? "Active" : "Disabled"}</span></div>
                            <div className="inline-actions"><button type="button" className="text-button" onClick={() => setSelected(`doctor:${doctor.name}`)}>{selected === `doctor:${doctor.name}` ? "Selected" : "View"}</button><button type="button" className="text-button" onClick={() => setSelected(`edit:${doctor.name}`)}>Edit</button><button type="button" className={`text-button ${doctor.enabled ? "danger-text" : ""}`} onClick={() => toggleDoctor(doctor.name)}>{doctor.enabled ? "Disable" : "Enable"}</button></div>
                        </div>)}{selected?.startsWith("doctor:") && <div className="detail-callout">Viewing doctor details for <strong>{selected.replace("doctor:", "")}</strong></div>}{selected?.startsWith("edit:") && <div className="detail-callout">Managing profile for <strong>{selected.replace("edit:", "")}</strong></div>}</div>
                    </section>

                    <section className="dashboard-panel" id="hospitals">
                        <div className="panel-heading"><div><span className="panel-eyebrow">Locations</span><h2>Hospital management</h2></div><button type="button" className="dashboard-action-button" onClick={() => setClinics((current) => [...current, `New clinic ${current.length + 1}`])}>Add hospital</button></div>
                        <div className="dashboard-list">{clinics.map((clinic) => <div className="dashboard-list-item compact-list-item" key={clinic}>
                            <strong>{clinic}</strong><button type="button" className="text-button" onClick={() => setSelected(selected === `clinic:${clinic}` ? "" : `clinic:${clinic}`)}>{selected === `clinic:${clinic}` ? "Selected" : "View details"}</button>
                        </div>)}{selected?.startsWith("clinic:") && <div className="detail-callout">Hospital profile: <strong>{selected.replace("clinic:", "")}</strong> · Active</div>}</div>
                    </section>

                    <section className="dashboard-panel" id="departments">
                        <div className="panel-heading"><div><span className="panel-eyebrow">Clinical structure</span><h2>Department management</h2></div><button type="button" className="dashboard-action-button" onClick={() => setDepartments((current) => [...current, { name: "New department", doctors: 0 }])}>Add department</button></div>
                        <div className="dashboard-list">{departments.map((department) => <div className="dashboard-list-item compact-list-item" key={department.name}><div><strong>{department.name}</strong><span>{department.doctors} associated doctors</span></div></div>)}</div>
                    </section>

                    <section className="dashboard-panel dashboard-panel-wide" id="appointments">
                        <div className="panel-heading"><div><span className="panel-eyebrow">Platform activity</span><h2>Appointment overview</h2></div><span className="count-badge">{appointments.length} shown</span></div>
                        <div className="dashboard-list">{appointments.map((appointment) => <div className="dashboard-list-item" key={appointment.id}>
                            <div><strong>{appointment.patient}</strong><span>{appointment.doctor} · {appointment.clinic} · {appointment.date}</span></div>
                            <span className={`status-badge status-${appointment.status.toLowerCase()}`}>{appointment.status}</span>
                            <select className="dashboard-select" aria-label={`Update ${appointment.patient} appointment status`} value={appointment.status} onChange={(event) => updateAppointment(appointment.id, event.target.value)}>
                                <option>Pending</option><option>Confirmed</option><option>Cancelled</option>
                            </select>
                        </div>)}</div>
                    </section>

                    <section className="dashboard-panel">
                        <div className="panel-heading"><div><span className="panel-eyebrow">Audit trail</span><h2>Recent system activity</h2></div></div>
                        <div className="dashboard-list">{recentActivity.map((activity) => <div className="dashboard-list-item compact-list-item" key={activity.action}><div><strong>{activity.action}</strong><span>{activity.detail}</span></div></div>)}</div>
                    </section>
                </div>
            </div>
        </main>
    )
}

export default AdminDashboard
