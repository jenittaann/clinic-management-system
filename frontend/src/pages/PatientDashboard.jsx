import { useCallback, useEffect, useState } from "react"
import { Link } from "react-router-dom"
import BackButton from "../components/BackButton"
import { cancelAppointment, getMyAppointments, getMyPatientProfile, updateMyPatientProfile } from "../api/clinic"
import { explainIdentityError } from "../utils/authBoundary"
import AccountAccess from "../components/AccountAccess"
import AIChatbox from "../components/AIChatbox"

function formatDate(value) {
    return new Intl.DateTimeFormat("en-IN", { dateStyle: "medium", timeStyle: "short", timeZone: "Asia/Kolkata" }).format(new Date(value))
}

function PatientDashboard() {
    const [appointments, setAppointments] = useState([])
    const [appointmentsLoading, setAppointmentsLoading] = useState(true)
    const [appointmentsError, setAppointmentsError] = useState("")
    const [profile, setProfile] = useState(null)
    const [profileLoading, setProfileLoading] = useState(true)
    const [profileError, setProfileError] = useState("")
    const [profileMessage, setProfileMessage] = useState("")
    const [phone, setPhone] = useState("")
    const [dateOfBirth, setDateOfBirth] = useState("")
    const [selectedAppointment, setSelectedAppointment] = useState(null)
    const [busyId, setBusyId] = useState("")

    const refreshAppointments = useCallback(async () => {
        setAppointmentsLoading(true)
        setAppointmentsError("")
        try {
            setAppointments(await getMyAppointments())
        } catch (error) {
            setAppointmentsError(explainIdentityError(error, "patient", "Unable to load appointments."))
        } finally {
            setAppointmentsLoading(false)
        }
    }, [])

    useEffect(() => {
        refreshAppointments()
        getMyPatientProfile()
            .then((record) => {
                setProfile(record)
                setPhone(record.phone || "")
                setDateOfBirth(record.dateOfBirth || "")
            })
            .catch((error) => setProfileError(explainIdentityError(error, "patient", "Unable to load your patient profile.")))
            .finally(() => setProfileLoading(false))
    }, [refreshAppointments])

    async function saveProfile(event) {
        event.preventDefault()
        setProfileMessage("")
        try {
            const saved = await updateMyPatientProfile({ phone: phone || null, dateOfBirth: dateOfBirth || null })
            setProfile(saved)
            setProfileMessage("Patient profile saved.")
        } catch (error) {
            setProfileError(explainIdentityError(error, "patient", "Unable to save your patient profile."))
        }
    }

    async function cancel(id) {
        setBusyId(id)
        setAppointmentsError("")
        try {
            await cancelAppointment(id)
            await refreshAppointments()
        } catch (error) {
            setAppointmentsError(explainIdentityError(error, "patient", "Unable to cancel this appointment."))
        } finally {
            setBusyId("")
        }
    }

    const upcoming = appointments.filter((item) => !["COMPLETED", "CANCELLED", "NO_SHOW"].includes(item.status))
    const history = appointments.filter((item) => ["COMPLETED", "CANCELLED", "NO_SHOW"].includes(item.status))

    return (
        <main className="role-dashboard">
            <div className="dashboard-shell dashboard-shell-wide">
                <div className="dashboard-kicker">CAMILLUS PATIENT PORTAL</div>
                <div className="dashboard-title-row">
                    <div><h1>Patient Dashboard</h1><p className="dashboard-intro">Keep track of your care, appointments, and clinic history.</p></div>
                    <div className="dashboard-header-links"><BackButton className="back-button-inline" /><Link to="/" className="dashboard-home-link">Home</Link><Link to="/settings" state={{ from: "/patient-dashboard" }} className="dashboard-home-link">Settings</Link></div>
                </div>
                <AccountAccess role="PATIENT" />
                <div className="dashboard-grid patient-dashboard-grid">
                    <section className="dashboard-panel dashboard-panel-wide">
                        <div className="panel-heading"><div><span className="panel-eyebrow">Your schedule</span><h2>Upcoming appointments</h2></div><Link to="/doctors" className="dashboard-action">Book new</Link></div>
                        {appointmentsLoading ? <p role="status">Loading appointments…</p> : appointmentsError ? <p className="login-error" role="alert">{appointmentsError}</p> : <div className="dashboard-list">
                            {upcoming.length === 0 ? <p className="empty-state">No upcoming appointments.</p> : upcoming.map((appointment) => <div className="dashboard-list-item" key={appointment.appointmentId}>
                                <div><strong>{appointment.doctorName}</strong><span>{appointment.hospitalName} · {formatDate(appointment.startsAt)}</span></div>
                                <span className={`status-badge status-${appointment.status.toLowerCase()}`}>{appointment.status}</span>
                                <div className="inline-actions">
                                    <button type="button" className="text-button" onClick={() => setSelectedAppointment(selectedAppointment?.appointmentId === appointment.appointmentId ? null : appointment)}>View</button>
                                    {appointment.status === "CONFIRMED" && <Link className="text-button" to={`/book-appointment?rescheduleId=${encodeURIComponent(appointment.appointmentId)}`} state={{ bookingDetails: { doctorId: appointment.doctorId, doctor: appointment.doctorName, hospital: appointment.hospitalName, hospitalId: appointment.hospitalId, department: appointment.departmentName } }}>Reschedule</Link>}
                                    {appointment.status !== "IN_PROGRESS" && <button type="button" className="text-button danger-text" disabled={busyId === appointment.appointmentId} onClick={() => cancel(appointment.appointmentId)}>Cancel</button>}
                                </div>
                            </div>)}
                            {selectedAppointment && <div className="detail-callout"><strong>{selectedAppointment.doctorName}</strong><span>{selectedAppointment.departmentName} · {selectedAppointment.hospitalName} · {formatDate(selectedAppointment.startsAt)} · {selectedAppointment.status}</span></div>}
                        </div>}
                    </section>
                    <section className="dashboard-panel action-panel"><span className="panel-eyebrow">Care options</span><h2>Find a doctor</h2><p>Browse doctors and specialties at your selected hospital.</p><Link to="/doctors" className="dashboard-button">Find a doctor</Link></section>
                    <section className="dashboard-panel action-panel"><span className="panel-eyebrow">Patient record</span><h2>Your profile</h2>{profileLoading ? <p role="status">Loading patient profile…</p> : profileError ? <p className="login-error" role="alert">{profileError}</p> : <form className="dashboard-form" onSubmit={saveProfile}>
                        <label>Email<input value={profile?.email || ""} readOnly /></label>
                        <label>Phone<input maxLength={32} value={phone} onChange={(event) => setPhone(event.target.value)} /></label>
                        <label>Date of birth<input type="date" max={new Date().toISOString().slice(0, 10)} value={dateOfBirth} onChange={(event) => setDateOfBirth(event.target.value)} /></label>
                        {profileMessage && <p role="status">{profileMessage}</p>}
                        <button type="submit" className="dashboard-button">Save profile</button>
                    </form>}</section>
                    <section className="dashboard-panel dashboard-panel-wide"><div className="panel-heading"><div><span className="panel-eyebrow">Past care</span><h2>Appointment history</h2></div></div>
                        {appointmentsLoading ? <p role="status">Loading appointment history…</p> : appointmentsError ? <p className="login-error" role="alert">{appointmentsError}</p> : <div className="dashboard-list">{history.length === 0 ? <p className="empty-state">No appointment history.</p> : history.map((appointment) => <div className="dashboard-list-item compact-list-item" key={appointment.appointmentId}><div><strong>{appointment.doctorName}</strong><span>{appointment.hospitalName} · {formatDate(appointment.startsAt)}</span></div><span className={`status-badge status-${appointment.status.toLowerCase()}`}>{appointment.status}</span></div>)}</div>}
                    </section>
                </div>
            </div>
            <AIChatbox />
        </main>
    )
}

export default PatientDashboard
