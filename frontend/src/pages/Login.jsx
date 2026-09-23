import { useState } from "react"
import { useNavigate } from "react-router-dom"

function Login() {
    const navigate = useNavigate()

    const [role, setRole] = useState("Patient")
    const [email, setEmail] = useState("")
    const [password, setPassword] = useState("")

    function handleLogin(event) {
        event.preventDefault()

        if (role === "Patient") {
            navigate("/")
        } else if (role === "Doctor") {
            navigate("/doctor-dashboard")
        } else if (role === "Receptionist") {
            navigate("/reception-dashboard")
        } else if (role === "Super Admin") {
            navigate("/admin-dashboard")
        }
    }

    return (
        <div className="login-page">

            <div className="login-box">

                <h1>Welcome to Camillus</h1>

                <p>Login to continue</p>

                <form onSubmit={handleLogin}>

                    <label>Select Role</label>

                    <select
                        value={role}
                        onChange={(e) => setRole(e.target.value)}
                    >
                        <option>Patient</option>
                        <option>Doctor</option>
                        <option>Receptionist</option>
                        <option>Super Admin</option>
                    </select>

                    <label>Email</label>

                    <input
                        type="email"
                        placeholder="Enter your email"
                        value={email}
                        onChange={(e) => setEmail(e.target.value)}
                        required
                    />

                    <label>Password</label>

                    <input
                        type="password"
                        placeholder="Enter your password"
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                        required
                    />

                    <button type="submit">
                        Login
                    </button>

                </form>

            </div>

        </div>
    )
}

export default Login