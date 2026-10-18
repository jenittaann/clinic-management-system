import { useEffect, useState } from "react"
import { Link } from "react-router-dom"
import BackButton from "../components/BackButton"
import { getDoctors, getDoctorSchedule } from "../api/clinic"
import { getSelectedHospital } from "../utils/selectedHospital"

function slug(name) {
    return name.toLowerCase().replace("dr. ", "").replaceAll(" ", "-")
}

function Doctors() {
    const [doctors, setDoctors] = useState([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState("")
    const [search, setSearch] = useState("")
    const [specialty, setSpecialty] = useState("All Specialties")

    useEffect(() => {
        let active = true
        getDoctors(getSelectedHospital()?.id)
            .then(async (items) => {
                const withSchedules = await Promise.all(items.map(async (doctor) => {
                    try { return { ...doctor, schedule: await getDoctorSchedule(doctor.id) } }
                    catch (scheduleError) {
                        return { ...doctor, schedule: { error: scheduleError.message || "Availability could not be checked." } }
                    }
                }))
                if (active) setDoctors(withSchedules)
            })
            .catch((requestError) => active && setError(requestError.message || "Unable to load doctors."))
            .finally(() => active && setLoading(false))
        return () => { active = false }
    }, [])

    const filteredDoctors = doctors.filter((doctor) => {
        const matchesSearch = doctor.name.toLowerCase().includes(search.toLowerCase()) ||
            (doctor.specialization || "").toLowerCase().includes(search.toLowerCase())
        return matchesSearch && (specialty === "All Specialties" || doctor.specialization === specialty)
    })

    return (
        <div className="doctors-page">
            <BackButton />
            <div className="doctors-header">
                <h1>Find a Doctor</h1>
                <p>Search for doctors, explore their specialties, and check their availability.</p>
            </div>
            <div className="doctor-search">
                <input type="text" placeholder="Search doctors or specialties..." value={search} onChange={(event) => setSearch(event.target.value)} />
                <select value={specialty} onChange={(event) => setSpecialty(event.target.value)}>
                    <option>All Specialties</option>
                    {[...new Set(doctors.map((doctor) => doctor.specialization).filter(Boolean))].map((item) => <option key={item}>{item}</option>)}
                </select>
            </div>
            {loading ? <p role="status">Loading doctors…</p> : error ? <p className="login-error" role="alert">{error}</p> : filteredDoctors.length ? (
                <div className="doctor-grid">
                    {filteredDoctors.map((doctor) => {
                        const doctorSlug = slug(doctor.name)
                        const bookingDetails = { doctorId: doctor.id, doctor: doctor.name, hospital: doctor.hospitalName, hospitalId: doctor.hospitalId, department: doctor.departmentName }
                        return <div className="doctor-card" key={doctor.id}>
                            <div className="doctor-avatar">{doctor.name.charAt(4)}</div>
                            <h2>{doctor.name}</h2>
                            <h3>{doctor.specialization || "General practice"}</h3>
                            <p>{doctor.hospitalName}</p>
                            <div className="availability">{doctor.schedule?.availableSlots?.length ? <>
                                <span>Available soon:</span>
                                <div className="availability-slots">{doctor.schedule.availableSlots.slice(0, 5).map((slot) => <Link className="availability-slot" key={`${slot.date}-${slot.startTime}`} to={`/appointments/${doctorSlug}`} state={{ bookingDetails, selectedSlot: slot }}>{slot.date} {slot.startTime.slice(0, 5)}</Link>)}</div>
                            </> : doctor.schedule?.error ? <span role="alert">{doctor.schedule.error}</span> : <span>No available appointments in the next 7 days</span>}</div>
                            <div className="doctor-actions">
                                <Link to={`/doctors/${doctorSlug}`} className="profile-btn" state={{ doctor }}>View Profile</Link>
                                <Link to={`/appointments/${doctorSlug}`} className="book-btn" state={{ bookingDetails }}>Book Appointment</Link>
                            </div>
                        </div>
                    })}
                </div>
            ) : <p>No doctors found.</p>}
        </div>
    )
}

export default Doctors
