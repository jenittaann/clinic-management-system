import { useEffect, useMemo, useState } from "react"
import { Link, useNavigate } from "react-router-dom"
import BackButton from "../components/BackButton"
import { fetchHospitals } from "../api/hospitals"
import { departments } from "../data/hospitals"
import { clearSelectedHospital, getSelectedHospital, saveSelectedHospital } from "../utils/selectedHospital"
import "./Hospitals.css"

function Hospitals() {
    const navigate = useNavigate()
    const [search, setSearch] = useState("")
    const [requestState, setRequestState] = useState({ status: "loading", hospitals: [], error: "" })
    const [reloadKey, setReloadKey] = useState(0)
    const [selectedHospital, setSelectedHospital] = useState(() => getSelectedHospital())

    function selectHospital(hospital) {
        const selected = saveSelectedHospital(hospital)
        if (!selected) return
        setSelectedHospital(selected)
        navigate(`/login?hospitalId=${encodeURIComponent(selected.id)}`)
    }

    useEffect(() => {
        let isCurrent = true

        fetchHospitals()
            .then((records) => {
                if (isCurrent) {
                    const storedSelection = getSelectedHospital()
                    const currentSelection = records.find((hospital) => String(hospital.id) === storedSelection?.id)
                    if (storedSelection && !currentSelection) {
                        clearSelectedHospital()
                        setSelectedHospital(null)
                    } else if (currentSelection) {
                        setSelectedHospital(saveSelectedHospital(currentSelection))
                    }
                    setRequestState({ status: "success", hospitals: records, error: "" })
                }
            })
            .catch((requestError) => {
                if (isCurrent) {
                    setRequestState({
                        status: "error",
                        hospitals: [],
                        error: requestError.message || "Unable to load hospitals.",
                    })
                }
            })

        return () => {
            isCurrent = false
        }
    }, [reloadKey])

    const filteredHospitals = useMemo(() => {
        const query = search.trim().toLowerCase()
        return requestState.hospitals.filter((hospital) =>
            `${hospital.name ?? ""} ${hospital.type ?? ""} ${hospital.area ?? ""}`
                .toLowerCase()
                .includes(query),
        )
    }, [search, requestState.hospitals])

    return (
        <main className="hospitals-page">
            <BackButton />

            <header className="hospitals-header">
                <p className="hospitals-eyebrow">CAMILLUS CARE NETWORK</p>
                <h1>Find Hospitals</h1>
                <p>Discover hospitals in Chennai and view available hospital information.</p>
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
            </div>

            {selectedHospital && (
                <p className="hospital-selected-notice" role="status">
                    Current selection: <strong>{selectedHospital.name}</strong> · {selectedHospital.location}
                </p>
            )}
            {requestState.status === "loading" ? (
                <p className="hospital-results-count" role="status">Loading hospitals…</p>
            ) : requestState.status === "error" ? (
                <div className="hospital-empty-state" role="alert">
                    <p>Hospitals could not be loaded: {requestState.error}</p>
                    <button type="button" className="hospital-view-button" onClick={() => {
                        setRequestState({ status: "loading", hospitals: [], error: "" })
                        setReloadKey((key) => key + 1)
                    }}>
                        Try again
                    </button>
                </div>
            ) : (
                <>
            <p className="hospital-results-count">{filteredHospitals.length} hospitals in Chennai</p>

            {filteredHospitals.length > 0 ? (
                <section className="hospital-grid" aria-label="Hospitals">
                    {filteredHospitals.map((hospital) => (
                        <article className="hospital-card" key={hospital.id}>
                            <div className="hospital-card-icon" aria-hidden="true">✚</div>
                            <div className="hospital-card-heading">
                                <span className="hospital-type">{hospital.type || "Type not listed"}</span>
                                <h2>{hospital.name || "Unnamed hospital"}</h2>
                                <p className="hospital-area"><span aria-hidden="true">⌖</span> {hospital.area || "Area not listed"}</p>
                            </div>
                            <div className="hospital-card-stats">
                                <div><strong>{Number.isInteger(hospital.beds) ? hospital.beds.toLocaleString("en-IN") : "Not listed"}</strong><span>Beds</span></div>
                                <div><strong>{Array.isArray(hospital.departmentIds) && hospital.departmentIds.length > 0 ? hospital.departmentIds.length : "Not listed"}</strong><span>Departments</span></div>
                            </div>
                            <p className="hospital-card-department-preview">
                                {(Array.isArray(hospital.departmentIds) ? hospital.departmentIds : [])
                                    .slice(0, 3)
                                    .map((id) => departments.find((department) => department.id === id)?.name)
                                    .filter(Boolean)
                                    .join(" · ")}
                            </p>
                            <div className="hospital-card-actions">
                            <Link className="hospital-view-button" to={`/hospitals/${encodeURIComponent(hospital.id)}`}>
                                View Hospital <span aria-hidden="true">→</span>
                            </Link>
                            <button
                                type="button"
                                className="hospital-select-button"
                                disabled={hospital.id === undefined || hospital.id === null || String(hospital.id).trim() === ""}
                                onClick={() => selectHospital(hospital)}
                            >
                                {String(selectedHospital?.id) === String(hospital.id) ? "Continue with this hospital" : "Select Hospital"}
                            </button>
                            </div>
                        </article>
                    ))}
                </section>
            ) : (
                <p className="hospital-empty-state">No hospitals match “{search}”. Try another name or area.</p>
            )}
                </>
            )}
        </main>
    )
}

export default Hospitals
