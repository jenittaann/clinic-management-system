import { useEffect, useState } from "react"
import { Navigate, useNavigate, useSearchParams } from "react-router-dom"
import BackButton from "../components/BackButton"
import { fetchHospitals } from "../api/hospitals"
import { getSelectedHospital, saveSelectedHospital } from "../utils/selectedHospital"
import { getSelectedRole, saveSelectedRole } from "../utils/selectedRole"
import { dashboardPath, roleOption } from "../utils/presentationFlow"

function HospitalSelection() {
    const navigate = useNavigate()
    const [searchParams] = useSearchParams()
    const role = (searchParams.get("role") || getSelectedRole() || "").toUpperCase()
    const selectedRole = roleOption(role)
    const requestedHospitalId = searchParams.get("hospitalId") || getSelectedHospital()?.id || ""
    const [hospitals, setHospitals] = useState([])
    const [hospitalId, setHospitalId] = useState(requestedHospitalId)
    const [status, setStatus] = useState("loading")

    useEffect(() => {
        let active = true
        fetchHospitals()
            .then((records) => {
                if (!active) return
                setHospitals(records)
                setStatus("ready")
                if (requestedHospitalId && records.some((hospital) => String(hospital.id) === String(requestedHospitalId))) {
                    setHospitalId(String(requestedHospitalId))
                } else if (requestedHospitalId) {
                    setHospitalId("")
                }
            })
            .catch(() => active && setStatus("error"))
        return () => { active = false }
    }, [requestedHospitalId])

    if (!selectedRole) return <Navigate to="/" replace />

    function continueToDashboard(event) {
        event.preventDefault()
        const hospital = hospitals.find((item) => String(item.id) === String(hospitalId))
        const selected = saveSelectedHospital(hospital)
        if (!selected) return
        saveSelectedRole(role)
        navigate(dashboardPath(role, selected.id))
    }

    return (
        <div className="login-page">
            <BackButton className="back-button-login" />
            <div className="login-box">
                <div className="login-heading">
                    <div className="login-mark">+</div>
                    <span>CAMILLUS</span>
                </div>
                <h1>Select a hospital</h1>
                <p>Continue as <strong>{selectedRole.label}</strong>.</p>
                <form onSubmit={continueToDashboard}>
                    <label htmlFor="entry-hospital">Hospital</label>
                    <select
                        id="entry-hospital"
                        value={hospitalId}
                        onChange={(event) => setHospitalId(event.target.value)}
                        required
                        disabled={status !== "ready" || hospitals.length === 0}
                    >
                        <option value="" disabled>
                            {status === "loading" ? "Loading hospitals..." : status === "error" ? "Hospitals unavailable" : "Select a hospital"}
                        </option>
                        {hospitals.map((hospital) => (
                            <option key={hospital.id} value={String(hospital.id)}>{hospital.name}</option>
                        ))}
                    </select>
                    {status === "error" && <p className="login-error" role="alert">Could not load hospitals. Please try again.</p>}
                    {status === "ready" && hospitals.length === 0 && <p className="login-error" role="alert">No hospitals are available from the hospital service.</p>}
                    <button type="submit" className="login-submit" disabled={status !== "ready" || hospitals.length === 0 || !hospitalId}>
                        Continue to Dashboard
                    </button>
                </form>
            </div>
        </div>
    )
}

export default HospitalSelection
