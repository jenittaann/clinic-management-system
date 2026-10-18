import { Link } from "react-router-dom"
import "./Home.css"

const features = [
    {
        number: "01",
        title: "Easy Appointment Booking",
        description: "Find a suitable doctor and book from the available appointment times.",
        icon: "calendar",
    },
    {
        number: "02",
        title: "Doctor Schedule Management",
        description: "Set availability and keep schedules and appointment times in sync.",
        icon: "clock",
    },
    {
        number: "03",
        title: "Patient Care Management",
        description: "Keep patient and appointment information together for care teams.",
        icon: "person",
    },
    {
        number: "04",
        title: "Streamlined Clinic Operations",
        description: "Support the everyday work of patients, clinicians, and clinic staff.",
        icon: "clinic",
    },
]

function FeatureIcon({ type }) {
    const shared = {
        width: 26,
        height: 26,
        viewBox: "0 0 24 24",
        fill: "none",
        stroke: "currentColor",
        strokeWidth: 1.7,
        strokeLinecap: "round",
        strokeLinejoin: "round",
        "aria-hidden": true,
    }

    if (type === "calendar") return <svg {...shared}><rect x="3.5" y="5" width="17" height="15.5" rx="2.5" /><path d="M7.5 3.5v3M16.5 3.5v3M3.5 9.5h17M8 13h3M8 16.5h3M14 13h2" /></svg>
    if (type === "clock") return <svg {...shared}><circle cx="12" cy="12" r="8.5" /><path d="M12 7v5l3.5 2" /><path d="M4.5 4.5 3 6M19.5 4.5 21 6" /></svg>
    if (type === "person") return <svg {...shared}><circle cx="12" cy="8" r="3.25" /><path d="M5.5 20c.5-3.3 2.7-5 6.5-5s6 1.7 6.5 5" /><path d="M18 8.5h3M19.5 7v3" /></svg>
    return <svg {...shared}><path d="M3 20.5h18M5 20.5V8l7-4 7 4v12.5M9 20.5v-5h6v5M8.5 10h.01M15.5 10h.01" /><path d="M12 7v4M10 9h4" /></svg>
}

function Home() {
    return (
        <main className="camillus-home">
            <header className="home-header">
                <Link className="home-brand" to="/" aria-label="CAMILLUS home">
                    <span className="home-brand-mark" aria-hidden="true"><i /><i /></span>
                    <span>CAMILLUS</span>
                </Link>
                <Link to="/role-selection" className="home-header-link">Sign in <span aria-hidden="true">↗</span></Link>
            </header>

            <section className="home-hero">
                <div className="home-hero-copy">
                    <p className="home-eyebrow"><span /> CLINIC MANAGEMENT SYSTEM</p>
                    <h1>Smarter Clinic Management.<br /><span>Better Patient Care.</span></h1>
                    <p className="home-description">
                        A unified platform for managing appointments, doctor availability,
                        patient records, and clinic operations.
                    </p>
                    <Link to="/role-selection" className="home-cta">
                        Get Started <span aria-hidden="true">→</span>
                    </Link>
                    <p className="home-note"><span aria-hidden="true">✳</span> Thoughtfully connected care, from the first appointment onward.</p>
                </div>

                <div className="home-visual" aria-hidden="true">
                    <div className="visual-orbit visual-orbit-outer" />
                    <div className="visual-orbit visual-orbit-inner" />
                    <div className="visual-cross"><span /><span /></div>
                    <div className="visual-dot visual-dot-one" />
                    <div className="visual-dot visual-dot-two" />
                    <div className="visual-dot visual-dot-three" />
                    <div className="visual-caption">
                        <span className="visual-caption-icon">+</span>
                        <span><strong>Care, coordinated.</strong><small>One clinic platform</small></span>
                    </div>
                </div>
                <div className="home-hero-bottom"><span>CARE, CONNECTED</span><span>BUILT FOR EVERYDAY CLINIC WORK</span></div>
            </section>

            <section className="home-features" aria-labelledby="home-features-title">
                <div className="home-section-heading">
                    <div>
                        <p className="home-eyebrow">A CLEARER WAY TO CARE</p>
                        <h2 id="home-features-title">Everything your clinic needs,<br />working together.</h2>
                    </div>
                    <p>One considered workspace for the people and processes behind better clinic experiences.</p>
                </div>
                <div className="home-feature-grid">
                    {features.map((feature) => (
                        <article className="home-feature-card" key={feature.number}>
                            <div className="home-feature-top"><span className="home-feature-icon"><FeatureIcon type={feature.icon} /></span><span>{feature.number}</span></div>
                            <h3>{feature.title}</h3>
                            <p>{feature.description}</p>
                        </article>
                    ))}
                </div>
            </section>

            <footer className="home-footer"><Link className="home-brand" to="/"><span className="home-brand-mark" aria-hidden="true"><i /><i /></span><span>CAMILLUS</span></Link><span>Clinic Management System</span></footer>
        </main>
    )
}

export default Home