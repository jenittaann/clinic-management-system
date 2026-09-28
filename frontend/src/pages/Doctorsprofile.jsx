import { useParams, Link } from "react-router-dom"
import BackButton from "../components/BackButton"

function DoctorProfile() {
    const { name } = useParams()

    const doctors = {
        "ananya-sharma": {
            name: "Dr. Ananya Sharma",
            specialty: "Cardiologist",
            hospital: "Camillus Medical Centre",
            experience: "8 years",
            availability: "Available Today",
            about:
                "Dr. Ananya Sharma specializes in cardiovascular care and provides consultation and preventive healthcare services.",
        },

        "rahul-menon": {
            name: "Dr. Rahul Menon",
            specialty: "General Physician",
            hospital: "City Care Hospital",
            experience: "6 years",
            availability: "Available Tomorrow",
            about:
                "Dr. Rahul Menon provides general medical consultations and routine healthcare services for patients.",
        },

        "priya-nair": {
            name: "Dr. Priya Nair",
            specialty: "Dermatologist",
            hospital: "Camillus Medical Centre",
            experience: "7 years",
            availability: "Available Today",
            about:
                "Dr. Priya Nair specializes in dermatological care and provides consultation for common skin conditions.",
        },
    }

    const doctor = doctors[name]

    if (!doctor) {
        return <h1>Doctor not found</h1>
    }

    return (
        <div className="doctor-profile">
            <BackButton />

            <div className="profile-header">

                <div className="profile-avatar">
                    {doctor.name.charAt(4)}
                </div>

                <div>
                    <h1>{doctor.name}</h1>
                    <h2>{doctor.specialty}</h2>
                    <p>{doctor.hospital}</p>
                </div>

            </div>

            <div className="profile-info">

                <div>
                    <strong>Experience</strong>
                    <p>{doctor.experience}</p>
                </div>

                <div>
                    <strong>Availability</strong>
                    <p>{doctor.availability}</p>
                </div>

            </div>

            <div className="profile-about">
                <h2>About</h2>
                <p>{doctor.about}</p>
            </div>

            <Link
                to={`/appointments/${name}`}
                className="book-appointment-btn"
            >
                Book Appointment
            </Link>

        </div>
    )
}

export default DoctorProfile