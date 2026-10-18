import { useCallback, useEffect, useState } from "react"
import { Link } from "react-router-dom"
import BackButton from "../components/BackButton"
import { cancelAppointment, createAndLinkDoctorAccount, createDepartment, createDoctor, getDoctorAppointments, getDoctors, getPatients, getUnlinkedDoctorAccounts, linkDoctorAccount } from "../api/clinic"
import { explainIdentityError } from "../utils/authBoundary"
import { fetchDepartments, fetchHospitals } from "../api/hospitals"
import { getSelectedHospital } from "../utils/selectedHospital"
import AccountAccess from "../components/AccountAccess"
import AIChatbox from "../components/AIChatbox"

const AVAILABLE_DEPARTMENTS = [
    "Cardiology",
    "Neurology",
    "Orthopedics",
    "Pediatrics",
    "Dermatology",
    "General Medicine",
    "Gynecology",
]

async function ensureHospitalDepartments(hospitalId) {
    let departments = await fetchDepartments(hospitalId)
    for (const name of AVAILABLE_DEPARTMENTS) {
        if (departments.some((department) => department.name.toLocaleLowerCase() === name.toLocaleLowerCase())) continue
        try {
            await createDepartment(hospitalId, { name, description: null })
        } catch (error) {
            // A concurrent administrator may have created it after our initial read.
            if (!error.message?.toLowerCase().includes("already exists")) throw error
        }
    }
    return fetchDepartments(hospitalId)
}

