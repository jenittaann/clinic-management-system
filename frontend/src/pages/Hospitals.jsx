import { useMemo, useState } from "react"
import { Link } from "react-router-dom"
import BackButton from "../components/BackButton"
import { departments, hospitals } from "../data/hospitals"
import "./Hospitals.css"

function Hospitals() {
    const [search, setSearch] = useState("")
    const [showNearMeMessage, setShowNearMeMessage] = useState(false)

    const filteredHospitals = useMemo(() => {
        const query = search.trim().toLowerCase()
        return hospitals.filter((hospital) =>
            `${hospital.name} ${hospital.type} ${hospital.area}`.toLowerCase().includes(query),
        )
    }, [search])

    return (
        <main className="hospitals-page">
            <BackButton />

            <header className="hospitals-header">
                <p className="hospitals-eyebrow">CAMILLUS CARE NETWORK</p>
                <h1>Find Hospitals</h1>
                <p>Discover hospitals near you and explore their departments, doctors and availability.</p>
            </header>

            <div className="hospitals-toolbar">
                <label className="hospital-search">
                    <span aria-hidden="true">⌕</span>
                    <input
                        type="search"
                        aria-label="Search hospitals by name"
                        placeholder="Search hospitals by name or area"
                        value={search}
                        onChange={(event) => setSearch(event.target.value)}
                    />
                </label>
                <button
                    type="button"
                    className="hospital-location-button"
                    onClick={() => setShowNearMeMessage(true)}
                >
                    <span aria-hidden="true">⌖</span>
                    Use My Location
                </button>
            </div>

            {showNearMeMessage && (
                <p className="hospital-location-notice" role="status">
                    Showing hospitals in Chennai. Device location is not accessed.
                </p>
            )}
            <p className="hospital-results-count">{filteredHospitals.length} hospitals in Chennai</p>

            {filteredHospitals.length > 0 ? (
                <section className="hospital-grid" aria-label="Hospitals">
                    {filteredHospitals.map((hospital) => (
                        <article className="hospital-card" key={hospital.id}>
                            <div className="hospital-card-icon" aria-hidden="true">✚</div>
                            <div className="hospital-card-heading">
                                <span className="hospital-type">{hospital.type}</span>
                                <h2>{hospital.name}</h2>
                                <p className="hospital-area"><span aria-hidden="true">⌖</span> {hospital.area}</p>
                            </div>
                            <div className="hospital-card-stats">
                                <div><strong>{hospital.beds.toLocaleString("en-IN")}</strong><span>Beds</span></div>
                                <div><strong>{hospital.departmentIds.length}</strong><span>Departments</span></div>
                            </div>
                            <p className="hospital-card-department-preview">
                                {hospital.departmentIds
                                    .slice(0, 3)
                                    .map((id) => departments.find((department) => department.id === id)?.name)
                                    .join(" · ")}
                            </p>
                            <Link className="hospital-view-button" to={`/hospitals/${hospital.id}`}>
                                View Hospital <span aria-hidden="true">→</span>
                            </Link>
                        </article>
                    ))}
                </section>
            ) : (
                <p className="hospital-empty-state">No hospitals match “{search}”. Try another name or area.</p>
            )}
        </main>
    )
}

export default Hospitals
