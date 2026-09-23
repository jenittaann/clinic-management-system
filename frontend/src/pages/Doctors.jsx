import { useState } from "react"
import { Link } from "react-router-dom"

function Doctors() {
    const doctors = [
        {
            name: "Dr. Ananya Sharma",
            specialty: "Cardiologist",
            hospital: "Camillus Medical Centre",
            availability: "Available Today",
        },
        {
            name: "Dr. Rahul Menon",
            specialty: "General Physician",
            hospital: "City Care Hospital",
            availability: "Available Tomorrow",
        },
        {
            name: "Dr. Priya Nair",
            specialty: "Dermatologist",
            hospital: "Camillus Medical Centre",
            availability: "Available Today",
        },
    ]

    const [search, setSearch] = useState("")
    const [specialty, setSpecialty] = useState("All Specialties")

    const filteredDoctors = doctors.filter((doctor) => {
        const matchesSearch =
            doctor.name.toLowerCase().includes(search.toLowerCase()) ||
            doctor.specialty.toLowerCase().includes(search.toLowerCase())

        const matchesSpecialty =
            specialty === "All Specialties" ||
            doctor.specialty === specialty

        return matchesSearch && matchesSpecialty
    })

    return (
        <div className="doctors-page">

            <div className="doctors-header">
                <h1>Find a Doctor</h1>
                <p>
                    Search for doctors, explore their specialties,
                    and check their availability.
                </p>
            </div>

            <div className="doctor-search">

                <input
                    type="text"
                    placeholder="Search doctors or specialties..."
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                />

                <select
                    value={specialty}
                    onChange={(e) => setSpecialty(e.target.value)}
                >
                    <option>All Specialties</option>
                    <option>Cardiologist</option>
                    <option>General Physician</option>
                    <option>Dermatologist</option>
                </select>

            </div>

            <div className="doctor-grid">

                {filteredDoctors.length > 0 ? (
                    filteredDoctors.map((doctor, index) => (
                        <div className="doctor-card" key={index}>

                            <div className="doctor-avatar">
                                {doctor.name.charAt(4)}
                            </div>

                            <h2>{doctor.name}</h2>

                            <h3>{doctor.specialty}</h3>

                            <p>{doctor.hospital}</p>

                            <p className="availability">
                                ● {doctor.availability}
                            </p>

                            <div className="doctor-actions">
                                <Link
                                    to={`/doctors/${doctor.name
                                        .toLowerCase()
                                        .replace("dr. ", "")
                                        .replaceAll(" ", "-")}`}
                                    className="profile-btn"
                                >
                                    View Profile
                                </Link>

                                <Link
                                    to={`/appointments/${doctor.name
                                        .toLowerCase()
                                        .replace("dr. ", "")
                                        .replaceAll(" ", "-")}`}
                                    className="book-btn"
                                >
                                    Book Appointment
                                </Link>
                            </div>

                        </div>
                    ))
                ) : (
                    <p>No doctors found.</p>
                )}

            </div>

        </div>
    )
}

export default Doctors