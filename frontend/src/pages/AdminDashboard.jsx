import { useState } from "react"
import { Link } from "react-router-dom"

const initialUsers = [
    { name: "Maya Thomas", email: "maya@example.com", role: "Patient" },
    { name: "Dr. Ananya Sharma", email: "ananya@camillus.com", role: "Doctor" },
    { name: "Priya Menon", email: "priya@camillus.com", role: "Receptionist" },
    { name: "Admin Team", email: "admin@camillus.com", role: "Super Admin" },
]

const initialDoctors = [
    { name: "Dr. Ananya Sharma", specialty: "Cardiologist", enabled: true },
    { name: "Dr. Rahul Menon", specialty: "General Physician", enabled: true },
    { name: "Dr. Priya Nair", specialty: "Dermatologist", enabled: false },
]

const clinics = ["Camillus Medical Centre", "City Care Hospital", "Wellness Point Clinic"]
const departments = [{ name: "Cardiology", doctors: 3 }, { name: "General Medicine", doctors: 5 }, { name: "Dermatology", doctors: 2 }]

function AdminDashboard() {
    const [users] = useState(initialUsers)
    const [doctors, setDoctors] = useState(initialDoctors)
    const [search, setSearch] = useState("")
    const [selected, setSelected] = useState("")
    const [editingClinic, setEditingClinic] = useState("")

    const filteredUsers = users.filter((user) => `${user.name} ${user.email} ${user.role}`.toLowerCase().includes(search.toLowerCase()))
    const toggleDoctor = (name) => setDoctors((current) => current.map((doctor) => doctor.name === name ? { ...doctor, enabled: !doctor.enabled } : doctor))

    return (
        <main className="role-dashboard"><div className="dashboard-shell dashboard-shell-wide">
            <div className="dashboard-kicker">CAMILLUS ADMINISTRATION</div><div className="dashboard-title-row"><div><h1>Super Admin Dashboard</h1><p className="dashboard-intro">Review the platform&apos;s people, places, and clinical structure.</p></div><Link to="/" className="dashboard-home-link">Home</Link></div>
            <section className="dashboard-stats"><div><strong>1,248</strong><span>Total patients</span></div><div><strong>{doctors.length}</strong><span>Doctors</span></div><div><strong>86</strong><span>Appointments</span></div><div><strong>{clinics.length}</strong><span>Hospitals / clinics</span></div></section>
            <div className="dashboard-grid">
                <section className="dashboard-panel dashboard-panel-wide"><div className="panel-heading"><div><span className="panel-eyebrow">Accounts</span><h2>Users</h2></div></div><input className="dashboard-search" placeholder="Search users by name, email, or role..." value={search} onChange={(event) => setSearch(event.target.value)} /><div className="dashboard-list">{filteredUsers.length === 0 ? <p className="empty-state">No users found.</p> : filteredUsers.map((user) => <div className="dashboard-list-item compact-list-item" key={user.email}><div><strong>{user.name}</strong><span>{user.email} · {user.role}</span></div><button type="button" className="text-button" onClick={() => setSelected(selected === user.email ? "" : user.email)}>{selected === user.email ? "Close" : "View"}</button></div>)}</div></section>
                <section className="dashboard-panel"><div className="panel-heading"><div><span className="panel-eyebrow">Clinical team</span><h2>Doctors</h2></div></div><div className="dashboard-list">{doctors.map((doctor) => <div className="dashboard-list-item compact-list-item" key={doctor.name}><div><strong>{doctor.name}</strong><span>{doctor.specialty}</span></div><div className="inline-actions"><button type="button" className="text-button" onClick={() => setSelected(`doctor:${doctor.name}`)}>{selected === `doctor:${doctor.name}` ? "Selected" : "View"}</button><button type="button" className="text-button" onClick={() => setSelected(`edit:${doctor.name}`)}>Edit</button><button type="button" className={`text-button ${doctor.enabled ? "danger-text" : ""}`} onClick={() => toggleDoctor(doctor.name)}>{doctor.enabled ? "Disable" : "Enable"}</button></div></div>)}{selected?.startsWith("doctor:") && <div className="detail-callout">Viewing doctor details for <strong>{selected.replace("doctor:", "")}</strong></div>}{selected?.startsWith("edit:") && <div className="detail-callout">Editing profile for <strong>{selected.replace("edit:", "")}</strong> · Changes are local to this prototype.</div>}</div></section>
                <section className="dashboard-panel"><div className="panel-heading"><div><span className="panel-eyebrow">Locations</span><h2>Hospitals / clinics</h2></div></div><div className="dashboard-list">{clinics.map((clinic) => <div className="dashboard-list-item compact-list-item" key={clinic}><strong>{clinic}</strong>{editingClinic === clinic ? <button type="button" className="text-button" onClick={() => setEditingClinic("")}>Save</button> : <div className="inline-actions"><button type="button" className="text-button" onClick={() => setEditingClinic(`view:${clinic}`)}>{editingClinic === `view:${clinic}` ? "Selected" : "View"}</button><button type="button" className="text-button" onClick={() => setEditingClinic(clinic)}>Edit</button></div>}</div>)}{editingClinic?.startsWith("view:") && <div className="detail-callout">Viewing clinic details for <strong>{editingClinic.replace("view:", "")}</strong></div>}</div></section>
                <section className="dashboard-panel"><div className="panel-heading"><div><span className="panel-eyebrow">Clinical structure</span><h2>Departments</h2></div></div><div className="dashboard-list">{departments.map((department) => <div className="dashboard-list-item compact-list-item" key={department.name}><strong>{department.name}</strong><span>{department.doctors} associated doctors</span></div>)}</div></section>
            </div>
        </div></main>
    )
}

export default AdminDashboard
