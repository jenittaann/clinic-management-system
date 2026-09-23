import { useState } from "react"
import { useNavigate } from "react-router-dom"

function Login() {
    const navigate = useNavigate()

    const [role, setRole] = useState("Patient")
    const [email, setEmail] = useState("")
    const [password, setPassword] = useState("")
    const [error, setError] = useState("")

    function handleLogin(event) {
        event.preventDefault()

        const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
        const passwordPattern = /^(?=.*[A-Za-z])(?=.*\d).{8,}$/

        if (!email.trim() || !password.trim()) {
            setError("Please enter your email and password.")
            return
        }

        if (!emailPattern.test(email.trim())) {
            setError("Please enter a valid email address.")
            return
        }

        if (!passwordPattern.test(password)) {
            setError("Password must be at least 8 characters and include a letter and a number.")
            return
        }

        setError("")

        if (role === "Patient") {
            navigate("/patient-dashboard")
        } else if (role === "Doctor") {
            navigate("/doctor-dashboard")
        } else if (role === "Receptionist/Hospital Manager") {
            navigate("/receptionist-dashboard")
        } else if (role === "Super Admin") {
            navigate("/admin-dashboard")
        }
    }

    return (
        <div className="login-page">

            <div className="login-box">

                <div className="login-heading">
                    <div className="login-mark">+</div>
                    <span>CAMILLUS</span>
                </div>

                <h1>Welcome back</h1>

                <p>Sign in to manage your healthcare journey.</p>

                <form onSubmit={handleLogin}>

                    <label htmlFor="login-role">Sign in as</label>

                    <select
                        id="login-role"
                        value={role}
                        onChange={(e) => setRole(e.target.value)}
                    >
                        <option>Patient</option>
                        <option>Doctor</option>
                        <option>Receptionist/Hospital Manager</option>
                        <option>Super Admin</option>
                    </select>

                    <label htmlFor="login-email">Email</label>

                    <input
                        id="login-email"
                        type="email"
                        placeholder="Enter your email"
                        value={email}
                        onChange={(e) => {
                            setEmail(e.target.value)
                            setError("")
                        }}
                    />

                    <label htmlFor="login-password">Password</label>

                    <input
                        id="login-password"
                        type="password"
                        placeholder="Enter your password"
                        value={password}
                        onChange={(e) => {
                            setPassword(e.target.value)
                            setError("")
                        }}
                    />

                    <div className="login-options">
                        <label className="remember-option">
                            <input type="checkbox" />
                            <span>Remember me</span>
                        </label>
                        <button type="button" className="forgot-link">
                            Forgot password?
                        </button>
                    </div>

                    {error && <p className="login-error" role="alert">{error}</p>}

                    <button type="submit" className="login-submit">
                        Login to Camillus
                    </button>

                </form>

                <p className="login-note">Secure access for patients and clinic teams.</p>

            </div>

        </div>
    )
}

export default Login