import { useState } from "react"
import { Link } from "react-router-dom"

const initialAppointments = [
    { id: 1, doctor: "Dr. Ananya Sharma", specialty: "Cardiologist", date: "Today, 10:30 AM", status: "Confirmed" },
    { id: 2, doctor: "Dr. Priya Nair", specialty: "Dermatologist", date: "18 Oct, 2:00 PM", status: "Pending" },
]

const history = [
    { doctor: "Dr. Rahul Menon", date: "12 Sep 2026", status: "Completed" },
    { doctor: "Dr. Ananya Sharma", date: "24 Aug 2026", status: "Completed" },
]

function PatientDashboard() {
    const [appointments, setAppointments] = useState(initialAppointments)
    const [selectedAppointment, setSelectedAppointment] = useState(null)

    function updateAppointment(id, status) {
        setAppointments((current) => current.map((appointment) => (
            appointment.id === id ? { ...appointment, status } : appointment
        )))
    }

    return (
        <main className="role-dashboard">
            <div className="dashboard-shell dashboard-shell-wide">
                <div className="dashboard-kicker">CAMILLUS PATIENT PORTAL</div>
                <div className="dashboard-title-row">
                    <div>
                        <h1>Patient Dashboard</h1>
                        <p className="dashboard-intro">Keep track of your care, appointments, and clinic history.</p>
                    </div>
                    <Link to="/" className="dashboard-home-link">Home</Link>
                </div>

                <div className="dashboard-grid patient-dashboard-grid">
                    <section className="dashboard-panel dashboard-panel-wide">
                        <div className="panel-heading">
                            <div><span className="panel-eyebrow">Your schedule</span><h2>Upcoming appointments</h2></div>
                            <Link to="/book-appointment" className="dashboard-action">Book new</Link>
                        </div>
                        <div className="dashboard-list">
                            {appointments.length === 0 ? <p className="empty-state">No upcoming appointments.</p> : appointments.map((appointment) => (
                                <div className="dashboard-list-item" key={appointment.id}>
                                    <div><strong>{appointment.doctor}</strong><span>{appointment.specialty} · {appointment.date}</span></div>
                                    <span className={`status-badge status-${appointment.status.toLowerCase()}`}>{appointment.status}</span>
                                    <div className="inline-actions">
                                        <button type="button" className="text-button" onClick={() => setSelectedAppointment(selectedAppointment?.id === appointment.id ? null : appointment)}>View</button>
                                        <button type="button" className="text-button" onClick={() => updateAppointment(appointment.id, "Rescheduled")}>Reschedule</button>
                                        <button type="button" className="text-button danger-text" onClick={() => updateAppointment(appointment.id, "Cancelled")}>Cancel</button>
                                    </div>
                                </div>
                            ))}
                            {selectedAppointment && <div className="detail-callout"><strong>{selectedAppointment.doctor}</strong><span>{selectedAppointment.specialty} · {selectedAppointment.date} · {selectedAppointment.status}</span></div>}
                        </div>
                    </section>

                    <section className="dashboard-panel action-panel">
                        <span className="panel-eyebrow">Care options</span>
                        <h2>Find a doctor</h2>
                        <p>Browse specialties and availability at Camillus.</p>
                        <Link to="/doctors" className="dashboard-button">Find a doctor</Link>
                    </section>

                    <section className="dashboard-panel action-panel">
                        <span className="panel-eyebrow">Next visit</span>
                        <h2>Book appointment</h2>
                        <p>Choose a convenient date and time for your next visit.</p>
                        <Link to="/book-appointment" className="dashboard-button">Book appointment</Link>
                    </section>

                    <section className="dashboard-panel dashboard-panel-wide">
                        <div className="panel-heading"><div><span className="panel-eyebrow">Past care</span><h2>Appointment history</h2></div></div>
                        <div className="dashboard-list">
                            {history.map((appointment) => <div className="dashboard-list-item compact-list-item" key={`${appointment.doctor}-${appointment.date}`}><div><strong>{appointment.doctor}</strong><span>{appointment.date}</span></div><span className="status-badge status-completed">{appointment.status}</span></div>)}
                        </div>
                    </section>
                </div>

            </div>
        </main>
    )
}

export default PatientDashboard
