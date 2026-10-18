import { useEffect, useState } from "react"
import { useNavigate, useSearchParams } from "react-router-dom"
import BackButton from "../components/BackButton"
import { getSelectedRole, saveSelectedRole } from "../utils/selectedRole"
import { dashboardPath, hospitalSelectionPath, roleOption, roleOptions } from "../utils/presentationFlow"
import { verifyPasswordlessLink } from "../api/auth"
import { fetchHospitals } from "../api/hospitals"
import { saveSelectedHospital } from "../utils/selectedHospital"

function RoleSelection() {
    const navigate = useNavigate()
    const [searchParams] = useSearchParams()
    const [role, setRole] = useState(getSelectedRole() || "PATIENT")
    const [linkStatus, setLinkStatus] = useState("")

    useEffect(() => {
        const token = searchParams.get("token")
        if (!token) return
        let active = true
        setLinkStatus("Verifying your one-time sign-in link…")
        Promise.all([verifyPasswordlessLink(token), fetchHospitals()])
            .then(([result, hospitals]) => {
                if (!active) return
                const hospital = hospitals.find((item) => String(item.id) === String(result.hospitalId))
                if (!hospital) throw new Error("The hospital for this sign-in link is unavailable.")
                saveSelectedRole(result.identity.role)
                saveSelectedHospital(hospital)
                let destination = dashboardPath(result.identity.role, hospital.id)
                let destinationState
                try {
                    const pending = JSON.parse(window.localStorage.getItem("camillus-auth-return-to") || "null")
                    if (pending?.role === result.identity.role && typeof pending.pathname === "string"
                        && pending.pathname.startsWith("/") && !pending.pathname.startsWith("//")
                        && pending.pathname !== "/login" && pending.pathname !== "/") {
                        destination = { pathname: pending.pathname, search: pending.search || "" }
                        destinationState = pending.state
                    }
                    window.localStorage.removeItem("camillus-auth-return-to")
                } catch { /* Use the authenticated role's dashboard if local storage is unavailable. */ }
                navigate(destination, { replace: true, state: destinationState })
            })
            .catch((error) => active && setLinkStatus(error.message || "This sign-in link is invalid or expired."))
        return () => { active = false }
    }, [searchParams, navigate])

    function continueToHospital(event) {
        event.preventDefault()
        const selectedRole = roleOption(role)?.value
        if (!selectedRole) return
        saveSelectedRole(selectedRole)
        navigate(hospitalSelectionPath(selectedRole, searchParams.get("hospitalId")))
    }

    return (
        <div className="login-page">
            <BackButton className="back-button-login" />
            <div className="login-box">
                <div className="login-heading">
                    <div className="login-mark">+</div>
                    <span>CAMILLUS</span>
                </div>
                <h1>Select a role</h1>
                {linkStatus && <p role="status" className={linkStatus.startsWith("Verifying") ? "" : "login-error"}>{linkStatus}</p>}
                <p>Choose how you want to explore CAMILLUS.</p>
                <form onSubmit={continueToHospital}>
                    <label htmlFor="entry-role">User / Role</label>
                    <select id="entry-role" value={role} onChange={(event) => setRole(event.target.value)}>
                        {roleOptions.map(({ value, label }) => <option key={value} value={value}>{label}</option>)}
                    </select>
                    <button type="submit" className="login-submit">Continue</button>
                </form>
            </div>
        </div>
    )
}

export default RoleSelection
