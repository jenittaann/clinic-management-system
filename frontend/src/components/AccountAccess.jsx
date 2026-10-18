import { useEffect, useState } from "react"
import { useLocation } from "react-router-dom"
import { dashboardPath } from "../utils/presentationFlow"
import { emailSignIn, getCurrentSession, registerPatientAndSignIn, registerReceptionistAndSignIn, requestPasswordlessLink } from "../api/auth"
import { getSelectedHospital } from "../utils/selectedHospital"

export default function AccountAccess({ role, onAuthenticated }) {
    const location = useLocation()
    const [session, setSession] = useState(undefined)
    const [email, setEmail] = useState("")
    const [registerPatient, setRegisterPatient] = useState(false)
    const [busy, setBusy] = useState(false)
    const [message, setMessage] = useState("")
    const [error, setError] = useState("")
    const hospital = getSelectedHospital()

    useEffect(() => {
        let active = true
        getCurrentSession().then((current) => {
            if (active) {
                setSession(current)
                if (current?.role === role) onAuthenticated?.()
            }
        }).catch(() => active && setSession(null))
        return () => { active = false }
    }, [role, onAuthenticated])

    async function submit(event) {
        event.preventDefault()
        setBusy(true)
        setError("")
        setMessage("")
        try {
            try {
                window.localStorage.setItem("camillus-auth-return-to", JSON.stringify({
                    role,
                    pathname: location.pathname,
                    search: location.search,
                    state: location.state,
                }))
            } catch { /* The login link can still be used; it will return to the role dashboard. */ }
            if (role === "ADMIN") {
                await requestPasswordlessLink({ email: email.trim(), role, hospitalId: hospital?.id })
                setMessage("If the account is eligible, a one-time sign-in link has been sent to that email address.")
            } else if (role === "PATIENT" && registerPatient) {
                await registerPatientAndSignIn({ email: email.trim(), hospitalId: hospital?.id })
                window.location.assign(dashboardPath("PATIENT", hospital.id))
            } else if (role === "RECEPTIONIST") {
                await registerReceptionistAndSignIn({ email: email.trim(), hospitalId: hospital?.id })
                window.location.assign(dashboardPath("RECEPTIONIST", hospital.id))
            } else {
                await emailSignIn({ email: email.trim(), role, hospitalId: hospital?.id })
                window.location.reload()
            }
        } catch (requestError) {
            setError(requestError.message || "Unable to request a sign-in link.")
        } finally {
            setBusy(false)
        }
    }

    if (session === undefined) return <section className="dashboard-panel" aria-live="polite"><p role="status">Checking account session…</p></section>
    if (session?.role === role) return null

    return <section className="dashboard-panel account-access-panel">
        <span className="panel-eyebrow">Secure account access</span>
        <h2>{session ? "Use the matching account" : "Sign in to load protected records"}</h2>
        <p>{session ? `This dashboard requires a ${role.toLowerCase()} account. The current session is ${session.role.toLowerCase()}.` : "Your role and hospital selection opens this dashboard. An authenticated account is still required for private records and staff actions."}</p>
        <form className="dashboard-form" onSubmit={submit}>
            <label>Email<input type="email" autoComplete="email" required maxLength={254} value={email} onChange={(event) => setEmail(event.target.value)} /></label>
            {role === "PATIENT" && <label className="account-register-option"><input type="checkbox" checked={registerPatient} onChange={(event) => setRegisterPatient(event.target.checked)} /> I am a new patient and want to create my patient account</label>}
            {error && <p className="login-error" role="alert">{error}</p>}
            {message && <p role="status">{message}</p>}
            <button type="submit" className="dashboard-button" disabled={busy || !hospital?.id}>{busy ? (role === "ADMIN" ? "Sending…" : registerPatient || role === "RECEPTIONIST" ? "Creating account…" : "Signing in…") : role === "ADMIN" ? "Email me a sign-in link" : registerPatient ? "Create patient account" : role === "RECEPTIONIST" ? "Continue / Create account" : "Continue / Sign in"}</button>
        </form>
    </section>
}