function AdminDashboard() {
    const users = []
    const [doctors, setDoctors] = useState([])
    const [clinics, setClinics] = useState([])
    const [departments, setDepartments] = useState([])
    const [hospitalOptions, setHospitalOptions] = useState([])
    const [doctorDepartments, setDoctorDepartments] = useState([])
    const [departmentsForHospitalId, setDepartmentsForHospitalId] = useState("")
    const [doctorLoading, setDoctorLoading] = useState(true)
    const [doctorError, setDoctorError] = useState("")
    const [doctorMessage, setDoctorMessage] = useState("")
    const [departmentName, setDepartmentName] = useState("")
    const [departmentError, setDepartmentError] = useState("")
    const [departmentMessage, setDepartmentMessage] = useState("")
    const [departmentSaving, setDepartmentSaving] = useState(false)
    const [doctorAccounts, setDoctorAccounts] = useState({})
    const [doctorAccountEmails, setDoctorAccountEmails] = useState({})
    const [unlinkedDoctorAccounts, setUnlinkedDoctorAccounts] = useState([])
    const [doctorForm, setDoctorForm] = useState({
        hospitalId: getSelectedHospital()?.id || "",
        departmentId: "",
        name: "",
        email: "",
        specialization: "",
        experienceYears: "",
        appointmentDurationMinutes: "30",
    })
    const [appointments, setAppointments] = useState([])
    const [patientCount, setPatientCount] = useState(0)
    const [recordsLoading, setRecordsLoading] = useState(true)
    const [recordsError, setRecordsError] = useState("")
    const [busy, setBusy] = useState("")
    const [search, setSearch] = useState("")
    const [selected, setSelected] = useState("")

    const filteredUsers = users.filter((user) => `${user.name} ${user.email} ${user.role}`.toLowerCase().includes(search.toLowerCase()))
    const refreshDoctors = useCallback(async () => {
        try {
            const [items, accounts] = await Promise.all([getDoctors(), getUnlinkedDoctorAccounts()])
            setDoctors(items)
            setUnlinkedDoctorAccounts(accounts)
            setDoctorError("")
        } catch (error) {
            setDoctorError(explainIdentityError(error, "administrator", "Unable to load doctors."))
        } finally {
            setDoctorLoading(false)
        }
    }, [])

    useEffect(() => {
        let active = true
        fetchHospitals()
            .then(async (items) => {
                if (!active) return
                setHospitalOptions(items)
                setClinics(items.map((hospital) => hospital.name))
                const departmentLists = await Promise.all(items.map((hospital) => fetchDepartments(hospital.id).catch(() => [])))
                if (active) setDepartments(departmentLists.flat())
                setDoctorForm((current) => ({
                    ...current,
                    hospitalId: current.hospitalId && items.some((hospital) => hospital.id === current.hospitalId)
                        ? current.hospitalId
                        : items[0]?.id || "",
                }))
            })
            .catch((error) => active && setDoctorError(error.message || "Unable to load hospitals."))
        return () => { active = false }
    }, [])

    useEffect(() => {
        if (!doctorForm.hospitalId) {
            return undefined
        }
        let active = true
        ensureHospitalDepartments(doctorForm.hospitalId)
            .then((items) => {
                if (!active) return
                setDoctorDepartments(items)
                setDepartmentsForHospitalId(doctorForm.hospitalId)
                setDoctorForm((current) => ({
                    ...current,
                    departmentId: items.some((department) => department.id === current.departmentId)
                        ? current.departmentId
                        : items[0]?.id || "",
                }))
            })
            .catch((error) => active && setDoctorError(error.message || "Unable to load hospital departments."))
        return () => { active = false }
    }, [doctorForm.hospitalId])

    useEffect(() => {
        let active = true
        Promise.all([getDoctors(), getUnlinkedDoctorAccounts()])
            .then(([items, accounts]) => {
                if (!active) return
                setDoctors(items)
                setUnlinkedDoctorAccounts(accounts)
            })
            .catch((error) => active && setDoctorError(explainIdentityError(error, "administrator", "Unable to load doctors.")))
            .finally(() => active && setDoctorLoading(false))
        return () => { active = false }
    }, [])

    async function addDoctor(event) {
        event.preventDefault()
        setDoctorError("")
        setDoctorMessage("")
        try {
            if (!doctorForm.hospitalId || !doctorForm.departmentId) {
                throw new Error("Select a hospital and department before adding a doctor.")
            }
            const validDepartments = await ensureHospitalDepartments(doctorForm.hospitalId)
            if (!validDepartments.some((department) => department.id === doctorForm.departmentId)) {
                throw new Error("The selected department is not available for this hospital. Choose it again and retry.")
            }
            await createDoctor({
                ...doctorForm,
                name: doctorForm.name.trim(),
                specialization: doctorForm.specialization.trim() || null,
                experienceYears: doctorForm.experienceYears === "" ? null : Number(doctorForm.experienceYears),
                appointmentDurationMinutes: Number(doctorForm.appointmentDurationMinutes),
            })
            setDoctorForm((current) => ({ ...current, name: "", email: "", specialization: "", experienceYears: "" }))
            await refreshDoctors()
            setDoctorMessage("Doctor profile saved.")
        } catch (error) {
            setDoctorError(explainIdentityError(error, "administrator", "Unable to save doctor profile."))
        }
    }

    async function addDepartment(event) {
        event.preventDefault()
        const hospitalId = doctorForm.hospitalId
        setDepartmentError("")
        setDepartmentMessage("")
        setDoctorError("")
        setDoctorMessage("")
        setDepartmentSaving(true)
        try {
            const created = await createDepartment(hospitalId, {
                name: departmentName.trim(),
                description: null,
            })
            // The POST response confirms the write. Refresh both views from the
            // server, but keep the created response as a fallback if the follow-up
            // read fails so the successful create is not reported as a failure.
            let refreshed = [created]
            try {
                refreshed = await fetchDepartments(hospitalId)
            } catch {
                refreshed = [created]
            }
            setDoctorDepartments(refreshed)
            setDepartmentsForHospitalId(hospitalId)
            const refreshedIds = new Set(refreshed.map((item) => item.id))
            setDepartments((current) => [
                ...current.filter((item) => !refreshedIds.has(item.id)),
                ...refreshed,
            ])
            setDoctorForm((current) => ({
                ...current,
                departmentId: current.hospitalId === hospitalId ? created.id : current.departmentId,
            }))
            setDepartmentName("")
            setDepartmentMessage(`${created.name} added. It is selected in the Add Doctor form.`)
        } catch (error) {
            setDepartmentError(explainIdentityError(error, "administrator", "Unable to add department."))
        } finally {
            setDepartmentSaving(false)
        }
    }

    async function linkAccount(doctorId) {
        const userId = doctorAccounts[doctorId]?.trim()
        if (!userId) {
            setDoctorError("Select an existing unlinked DOCTOR account.")
            return
        }
        const doctor = doctors.find((item) => item.id === doctorId)
        const account = unlinkedDoctorAccounts.find((item) => item.publicId === userId)
        if (!doctor || !account) {
            setDoctorError("The selected doctor profile or Doctor account is no longer available. Refresh and try again.")
            return
        }
        if (!window.confirm(`Confirm that ${account.email} is the correct account for ${doctor.name} at ${doctor.hospitalName} (${doctor.departmentName}) before linking.`)) {
            return
        }
        setDoctorError("")
        setDoctorMessage("")
        try {
            await linkDoctorAccount(doctorId, userId)
            setDoctorAccounts((current) => ({ ...current, [doctorId]: "" }))
            await refreshDoctors()
            setDoctorMessage("Existing doctor account linked.")
        } catch (error) {
            setDoctorError(explainIdentityError(error, "administrator", "Unable to link doctor account."))
        }
    }

    async function createAndLinkAccount(doctor) {
        const email = doctorAccountEmails[doctor.id]?.trim()
        if (!email) {
            setDoctorError("Enter the verified email address that belongs to this doctor.")
            return
        }
        if (!window.confirm(`Create a DOCTOR account for ${doctor.name} using ${email} and link it to this existing profile? Confirm that this email belongs to this doctor.`)) {
            return
        }
        setDoctorError("")
        setDoctorMessage("")
        try {
            await createAndLinkDoctorAccount(doctor.id, email)
            setDoctorAccountEmails((current) => ({ ...current, [doctor.id]: "" }))
            await refreshDoctors()
            setDoctorMessage(`Doctor account created and linked for ${doctor.name}. The doctor can sign in using the passwordless email link.`)
        } catch (error) {
            setDoctorError(explainIdentityError(error, "administrator", "Unable to create and link doctor account."))
        }
    }
    const refreshRecords = useCallback(async () => {
        try {
            const [patientPage, doctorList] = await Promise.all([getPatients(), getDoctors()])
            setRecordsError("")
            setPatientCount(patientPage.totalElements || 0)
            const perDoctor = await Promise.all(doctorList.map((doctor) => getDoctorAppointments(doctor.id, new Date().toLocaleDateString("en-CA", { timeZone: "Asia/Kolkata" }))))
            setAppointments(perDoctor.flat())
        } catch (error) { setRecordsError(explainIdentityError(error, "administrator", "Unable to load platform records.")) } finally { setRecordsLoading(false) }
    }, [])
    useEffect(() => {
        let active = true
        async function loadRecords() {
            try {
                const [patientPage, doctorList] = await Promise.all([getPatients(), getDoctors()])
                const perDoctor = await Promise.all(doctorList.map((doctor) => getDoctorAppointments(doctor.id, new Date().toLocaleDateString("en-CA", { timeZone: "Asia/Kolkata" }))))
                if (!active) return
                setPatientCount(patientPage.totalElements || 0)
                setAppointments(perDoctor.flat())
                setRecordsError("")
            } catch (error) {
                if (active) setRecordsError(explainIdentityError(error, "administrator", "Unable to load platform records."))
            } finally {
                if (active) setRecordsLoading(false)
            }
        }
        loadRecords()
        return () => { active = false }
    }, [])
    async function cancel(id) { setBusy(id); setRecordsError(""); try { await cancelAppointment(id); await refreshRecords() } catch (error) { setRecordsError(explainIdentityError(error, "administrator", "Unable to cancel appointment.")) } finally { setBusy("") } }

    return (
        <main className="role-dashboard">
            <div className="dashboard-shell dashboard-shell-wide">
        <AccountAccess role="ADMIN" />
                <div className="dashboard-kicker">CAMILLUS ADMINISTRATION</div>
                <div className="dashboard-title-row">
                    <div><h1>Super Admin Dashboard</h1><p className="dashboard-intro">Review the platform&apos;s people, places, and clinical structure.</p></div>
                    <div className="dashboard-header-links"><BackButton className="back-button-inline" /><Link to="/" className="dashboard-home-link">Home</Link><Link to="/settings" state={{ from: "/admin-dashboard" }} className="dashboard-home-link">Settings</Link></div>
                </div>

                <section className="dashboard-stats">
                    <div><strong>{clinics.length}</strong><span>Hospitals / clinics</span></div>
                    <div><strong>{doctors.length}</strong><span>Total doctors</span></div>
                    <div><strong>{recordsLoading ? "…" : patientCount}</strong><span>Total patients</span></div>
                    <div><strong>{recordsLoading ? "…" : appointments.length}</strong><span>Appointments today</span></div>
                </section>

                <section className="dashboard-panel">
                    <div className="panel-heading"><div><span className="panel-eyebrow">Quick actions</span><h2>System management</h2></div></div>
                    <div className="dashboard-grid">
                        <a className="dashboard-button" href="#users">Manage users</a>
                        <a className="dashboard-button" href="#doctors">Manage doctors</a>
                        <a className="dashboard-button" href="#hospitals">Manage hospitals</a>
                        <a className="dashboard-button" href="#appointments">View appointments</a>
                    </div>
                </section>

                <div className="dashboard-grid">
                    <section className="dashboard-panel dashboard-panel-wide" id="users">
                        <div className="panel-heading"><div><span className="panel-eyebrow">Accounts</span><h2>Doctor and user management</h2></div><span className="count-badge">{users.length}</span></div>
                        <input className="dashboard-search" aria-label="Search users" placeholder="Search users by name, email, or role..." value={search} onChange={(event) => setSearch(event.target.value)} />
                        <div className="dashboard-list">{filteredUsers.length === 0 ? <p className="empty-state">User accounts are not exposed by the current administration APIs.</p> : filteredUsers.map((user) => <div className="dashboard-list-item compact-list-item" key={user.email}>
                            <div><strong>{user.name}</strong><span>{user.email} · {user.role} · {user.active ? "Active" : "Disabled"}</span></div>
                            <div className="inline-actions"><button type="button" className="text-button" onClick={() => setSelected(selected === user.email ? "" : user.email)}>{selected === user.email ? "Close" : "View"}</button><button type="button" className={`text-button ${user.active ? "danger-text" : ""}`} disabled>{user.active ? "Disable" : "Enable"}</button></div>
                        </div>)}</div>
                        {selected && !selected.startsWith("doctor:") && !selected.startsWith("edit:") && <div className="detail-callout">Selected account: <strong>{users.find((user) => user.email === selected)?.name}</strong></div>}
                    </section>

                    <section className="dashboard-panel" id="doctors">
                        <div className="panel-heading"><div><span className="panel-eyebrow">Clinical team</span><h2>Doctor management</h2></div></div>
                        <form className="dashboard-form" onSubmit={addDoctor}>
                            <label>Hospital<select required disabled={departmentSaving} value={doctorForm.hospitalId} onChange={(event) => setDoctorForm((current) => ({ ...current, hospitalId: event.target.value, departmentId: "" }))}>
                                <option value="">Select a hospital</option>{hospitalOptions.map((hospital) => <option key={hospital.id} value={hospital.id}>{hospital.name}</option>)}
                            </select></label>
                            <label>Department<select required value={doctorForm.departmentId} onChange={(event) => setDoctorForm((current) => ({ ...current, departmentId: event.target.value }))} disabled={!doctorForm.hospitalId || departmentsForHospitalId !== doctorForm.hospitalId || !doctorDepartments.length}>
                                <option value="">Select a department</option>{doctorDepartments.map((department) => <option key={department.id} value={department.id}>{department.name}</option>)}
                            </select></label>
                            <label>Doctor name<input required maxLength={200} value={doctorForm.name} onChange={(event) => setDoctorForm((current) => ({ ...current, name: event.target.value }))} /></label>
                            <label>Doctor email<input type="email" required maxLength={254} autoComplete="email" value={doctorForm.email} onChange={(event) => setDoctorForm((current) => ({ ...current, email: event.target.value }))} /></label>
                            <label>Specialization<input maxLength={160} value={doctorForm.specialization} onChange={(event) => setDoctorForm((current) => ({ ...current, specialization: event.target.value }))} /></label>
                            <label>Experience (years)<input type="number" min="0" step="1" value={doctorForm.experienceYears} onChange={(event) => setDoctorForm((current) => ({ ...current, experienceYears: event.target.value }))} /></label>
                            <label>Appointment duration (minutes)<input type="number" min="1" step="1" required value={doctorForm.appointmentDurationMinutes} onChange={(event) => setDoctorForm((current) => ({ ...current, appointmentDurationMinutes: event.target.value }))} /></label>
                            <button type="submit" className="dashboard-button" disabled={!doctorForm.departmentId}>Add doctor</button>
                        </form>
                        {doctorError && <p className="login-error" role="alert">{doctorError}</p>}
                        {doctorMessage && <p role="status">{doctorMessage}</p>}
                        {doctorLoading ? <p role="status">Loading doctors…</p> : <div className="dashboard-list">{doctors.length === 0 ? <p className="empty-state">No doctors are listed yet.</p> : doctors.map((doctor) => <div className="dashboard-list-item compact-list-item" key={doctor.id}>
                            <div><strong>{doctor.name}</strong><span>{doctor.specialization || "General practice"} · {doctor.departmentName} · {doctor.hospitalName}</span></div>
                            <div className="inline-actions"><button type="button" className="text-button" onClick={() => setSelected(selected === `doctor:${doctor.id}` ? "" : `doctor:${doctor.id}`)}>{selected === `doctor:${doctor.id}` ? "Close" : "View"}</button></div>
                            {selected === `doctor:${doctor.id}` && <div className="detail-callout"><strong>{doctor.name}</strong><span>{doctor.experienceYears ?? "Experience not listed"} years · {doctor.appointmentDurationMinutes} minute appointments</span></div>}
                            <label>Link existing DOCTOR account<select value={doctorAccounts[doctor.id] || ""} onChange={(event) => setDoctorAccounts((current) => ({ ...current, [doctor.id]: event.target.value }))} disabled={!unlinkedDoctorAccounts.length}>
                                <option value="">Select a verified account</option>{unlinkedDoctorAccounts.map((account) => <option key={account.publicId} value={account.publicId}>{account.email}</option>)}
                            </select></label>
                            {!unlinkedDoctorAccounts.length && <p className="empty-state">No unlinked Doctor accounts are available.</p>}
                            <button type="button" className="text-button" disabled={!doctorAccounts[doctor.id]} onClick={() => linkAccount(doctor.id)}>Link account</button>
                            <label>Create a DOCTOR account for this existing profile<input type="email" maxLength={254} autoComplete="email" placeholder="Verified doctor email" value={doctorAccountEmails[doctor.id] || ""} onChange={(event) => setDoctorAccountEmails((current) => ({ ...current, [doctor.id]: event.target.value }))} /></label>
                            <button type="button" className="text-button" disabled={!doctorAccountEmails[doctor.id]?.trim()} onClick={() => createAndLinkAccount(doctor)}>Create and link account</button>
                        </div>)}</div>}
                    </section>

                    <section className="dashboard-panel" id="hospitals">
                        <div className="panel-heading"><div><span className="panel-eyebrow">Locations</span><h2>Hospital management</h2></div></div>
                        <div className="dashboard-list">{clinics.map((clinic) => <div className="dashboard-list-item compact-list-item" key={clinic}>
                            <strong>{clinic}</strong><button type="button" className="text-button" onClick={() => setSelected(selected === `clinic:${clinic}` ? "" : `clinic:${clinic}`)}>{selected === `clinic:${clinic}` ? "Selected" : "View details"}</button>
                        </div>)}{selected?.startsWith("clinic:") && <div className="detail-callout">Hospital profile: <strong>{selected.replace("clinic:", "")}</strong> · Active</div>}</div>
                    </section>

                    <section className="dashboard-panel" id="departments">
                        <div className="panel-heading"><div><span className="panel-eyebrow">Clinical structure</span><h2>Department management</h2></div></div>
                        <form className="dashboard-form" onSubmit={addDepartment}>
                            <label>Hospital<select required disabled={departmentSaving} value={doctorForm.hospitalId} onChange={(event) => setDoctorForm((current) => ({ ...current, hospitalId: event.target.value, departmentId: "" }))}>
                                <option value="">Select a hospital</option>{hospitalOptions.map((hospital) => <option key={hospital.id} value={hospital.id}>{hospital.name}</option>)}
                            </select></label>
                            <label>Department name<input required maxLength={160} value={departmentName} onChange={(event) => setDepartmentName(event.target.value)} /></label>
                            <button type="submit" className="dashboard-button" disabled={departmentSaving || !doctorForm.hospitalId}>{departmentSaving ? "Saving…" : "Add department"}</button>
                        </form>
                        {departmentError && <p className="login-error" role="alert">{departmentError}</p>}
                        {departmentMessage && <p role="status">{departmentMessage}</p>}
                        <div className="dashboard-list">{departments.map((department) => <div className="dashboard-list-item compact-list-item" key={department.id}><div><strong>{department.name}</strong></div></div>)}</div>
                    </section>

                    <section className="dashboard-panel dashboard-panel-wide" id="appointments">
                        <div className="panel-heading"><div><span className="panel-eyebrow">Platform activity</span><h2>Appointment overview</h2></div><span className="count-badge">{appointments.length} shown</span></div>
                        {recordsError && <p className="login-error" role="alert">{recordsError}</p>}
                        {recordsLoading ? <p role="status">Loading appointments…</p> : <div className="dashboard-list">{appointments.length ? appointments.map((appointment) => <div className="dashboard-list-item" key={appointment.appointmentId}>
                            <div><strong>{appointment.doctorName}</strong><span>{appointment.hospitalName} · {new Intl.DateTimeFormat("en-IN", { dateStyle: "medium", timeStyle: "short", timeZone: "Asia/Kolkata" }).format(new Date(appointment.startsAt))}</span></div>
                            <span className={`status-badge status-${appointment.status.toLowerCase()}`}>{appointment.status}</span>
                            {!['COMPLETED','CANCELLED','NO_SHOW'].includes(appointment.status) && <button type="button" className="text-button danger-text" disabled={busy === appointment.appointmentId} onClick={() => cancel(appointment.appointmentId)}>Cancel</button>}
                        </div>) : <p className="empty-state">No appointments scheduled today.</p>}</div>}
                    </section>

                    <section className="dashboard-panel">
                        <div className="panel-heading"><div><span className="panel-eyebrow">Audit trail</span><h2>Recent system activity</h2></div></div>
                        <div className="dashboard-list"><p className="empty-state">Audit events are not exposed by the current administration APIs.</p></div>
                    </section>
                </div>
            </div>
            <AIChatbox />
        </main>
    )
}

export default AdminDashboard
