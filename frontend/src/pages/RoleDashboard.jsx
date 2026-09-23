import { Link } from "react-router-dom"

function RoleDashboard({ role }) {
    return (
        <main className="role-dashboard">
            <div className="dashboard-shell">
                <div className="dashboard-kicker">CAMILLUS CLINIC MANAGEMENT</div>
                <h1>{role} Dashboard</h1>
                <p className="dashboard-intro">
                    Your mock workspace is ready. Connect the Spring Boot services here when the backend is available.
                </p>

                <div className="dashboard-summary">
                    <div className="summary-card">
                        <span className="summary-label">Access level</span>
                        <strong>{role}</strong>
                    </div>
                    <div className="summary-card">
                        <span className="summary-label">System status</span>
                        <strong className="status-ready">Frontend ready</strong>
                    </div>
                </div>

                <Link to="/" className="dashboard-home-link">Return to Camillus home</Link>
            </div>
        </main>
    )
}

export default RoleDashboard
