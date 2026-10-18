import { useEffect, useState } from "react"
import { Link, useLocation, useParams } from "react-router-dom"
import BackButton from "../components/BackButton"
import { getDoctors } from "../api/clinic"

function doctorSlug(name) {
    return name.toLowerCase().replace("dr. ", "").replaceAll(" ", "-")
}

function DoctorProfile() {
    const { name } = useParams()
    const location = useLocation()
    const [doctor, setDoctor] = useState(location.state?.doctor || null)
    const [loading, setLoading] = useState(!location.state?.doctor)
    const [error, setError] = useState("")

    useEffect(() => {
        if (doctor) return undefined
        let active = true
        getDoctors()
            .then((items) => {
                if (!active) return
                setDoctor(items.find((item) => doctorSlug(item.name) === name) || null)
            })
            .catch((requestError) => active && setError(requestError.message || "Unable to load doctor details."))
            .finally(() => active && setLoading(false))
        return () => { active = false }
    }, [doctor, name])

    if (loading) return <p role="status">Loading doctor profile…</p>
    if (error) return <p className="login-error" role="alert">{error}</p>
    if (!doctor) return <h1>Doctor not found</h1>

    const bookingDetails = { doctorId: doctor.id, doctor: doctor.name, hospital: doctor.hospitalName, hospitalId: doctor.hospitalId, department: doctor.departmentName }
    return (
        <div className="doctor-profile">
            <BackButton />
            <div className="profile-header">
                <div className="profile-avatar">{doctor.name.charAt(4)}</div>
                <div><h1>{doctor.name}</h1><h2>{doctor.specialization || "General practice"}</h2><p>{doctor.hospitalName}</p></div>
            </div>
            <div className="profile-info">
                <div><strong>Experience</strong><p>{doctor.experienceYears == null ? "Not specified" : `${doctor.experienceYears} years`}</p></div>
                <div><strong>Department</strong><p>{doctor.departmentName}</p></div>
            </div>
            <div className="profile-about"><h2>About</h2><p>Appointment times are validated against this doctor&apos;s clinic schedule.</p></div>
            <Link to={`/appointments/${name}`} state={{ bookingDetails }} className="book-appointment-btn">Book Appointment</Link>
        </div>
    )
}

export default DoctorProfile
