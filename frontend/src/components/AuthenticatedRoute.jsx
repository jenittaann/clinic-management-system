import { useEffect, useState } from "react"
import { Navigate, useLocation } from "react-router-dom"
import { getCurrentSession } from "../api/auth"

function AuthenticatedRoute({ role, children }) {
    const location = useLocation()
    const [state, setState] = useState({ status: "loading", identity: null })

    useEffect(() => {
        let current = true
        getCurrentSession()
            .then((identity) => current && setState({ status: identity ? "authenticated" : "anonymous", identity }))
            .catch(() => current && setState({ status: "error", identity: null }))
        return () => { current = false }
    }, [])

    if (state.status === "loading") return <p role="status">Checking your sign-in…</p>
    if (state.status === "error") return <p role="alert">Unable to verify your sign-in. Please try again.</p>
    if (state.status === "anonymous") return <Navigate to={`/login${location.search}`} replace state={{ from: `${location.pathname}${location.search}` }} />
    if (role && state.identity.role !== role) return <Navigate to={`/login${location.search}`} replace state={{ authError: `This account has the ${state.identity.role} role and cannot open this dashboard.` }} />
    return children
}

export default AuthenticatedRoute
