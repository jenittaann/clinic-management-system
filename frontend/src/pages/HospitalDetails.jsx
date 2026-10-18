import { useEffect, useMemo, useState } from "react"
import { Link, useNavigate, useParams } from "react-router-dom"
import BackButton from "../components/BackButton"
import { fetchHospitals, fetchDepartments } from "../api/hospitals"
import { getDoctors, getDoctorSchedule } from "../api/clinic"
import { getSelectedRole } from "../utils/selectedRole"
import { dashboardPath } from "../utils/presentationFlow"
import { saveSelectedHospital } from "../utils/selectedHospital"
import "./Hospitals.css"

const slug = (name) => name.toLowerCase().replace(/^dr\.\s*/, "").replaceAll(" ", "-")

function HospitalDetails() {
    const { hospitalId } = useParams()
    const navigate = useNavigate()
    const [hospital, setHospital] = useState(null)
    const [departments, setDepartments] = useState([])
    const [doctors, setDoctors] = useState([])
    const [selectedDepartmentId, setSelectedDepartmentId] = useState("")
    const [selectedDoctorId, setSelectedDoctorId] = useState("")
    const [schedules, setSchedules] = useState({})
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState("")

    useEffect(() => {
        let active = true
        setLoading(true)
        Promise.all([fetchHospitals(), fetchDepartments(hospitalId), getDoctors(hospitalId)])
            .then(([hospitals, hospitalDepartments, hospitalDoctors]) => {
                if (!active) return
                const record = hospitals.find((item) => String(item.id) === String(hospitalId))
                if (!record) throw new Error("Hospital not found.")
                setHospital(record)
                setDepartments(hospitalDepartments)
                setDoctors(hospitalDoctors)
                setSelectedDepartmentId("")
                setSelectedDoctorId("")
            })
            .catch((requestError) => active && setError(requestError.message || "Unable to load hospital details."))
            .finally(() => active && setLoading(false))
        return () => { active = false }
    }, [hospitalId])

    const departmentDoctors = useMemo(
        () => doctors.filter((doctor) => String(doctor.departmentId) === String(selectedDepartmentId)),
        [doctors, selectedDepartmentId],
    )

    useEffect(() => {
        if (!selectedDoctorId || schedules[selectedDoctorId]) return
        let active = true
        getDoctorSchedule(selectedDoctorId)
            .then((schedule) => active && setSchedules((current) => ({ ...current, [selectedDoctorId]: schedule })))
            .catch((requestError) => active && setSchedules((current) => ({ ...current, [selectedDoctorId]: { error: requestError.message } })))
        return () => { active = false }
    }, [selectedDoctorId, schedules])

    if (loading) return <main className="hospitals-page"><BackButton /><p className="hospital-results-count" role="status">Loading hospital details…</p></main>
    if (error || !hospital) return <main className="hospitals-page"><BackButton /><section className="hospital-not-found" role="alert"><h1>Hospital details could not be loaded</h1><p>{error || "Hospital not found."}</p><button type="button" className="hospital-view-button" onClick={() => navigate("/hospitals")}>Browse hospitals</button></section></main>

    function continueWithHospital() {
        const selected = saveSelectedHospital(hospital)
        const role = getSelectedRole()
        if (selected) navigate(role ? dashboardPath(role, selected.id) : "/")
    }

    return <main className="hospitals-page hospital-detail-page">
        <BackButton />
        <header className="hospital-detail-hero">
            <div className="hospital-detail-icon" aria-hidden="true">✚</div>
            <div>
                <span className="hospital-type">{hospital.type || "Type not listed"}</span>
                <h1>{hospital.name || "Unnamed hospital"}</h1>
                <p className="hospital-area">{hospital.address || hospital.area || "Location not listed"}</p>
                <p className="hospital-detail-description">{hospital.description || "Description not available."}</p>
                <button type="button" className="hospital-select-button hospital-detail-select" onClick={continueWithHospital}>Continue to dashboard</button>
            </div>
            <div className="hospital-beds-summary"><strong>{Number.isInteger(hospital.beds) ? hospital.beds.toLocaleString("en-IN") : "Not listed"}</strong><span>Hospital beds</span></div>
        </header>

        <section className="hospital-departments-section">
            <div className="hospital-section-heading"><div><p className="hospitals-eyebrow">SPECIALIST CARE</p><h2>Departments</h2></div><span>{departments.length} departments</span></div>
            {departments.length ? <div className="department-grid">{departments.map((department) => <button type="button" className={`department-card${String(selectedDepartmentId) === String(department.id) ? " is-selected" : ""}`} key={department.id} onClick={() => { setSelectedDepartmentId(department.id); setSelectedDoctorId("") }} aria-pressed={String(selectedDepartmentId) === String(department.id)}><span className="department-card-icon" aria-hidden="true">✚</span><span className="department-card-name">{department.name}</span><span className="department-card-description">{department.description || "Department at this hospital"}</span><span className="department-card-link">View doctors <span aria-hidden="true">→</span></span></button>)}</div> : <p className="hospital-empty-state">No departments are listed for this hospital.</p>}
        </section>

        {selectedDepartmentId && <section className="hospital-doctors-section"><div className="hospital-section-heading"><div><p className="hospitals-eyebrow">MEET THE SPECIALISTS</p><h2>{departments.find((item) => String(item.id) === String(selectedDepartmentId))?.name} doctors</h2></div><span>{departmentDoctors.length} doctors</span></div>
            {departmentDoctors.length ? <div className="hospital-doctor-grid">{departmentDoctors.map((doctor) => {
                const schedule = schedules[doctor.id]
                return <article className="hospital-doctor-card" key={doctor.id}><div className="hospital-doctor-avatar" aria-hidden="true">{doctor.name.split(" ").map((part) => part[0]).slice(0, 2).join("")}</div><div className="hospital-doctor-info"><h3>{doctor.name}</h3><p className="hospital-doctor-specialty">{doctor.specialization || "General practice"}</p><p>{doctor.experienceYears == null ? "Experience not listed" : `${doctor.experienceYears} years of experience`}</p><p>{hospital.name} · {doctor.departmentName}</p></div><button type="button" className="hospital-doctor-button" onClick={() => setSelectedDoctorId(selectedDoctorId === doctor.id ? "" : doctor.id)} aria-expanded={selectedDoctorId === doctor.id}>{selectedDoctorId === doctor.id ? "Hide Availability" : "View Availability"}</button>
                    {selectedDoctorId === doctor.id && <div className="doctor-availability"><h4>Available appointment slots</h4>{!schedule ? <p role="status">Loading schedule…</p> : schedule.error ? <p role="alert">{schedule.error}</p> : schedule.availableSlots.length ? <div className="availability-slots">{schedule.availableSlots.slice(0, 10).map((slot) => <Link className="availability-slot" key={`${slot.date}-${slot.startTime}`} to={`/appointments/${slug(doctor.name)}`} state={{ bookingDetails: { doctorId: doctor.id, doctor: doctor.name, hospital: doctor.hospitalName, hospitalId: doctor.hospitalId, department: doctor.departmentName }, selectedSlot: slot }}>{slot.date} {slot.startTime.slice(0, 5)}</Link>)}</div> : <p>No appointments are available in the next 7 days.</p>}<p>Availability is checked again when you book.</p></div>}
                </article>
            })}</div> : <p className="hospital-empty-state">No doctors are listed for this department.</p>}
        </section>}
    </main>
}

export default HospitalDetails
