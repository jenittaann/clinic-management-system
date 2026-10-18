import { useEffect, useState } from "react"
import { Link } from "react-router-dom"
import BackButton from "../components/BackButton"
import AccountAccess from "../components/AccountAccess"
import {
    cancelAppointment,
    createDoctorAvailability,
    deleteDoctorAvailability,
    getDoctorAppointments,
    getDoctorPatients,
    getDoctorSchedule,
    getMyDoctor,
    setDoctorAppointmentDuration,
    updateDoctorAvailability,
    updateMyDoctor,
} from "../api/clinic"
import { explainIdentityError } from "../utils/authBoundary"
import AIChatbox from "../components/AIChatbox"

const today = () => new Date().toLocaleDateString("en-CA", { timeZone: "Asia/Kolkata" })
const nextSevenDays = () => {
    const firstDay = new Date(`${today()}T00:00:00.000Z`)
    return Array.from({ length: 7 }, (_, offset) => {
        const date = new Date(firstDay)
        date.setUTCDate(date.getUTCDate() + offset)
        return date.toISOString().slice(0, 10)
    })
}
const weekdays = ["", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
const formatDate = (value) => new Intl.DateTimeFormat("en-IN", {
    dateStyle: "medium", timeStyle: "short", timeZone: "Asia/Kolkata",
}).format(new Date(value))

async function loadDashboardData() {
    const doctorProfile = await getMyDoctor()
    const [visitLists, records, doctorSchedule] = await Promise.all([
        Promise.all(nextSevenDays().map((serviceDate) => getDoctorAppointments(doctorProfile.id, serviceDate))),
        getDoctorPatients(),
        getDoctorSchedule(doctorProfile.id),
    ])
    return { doctorProfile, appointments: visitLists.flat(), patients: records, schedule: doctorSchedule }
}

function DoctorDashboard() {
    const [doctor, setDoctor] = useState(null)
    const [profile, setProfile] = useState({ name: "", specialization: "", experienceYears: "" })
    const [schedule, setSchedule] = useState(null)
    const [duration, setDuration] = useState("30")
    const [rule, setRule] = useState({ dayOfWeek: "1", startTime: "09:00", endTime: "17:00" })
    const [editingAvailabilityId, setEditingAvailabilityId] = useState("")
    const [appointments, setAppointments] = useState([])
    const [patients, setPatients] = useState([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState("")
    const [message, setMessage] = useState("")
    const [query, setQuery] = useState("")
    const [busyId, setBusyId] = useState("")

    async function refresh() {
        setLoading(true)
        setError("")
        try {
            const { doctorProfile, appointments: visitAppointments, patients: records, schedule: doctorSchedule } = await loadDashboardData()
            setDoctor(doctorProfile)
            setProfile({
                name: doctorProfile.name || "",
                specialization: doctorProfile.specialization || "",
                experienceYears: doctorProfile.experienceYears ?? "",
            })
            setAppointments(visitAppointments)
            setPatients(records)
            setSchedule(doctorSchedule)
            setDuration(String(doctorSchedule.appointmentDurationMinutes || 30))
        } catch (requestError) {
            setError(explainIdentityError(requestError, "doctor", "Unable to load clinic records."))
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        let active = true
        loadDashboardData()
            .then(({ doctorProfile, appointments: visitAppointments, patients: records, schedule: doctorSchedule }) => {
                if (!active) return
                setDoctor(doctorProfile)
                setProfile({
                    name: doctorProfile.name || "",
                    specialization: doctorProfile.specialization || "",
                    experienceYears: doctorProfile.experienceYears ?? "",
                })
                setAppointments(visitAppointments)
                setPatients(records)
                setSchedule(doctorSchedule)
                setDuration(String(doctorSchedule.appointmentDurationMinutes || 30))
            })
            .catch((requestError) => {
                if (active) setError(explainIdentityError(requestError, "doctor", "Unable to load clinic records."))
            })
            .finally(() => {
                if (active) setLoading(false)
            })
        return () => { active = false }
    }, [])

    async function saveProfile(event) {
        event.preventDefault()
        setError("")
        setMessage("")
        try {
            const saved = await updateMyDoctor({
                name: profile.name.trim(),
                specialization: profile.specialization.trim() || null,
                experienceYears: profile.experienceYears === "" ? null : Number(profile.experienceYears),
            })
            setDoctor(saved)
            setProfile({ name: saved.name, specialization: saved.specialization || "", experienceYears: saved.experienceYears ?? "" })
            setMessage("Doctor profile saved.")
        } catch (requestError) {
            setError(explainIdentityError(requestError, "doctor", "Unable to save doctor profile."))
        }
    }

    async function saveDuration(event) {
        event.preventDefault()
        setError("")
        setMessage("")
        try {
            const updated = await setDoctorAppointmentDuration(doctor.id, Number(duration))
            setSchedule(updated)
            setMessage("Appointment duration saved.")
        } catch (requestError) {
            setError(explainIdentityError(requestError, "doctor", "Unable to save appointment duration."))
        }
    }

    async function saveAvailability(event) {
        event.preventDefault()
        setError("")
        setMessage("")
        try {
            const request = { ...rule, dayOfWeek: Number(rule.dayOfWeek) }
            if (editingAvailabilityId) await updateDoctorAvailability(doctor.id, editingAvailabilityId, request)
            else await createDoctorAvailability(doctor.id, request)
            setSchedule(await getDoctorSchedule(doctor.id))
            setEditingAvailabilityId("")
            setMessage("Availability saved.")
        } catch (requestError) {
            setError(explainIdentityError(requestError, "doctor", "Unable to save availability."))
        }
    }

    function editAvailability(item) {
        setEditingAvailabilityId(item.availabilityId)
        setRule({ dayOfWeek: String(item.dayOfWeek), startTime: item.startTime.slice(0, 5), endTime: item.endTime.slice(0, 5) })
    }

    async function removeAvailability(availabilityId) {
        setError("")
        setMessage("")
        try {
            await deleteDoctorAvailability(doctor.id, availabilityId)
            setSchedule(await getDoctorSchedule(doctor.id))
            if (editingAvailabilityId === availabilityId) setEditingAvailabilityId("")
            setMessage("Availability removed.")
        } catch (requestError) {
            setError(explainIdentityError(requestError, "doctor", "Unable to remove availability."))
        }
    }

    async function cancel(appointmentId) {
        setBusyId(appointmentId)
        setError("")
        try {
            await cancelAppointment(appointmentId)
            await refresh()
        } catch (requestError) {
            setError(explainIdentityError(requestError, "doctor", "Unable to cancel appointment."))
        } finally {
            setBusyId("")
        }
    }

    const filteredPatients = patients.filter((patient) => patient.email.toLowerCase().includes(query.toLowerCase()))

    return <main className="role-dashboard">
        <div className="dashboard-shell dashboard-shell-wide">
            <div className="dashboard-kicker">CAMILLUS CLINIC TEAM</div>
            <div className="dashboard-title-row">
                <div><h1>Doctor Dashboard</h1><p className="dashboard-intro">Manage your clinic schedule and patient care from one place.</p></div>
                <div className="dashboard-header-links"><BackButton className="back-button-inline" /><Link to="/" className="dashboard-home-link">Home</Link><Link to="/settings" state={{ from: "/doctor-dashboard" }} className="dashboard-home-link">Settings</Link></div>
            </div>
            <AccountAccess role="DOCTOR" />
            {error && <p className="login-error" role="alert">{error}</p>}
            <div className="dashboard-grid">
                <section className="dashboard-panel">
                    <div className="panel-heading"><div><span className="panel-eyebrow">Professional details</span><h2>Doctor profile</h2></div></div>
                    {loading ? <p role="status">Loading profile…</p> : doctor ? <>
                        <div className="profile-summary"><strong>{doctor.name}</strong><span>{doctor.specialization || "General practice"} · {doctor.departmentName}</span><span>{doctor.hospitalName}</span></div>
                        <form className="dashboard-form" onSubmit={saveProfile}>
                            <label>Name<input required maxLength={200} value={profile.name} onChange={(event) => setProfile((current) => ({ ...current, name: event.target.value }))} /></label>
                            <label>Specialization<input maxLength={160} value={profile.specialization} onChange={(event) => setProfile((current) => ({ ...current, specialization: event.target.value }))} /></label>
                            <label>Experience (years)<input type="number" min="0" step="1" value={profile.experienceYears} onChange={(event) => setProfile((current) => ({ ...current, experienceYears: event.target.value }))} /></label>
                            <button type="submit" className="dashboard-button">Save profile</button>
                        </form>
                    </> : <p className="empty-state">No doctor profile is linked to this account. Ask an administrator to link the existing profile.</p>}
                </section>

                <section className="dashboard-panel dashboard-panel-wide">
                    <div className="panel-heading"><div><span className="panel-eyebrow">Next 7 days</span><h2>Appointments</h2></div><span className="count-badge">{appointments.length}</span></div>
                    {loading ? <p role="status">Loading appointments…</p> : <div className="dashboard-list">{appointments.length ? appointments.map((appointment) => <div className="dashboard-list-item" key={appointment.appointmentId}>
                        <div><strong>{appointment.doctorName}</strong><span>{appointment.hospitalName} · {formatDate(appointment.startsAt)}</span></div>
                        <span className={`status-badge status-${appointment.status.toLowerCase()}`}>{appointment.status}</span>
                        {!(["COMPLETED", "CANCELLED", "NO_SHOW"].includes(appointment.status)) && <button type="button" className="text-button danger-text" disabled={busyId === appointment.appointmentId} onClick={() => cancel(appointment.appointmentId)}>Cancel</button>}
                    </div>) : <p className="empty-state">No appointments scheduled in the next 7 days.</p>}</div>}
                </section>

                <section className="dashboard-panel dashboard-panel-wide">
                    <div className="panel-heading"><div><span className="panel-eyebrow">Availability</span><h2>Manage your schedule</h2></div></div>
                    {schedule ? <>
                        <div className="dashboard-list">{schedule.recurringWeeklyRules.length ? schedule.recurringWeeklyRules.map((item) => <div className="dashboard-list-item compact-list-item" key={item.availabilityId}>
                            <strong>{weekdays[item.dayOfWeek]}</strong><span>{item.startTime.slice(0, 5)}–{item.endTime.slice(0, 5)}</span>
                            <button type="button" className="text-button" onClick={() => editAvailability(item)}>Edit</button>
                            <button type="button" className="text-button danger-text" onClick={() => removeAvailability(item.availabilityId)}>Delete</button>
                        </div>) : <p className="empty-state">No weekly availability set.</p>}</div>
                        <div className="dashboard-grid">
                            <form className="dashboard-form" onSubmit={saveAvailability}>
                                <label>Day<select value={rule.dayOfWeek} onChange={(event) => setRule((current) => ({ ...current, dayOfWeek: event.target.value }))}>{weekdays.slice(1).map((day, index) => <option key={day} value={index + 1}>{day}</option>)}</select></label>
                                <label>Start time<input type="time" required value={rule.startTime} onChange={(event) => setRule((current) => ({ ...current, startTime: event.target.value }))} /></label>
                                <label>End time<input type="time" required value={rule.endTime} onChange={(event) => setRule((current) => ({ ...current, endTime: event.target.value }))} /></label>
                                <button className="dashboard-button" type="submit">{editingAvailabilityId ? "Save availability" : "Add availability"}</button>
                                {editingAvailabilityId && <button type="button" className="text-button" onClick={() => setEditingAvailabilityId("")}>Cancel edit</button>}
                            </form>
                            <form className="dashboard-form" onSubmit={saveDuration}>
                                <label>Appointment duration (minutes)<input type="number" min="1" max="240" required value={duration} onChange={(event) => setDuration(event.target.value)} /></label>
                                <button className="dashboard-button" type="submit">Save duration</button>
                            </form>
                        </div>
                    </> : loading ? <p role="status">Loading schedule…</p> : <p className="empty-state">Schedule management requires a linked doctor profile.</p>}
                    {message && <p role="status">{message}</p>}
                </section>

                <section className="dashboard-panel dashboard-panel-wide">
                    <div className="panel-heading"><div><span className="panel-eyebrow">Care team</span><h2>Patients with appointments</h2></div><span className="count-badge">{patients.length}</span></div>
                    <input className="dashboard-search" placeholder="Search patients by email..." value={query} onChange={(event) => setQuery(event.target.value)} />
                    {loading ? <p role="status">Loading patients…</p> : <div className="dashboard-list">{filteredPatients.length ? filteredPatients.map((patient) => <div className="dashboard-list-item compact-list-item" key={patient.patientId}><div><strong>{patient.email}</strong><span>{patient.phone || "No phone on file"}</span></div></div>) : <p className="empty-state">No patients found.</p>}</div>}
                </section>
            </div>
        </div>
        <AIChatbox />
    </main>
}

export default DoctorDashboard
