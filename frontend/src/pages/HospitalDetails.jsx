import { useEffect, useState } from "react"
import { useNavigate, useParams } from "react-router-dom"
import BackButton from "../components/BackButton"
import { fetchHospitals } from "../api/hospitals"
import { availabilitySlots, departments, doctors, getNextAppointmentDate, hospitals as fixtureHospitals } from "../data/hospitals"
import { saveSelectedHospital } from "../utils/selectedHospital"
import "./Hospitals.css"

function HospitalDetails() {
    const { hospitalId } = useParams()
    const navigate = useNavigate()
    const legacyHospital = fixtureHospitals.find((item) => item.id === hospitalId)
    const [apiHospitals, setApiHospitals] = useState([])
    const [requestState, setRequestState] = useState({ route: null, status: "loading", error: "" })
    const [reloadKey, setReloadKey] = useState(0)
    const [selectedDepartmentId, setSelectedDepartmentId] = useState("")
    const [selectedDoctorId, setSelectedDoctorId] = useState("")
    const [selectedSlotId, setSelectedSlotId] = useState("")

    useEffect(() => {
        if (legacyHospital) {
            return undefined
        }

        let isCurrent = true
        fetchHospitals()
            .then((records) => {
                if (isCurrent) {
                    setApiHospitals(records)
                    setRequestState({ route: hospitalId, status: "success", error: "" })
                }
            })
            .catch((requestError) => {
                if (isCurrent) {
                    setRequestState({
                        route: hospitalId,
                        status: "error",
                        error: requestError.message || "Unable to load hospital details.",
                    })
                }
            })

        return () => {
            isCurrent = false
        }
    }, [hospitalId, legacyHospital, reloadKey])

    const isLoading = !legacyHospital && (
        requestState.route !== hospitalId || requestState.status === "loading"
    )
    const error = requestState.route === hospitalId && requestState.status === "error"
        ? requestState.error
        : ""
    const hospital = legacyHospital || apiHospitals.find((item) => String(item.id) === hospitalId)
    const demoHospital = legacyHospital || fixtureHospitals.find((item) =>
        item.name.trim().toLocaleLowerCase("en-IN") === hospital?.name?.trim().toLocaleLowerCase("en-IN"),
    )
    const hasDemoDetails = Boolean(demoHospital)

    if (!hospital && isLoading) {
        return (
            <main className="hospitals-page">
                <BackButton />
                <p className="hospital-results-count" role="status">Loading hospital details…</p>
            </main>
        )
    }

    if (!hospital && error) {
        return (
            <main className="hospitals-page">
                <BackButton />
                <section className="hospital-not-found" role="alert">
                    <h1>Hospital details could not be loaded</h1>
                    <p>{error}</p>
                    <button type="button" className="hospital-view-button" onClick={() => {
                        setRequestState({ route: hospitalId, status: "loading", error: "" })
                        setReloadKey((key) => key + 1)
                    }}>
                        Try again
                    </button>
                </section>
            </main>
        )
    }

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

    const hospitalDepartments = hasDemoDetails
        ? departments.filter((department) => department.hospitalId === demoHospital.id)
        : []
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

    function selectCurrentHospital() {
        const selected = saveSelectedHospital(hospital)
        if (selected) navigate(`/login?hospitalId=${encodeURIComponent(selected.id)}`)
    }

    const isApiHospital = !legacyHospital && apiHospitals.some((item) => String(item.id) === hospitalId)

    return (
        <main className="hospitals-page hospital-detail-page">
            <BackButton />

            {hasDemoDetails && (
                <p className="hospital-demo-notice" role="note">
                    Demo departments, doctors, and appointment slots for this legacy fixture; these details are not loaded from the hospital API.
                </p>
            )}

            <header className="hospital-detail-hero">
                <div className="hospital-detail-icon" aria-hidden="true">✚</div>
                <div>
                    <span className="hospital-type">{hospital.type || "Type not listed"}</span>
                    <h1>{hospital.name || "Unnamed hospital"}</h1>
                    <p className="hospital-area"><span aria-hidden="true">⌖</span> {hospital.address || "Address not listed"}</p>
                    <p className="hospital-detail-description">{hospital.description || "Description not available."}</p>
                    {isApiHospital && (
                        <button type="button" className="hospital-select-button hospital-detail-select" onClick={selectCurrentHospital}>
                            Select Hospital
                        </button>
                    )}
                </div>
                <div className="hospital-beds-summary">
                    <strong>{Number.isInteger(hospital.beds) ? hospital.beds.toLocaleString("en-IN") : "Not listed"}</strong>
                    <span>Hospital beds</span>
                </div>
            </header>

            {!hasDemoDetails ? (
                <section className="hospital-departments-section">
                    <div className="hospital-section-heading">
                        <div>
                            <p className="hospitals-eyebrow">SPECIALIST CARE</p>
                            <h2>Departments</h2>
                        </div>
                        <span>{Array.isArray(hospital.departmentIds) && hospital.departmentIds.length > 0
                            ? `${hospital.departmentIds.length} department IDs`
                            : "Not listed"}</span>
                    </div>
                    <p className="hospital-empty-state">
                        Department names, doctors, and appointment availability are not provided by the current hospital API.
                    </p>
                </section>
            ) : (
            <>
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
            </>
            )}
        </main>
    )
}

export default HospitalDetails
