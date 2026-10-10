import { Link } from "react-router-dom"

function Home() {
    return (
        <div className="app">
            <nav className="navbar">
                <div className="logo">CAMILLUS</div>

                <div className="nav-links">
                    <a href="#home">Home</a>
                    <a href="#services">Services</a>
                    <a href="#doctors">Doctors</a>
                    <a href="#about">About</a>
                </div>

                <Link to="/login" className="login-btn">Login</Link>
            </nav>

            <main id="home" className="hero-section">
                <div className="hero-content">
                    <p className="tagline">SMARTER HEALTHCARE • SIMPLER MANAGEMENT</p>

                    <h1>
                        Your Healthcare,
                        <br />
                        <span>All in One Place.</span>
                    </h1>

                    <p className="hero-text">
                        Discover doctors, explore departments, check availability,
                        and manage your appointments with ease.
                    </p>

                    <div className="hero-buttons">
                        <Link to="/doctors" className="primary-btn">Find a Doctor</Link>
                        <Link to="/book-appointment" className="secondary-btn">Book Appointment</Link>
                        <Link to="/hospitals" className="secondary-btn">Find a Hospital</Link>
                    </div>
                </div>

                <div className="hero-card">
                    <div className="card-icon">✚</div>
                    <h3>Clinic Management System</h3>
                    <p>
                        Connecting patients, doctors and clinic staff through
                        one intelligent platform.
                    </p>
                </div>
            </main>

            <section id="services" className="services-section">
                <h2>Healthcare Made Simpler</h2>

                <div className="service-container">
                    <div className="service-card">
                        <h3>Find Doctors</h3>
                        <p>Explore doctors, departments and their availability.</p>
                    </div>

                    <div className="service-card">
                        <h3>Manage Appointments</h3>
                        <p>Book, cancel or reschedule appointments easily.</p>
                    </div>

                    <div className="service-card">
                        <h3>AI Assistance</h3>
                        <p>Get quick answers to clinic and appointment-related queries.</p>
                    </div>
                </div>
            </section>
        </div>
    )
}

export default Home