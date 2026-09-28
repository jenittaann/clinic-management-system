import { Link } from "react-router-dom"
import BackButton from "../components/BackButton"

function RoleDashboard({ role }) {
    return (
        <main className="role-dashboard">
            <div className="dashboard-shell">
                <BackButton />
                <div className="dashboard-kicker">CAMILLUS CLINIC MANAGEMENT</div>
                <h1>{role} Dashboard</h1>
                <p className="dashboard-intro">
                    Your clinic workspace is ready. Review your access level and current system status.
                </p>

                <div className="dashboard-summary">
                    <div className="summary-card">
                        <span className="summary-label">Access level</span>
                        <strong>{role}</strong>
                    </div>
                    <div className="summary-card">
                        <span className="summary-label">System status</span>
                        <strong className="status-ready">Ready</strong>
                    </div>
                </div>

                <Link to="/" className="dashboard-home-link">Return to Camillus home</Link>
            </div>
        </main>
    )
}

export default RoleDashboard
