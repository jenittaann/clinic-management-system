import { useState } from "react"
import { useNavigate, useParams } from "react-router-dom"
import BackButton from "../components/BackButton"
import { availabilitySlots, departments, doctors, getNextAppointmentDate, hospitals } from "../data/hospitals"
import "./Hospitals.css"

function HospitalDetails() {
    const { hospitalId } = useParams()
    const navigate = useNavigate()
    const hospital = hospitals.find((item) => item.id === hospitalId)
    const [selectedDepartmentId, setSelectedDepartmentId] = useState("")
    const [selectedDoctorId, setSelectedDoctorId] = useState("")
    const [selectedSlotId, setSelectedSlotId] = useState("")

    if (!hospital) {
        return (
            <main className="hospitals-page">
                <BackButton />
                <section className="hospital-not-found">
                    <h1>Hospital not found</h1>
                    <p>This hospital may have been removed or the link may be incorrect.</p>
                    <button type="button" className="hospital-view-button" onClick={() => navigate("/hospitals")}>
                        Browse hospitals
                    </button>
                </section>
            </main>
        )
    }

    const hospitalDepartments = departments.filter((department) => department.hospitalId === hospital.id)
    const selectedDepartment = hospitalDepartments.find((department) => department.id === selectedDepartmentId)
    const departmentDoctors = doctors.filter((doctor) => doctor.departmentId === selectedDepartmentId)
    const selectedDoctor = departmentDoctors.find((doctor) => doctor.id === selectedDoctorId)
    const doctorSlots = availabilitySlots.filter((slot) => slot.doctorId === selectedDoctorId)
    const selectedSlot = doctorSlots.find((slot) => slot.id === selectedSlotId)

    function selectDepartment(departmentId) {
        setSelectedDepartmentId(departmentId)
        setSelectedDoctorId("")
        setSelectedSlotId("")
    }

    function selectDoctor(doctorId) {
        setSelectedDoctorId(doctorId)
        setSelectedSlotId("")
    }

    function bookSelectedSlot() {
        if (!selectedSlot || !selectedDoctor || !selectedDepartment) return

        navigate(`/appointments/${selectedDoctor.id}`, {
            state: {
                bookingDetails: {
                    hospital: hospital.name,
                    department: selectedDepartment.name,
                    doctor: selectedDoctor.name,
                    date: getNextAppointmentDate(selectedSlot.dayOfWeek),
                    day: selectedSlot.day,
                    time: selectedSlot.time,
                },
            },
        })
    }

    return (
        <main className="hospitals-page hospital-detail-page">
            <BackButton />

            <header className="hospital-detail-hero">
                <div className="hospital-detail-icon" aria-hidden="true">✚</div>
                <div>
                    <span className="hospital-type">{hospital.type}</span>
                    <h1>{hospital.name}</h1>
                    <p className="hospital-area"><span aria-hidden="true">⌖</span> {hospital.address}</p>
                    <p className="hospital-detail-description">{hospital.description}</p>
                </div>
                <div className="hospital-beds-summary">
                    <strong>{hospital.beds.toLocaleString("en-IN")}</strong>
                    <span>Hospital beds</span>
                </div>
            </header>

            <section className="hospital-departments-section">
                <div className="hospital-section-heading">
                    <div>
                        <p className="hospitals-eyebrow">SPECIALIST CARE</p>
                        <h2>Departments</h2>
                    </div>
                    <span>{hospitalDepartments.length} departments</span>
                </div>
                <div className="department-grid">
                    {hospitalDepartments.map((department) => (
                        <button
                            type="button"
                            className={`department-card${selectedDepartmentId === department.id ? " is-selected" : ""}`}
                            key={department.id}
                            onClick={() => selectDepartment(department.id)}
                            aria-pressed={selectedDepartmentId === department.id}
                        >
                            <span className="department-card-icon" aria-hidden="true">✚</span>
                            <span className="department-card-name">{department.name}</span>
                            <span className="department-card-description">{department.description}</span>
                            <span className="department-card-link">View doctors <span aria-hidden="true">→</span></span>
                        </button>
                    ))}
                </div>
            </section>

            {selectedDepartment && (
                <section className="hospital-doctors-section">
                    <div className="hospital-section-heading">
                        <div>
                            <p className="hospitals-eyebrow">MEET THE SPECIALISTS</p>
                            <h2>{selectedDepartment.name} doctors</h2>
                        </div>
                        <span>{departmentDoctors.length} doctors</span>
                    </div>
                    <div className="hospital-doctor-grid">
                        {departmentDoctors.map((doctor) => (
                            <article className="hospital-doctor-card" key={doctor.id}>
                                <div className="hospital-doctor-avatar" aria-hidden="true">
                                    {doctor.name.replace("Dr. ", "").split(" ").map((part) => part[0]).slice(0, 2).join("")}
                                </div>
                                <div className="hospital-doctor-info">
                                    <h3>{doctor.name}</h3>
                                    <p className="hospital-doctor-specialty">{doctor.specialization}</p>
                                    <p>{doctor.experienceYears} years of experience</p>
                                    <p>{hospital.name} · {selectedDepartment.name}</p>
                                </div>
                                <button
                                    type="button"
                                    className="hospital-doctor-button"
                                    onClick={() => selectDoctor(doctor.id)}
                                    aria-expanded={selectedDoctorId === doctor.id}
                                >
                                    {selectedDoctorId === doctor.id ? "Hide Availability" : "View Availability"}
                                </button>
                                {selectedDoctorId === doctor.id && (
                                    <div className="doctor-availability">
                                        <h4>Available appointment slots</h4>
                                        {["Monday", "Tuesday"].map((day) => (
                                            <div className="availability-day" key={day}>
                                                <strong>{day}</strong>
                                                <div className="availability-slots">
                                                    {doctorSlots.filter((slot) => slot.day === day).map((slot) => (
                                                        <button
                                                            type="button"
                                                            className={`availability-slot${selectedSlotId === slot.id ? " is-selected" : ""}`}
                                                            key={slot.id}
                                                            onClick={() => setSelectedSlotId(slot.id)}
                                                            aria-pressed={selectedSlotId === slot.id}
                                                        >
                                                            {slot.time}
                                                        </button>
                                                    ))}
                                                </div>
                                            </div>
                                        ))}
                                        {selectedSlot && (
                                            <button type="button" className="hospital-book-button" onClick={bookSelectedSlot}>
                                                Book Appointment
                                            </button>
                                        )}
                                    </div>
                                )}
                            </article>
                        ))}
                    </div>
                </section>
            )}
        </main>
    )
}

export default HospitalDetails
