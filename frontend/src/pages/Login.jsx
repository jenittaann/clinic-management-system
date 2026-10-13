import { useEffect, useState } from "react"
import { Link, useLocation, useNavigate, useSearchParams } from "react-router-dom"
import BackButton from "../components/BackButton"
import { fetchHospitals } from "../api/hospitals"
import { login } from "../api/auth"
import { clearSelectedHospital, getSelectedHospital, saveSelectedHospital } from "../utils/selectedHospital"

function Login() {
    const navigate = useNavigate()
    const location = useLocation()
    const [searchParams] = useSearchParams()
    const requestedHospitalId = searchParams.get("hospitalId")
    const initialHospitalId = requestedHospitalId || getSelectedHospital()?.id

    const [role, setRole] = useState("Patient")
    const [email, setEmail] = useState("")
    const [password, setPassword] = useState("")
    const [error, setError] = useState(location.state?.authError || "")
    const [isSubmitting, setIsSubmitting] = useState(false)
    const [hospitalState, setHospitalState] = useState(() => initialHospitalId
        ? { status: "loading", hospital: null, error: "" }
        : { status: "missing", hospital: null, error: "Select a hospital before choosing a login role." })
    const [reloadKey, setReloadKey] = useState(0)

    useEffect(() => {
        const storedSelection = getSelectedHospital()
        const hospitalId = requestedHospitalId || storedSelection?.id

        if (!hospitalId) {
            return undefined
        }

        let isCurrent = true
        fetchHospitals()
            .then((hospitals) => {
                if (!isCurrent) return
                const hospital = hospitals.find((item) => String(item.id) === String(hospitalId))
                if (!hospital) {
                    clearSelectedHospital()
                    setHospitalState({ status: "invalid", hospital: null, error: "That hospital is no longer available. Select a hospital again." })
                    return
                }
                const selected = saveSelectedHospital(hospital)
                setHospitalState({ status: "success", hospital: selected, error: "" })
            })
            .catch((requestError) => {
                if (isCurrent) {
                    setHospitalState({
                        status: "error",
                        hospital: null,
                        error: requestError.message || "Hospital information is temporarily unavailable.",
                    })
                }
            })

        return () => {
            isCurrent = false
        }
    }, [requestedHospitalId, reloadKey])

    async function handleLogin(event) {
        event.preventDefault()

        if (hospitalState.status !== "success" || !hospitalState.hospital?.id) {
            setError("Select a valid hospital before continuing.")
            return
        }

        const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
        if (!email.trim() || !password.trim()) {
            setError("Please enter your email and password.")
            return
        }

        if (!emailPattern.test(email.trim())) {
            setError("Please enter a valid email address.")
            return
        }

        setError("")
        setIsSubmitting(true)
        const hospitalQuery = `?hospitalId=${encodeURIComponent(hospitalState.hospital.id)}`
        const dashboardByRole = {
            PATIENT: "/patient-dashboard",
            DOCTOR: "/doctor-dashboard",
            RECEPTIONIST: "/receptionist-dashboard",
            ADMIN: "/admin-dashboard",
        }
        try {
            const identity = await login(email.trim(), password)
            const dashboard = dashboardByRole[identity?.role]
            if (!dashboard) throw new Error("Your account has an unsupported role. Contact your clinic administrator.")
            navigate(`${dashboard}${hospitalQuery}`, { replace: true })
        } catch (requestError) {
            setError(requestError.message || "Unable to sign in. Please try again.")
        } finally {
            setIsSubmitting(false)
        }
    }

    if (hospitalState.status !== "success") {
        return (
            <div className="login-page">
                <BackButton className="back-button-login" />
                <section className="login-box" aria-live="polite">
                    <div className="login-heading">
                        <div className="login-mark">+</div>
                        <span>CAMILLUS</span>
                    </div>
                    <h1>Choose a hospital first</h1>
                    {hospitalState.status === "loading" ? (
                        <p role="status">Checking your hospital selection…</p>
                    ) : (
                        <>
                            <p role={hospitalState.status === "error" || hospitalState.status === "invalid" ? "alert" : undefined}>
                                {hospitalState.error}
                            </p>
                            {hospitalState.status === "error" && (
                                <button type="button" className="login-submit" onClick={() => setReloadKey((key) => key + 1)}>
                                    Try again
                                </button>
                            )}
                            <Link className="hospital-view-button" to="/hospitals">Go to hospital selection</Link>
                        </>
                    )}
                </section>
            </div>
        )
    }

    return (
        <div className="login-page">
            <BackButton className="back-button-login" />

            <div className="login-box">
                <div className="login-heading">
                    <div className="login-mark">+</div>
                    <span>CAMILLUS</span>
                </div>

                <h1>Welcome back</h1>
                <p>Sign in to manage your healthcare journey.</p>

                <div className="login-hospital-context">
                    <span>Selected hospital</span>
                    <strong>{hospitalState.hospital.name}</strong>
                    <span>{hospitalState.hospital.location}</span>
                    <Link to="/hospitals">Change Hospital</Link>
                </div>

                <form onSubmit={handleLogin}>
                    <label htmlFor="login-role">Sign in as</label>
                    <select id="login-role" value={role} onChange={(event) => setRole(event.target.value)}>
                        <option>Patient</option>
                        <option>Doctor</option>
                        <option>Receptionist</option>
                        <option>Admin</option>
                    </select>

                    <label htmlFor="login-email">Email</label>
                    <input
                        id="login-email"
                        type="email"
                        placeholder="Enter your email"
                        value={email}
                        onChange={(event) => {
                            setEmail(event.target.value)
                            setError("")
                        }}
                    />

                    <label htmlFor="login-password">Password</label>
                    <input
                        id="login-password"
                        type="password"
                        placeholder="Enter your password"
                        value={password}
                        onChange={(event) => {
                            setPassword(event.target.value)
                            setError("")
                        }}
                    />

                    <div className="login-options">
                        <label className="remember-option">
                            <input type="checkbox" />
                            <span>Remember me</span>
                        </label>
                        <button type="button" className="forgot-link">Forgot password?</button>
                    </div>

                    {error && <p className="login-error" role="alert">{error}</p>}

                    <button type="submit" className="login-submit" disabled={isSubmitting}>
                        {isSubmitting ? "Signing in…" : "Login to Camillus"}
                    </button>
                </form>
                {isSubmitting && <p role="status">Verifying your account…</p>}
            </div>
        </div>
    )
}

export default Login
