import { useState } from "react"
import { Link, useLocation, useNavigate } from "react-router-dom"
import BackButton from "../components/BackButton"

const initialNotifications = {
    appointments: true,
    reminders: true,
    general: false,
}

const initialProfile = {
    name: "Alex Morgan",
    email: "alex.morgan@example.com",
    phone: "+1 555 010 2840",
    role: "Clinic team member",
}

function readStoredValue(key, fallback) {
    try {
        const value = window.localStorage.getItem(key)
        return value ? { ...fallback, ...JSON.parse(value) } : fallback
    } catch {
        return fallback
    }
}

function Settings({ theme, onThemeChange }) {
    const navigate = useNavigate()
    const location = useLocation()
    const dashboardPath = location.state?.from || "/"
    const [notifications, setNotifications] = useState(() => readStoredValue("camillus-notifications", initialNotifications))
    const [profile, setProfile] = useState(() => readStoredValue("camillus-profile", initialProfile))
    const [draftProfile, setDraftProfile] = useState(profile)
    const [editingProfile, setEditingProfile] = useState(false)
    const [language, setLanguage] = useState(() => window.localStorage.getItem("camillus-language") || "English")
    const [dateFormat, setDateFormat] = useState(() => window.localStorage.getItem("camillus-date-format") || "DD MMM YYYY")
    const [timeFormat, setTimeFormat] = useState(() => window.localStorage.getItem("camillus-time-format") || "12-hour")
    const [preferences, setPreferences] = useState(() => readStoredValue("camillus-preferences", { weeklySummary: true, compactLists: false }))
    const [privacy, setPrivacy] = useState(() => readStoredValue("camillus-privacy", { profileVisibility: true, shareUsageData: false }))
    const [passwordForm, setPasswordForm] = useState({ current: "", next: "", confirm: "" })
    const [passwordMessage, setPasswordMessage] = useState("")
    const [profileMessage, setProfileMessage] = useState("")

    function toggleStoredSetting(group, key, value, storageKey, setGroup) {
        const updated = { ...group, [key]: value }
        setGroup(updated)
        window.localStorage.setItem(storageKey, JSON.stringify(updated))
    }

    function saveProfile(event) {
        event.preventDefault()
        const updated = { ...draftProfile }
        setProfile(updated)
        window.localStorage.setItem("camillus-profile", JSON.stringify(updated))
        setEditingProfile(false)
        setProfileMessage("Profile updated on this device.")
    }

    function changePassword(event) {
        event.preventDefault()
        if (!passwordForm.current) {
            setPasswordMessage("Enter your current password to continue.")
            return
        }
        if (!/^(?=.*[A-Za-z])(?=.*\d).{8,}$/.test(passwordForm.next)) {
            setPasswordMessage("Use at least 8 characters with at least one letter and one number.")
            return
        }
        if (passwordForm.next !== passwordForm.confirm) {
            setPasswordMessage("The new password and confirmation do not match.")
            return
        }
        setPasswordMessage("Password updated successfully.")
        setPasswordForm({ current: "", next: "", confirm: "" })
    }

    function logout() {
        navigate("/login")
    }

    return (
        <main className="role-dashboard settings-page">
            <div className="dashboard-shell dashboard-shell-wide">
                <div className="dashboard-kicker">CAMILLUS PREFERENCES</div>
                <div className="dashboard-title-row">
                    <div><h1>Settings</h1><p className="dashboard-intro">Manage your account, workspace preferences, and privacy.</p></div>
                    <div className="dashboard-header-links"><BackButton className="back-button-inline" /><Link to={dashboardPath} className="dashboard-home-link">{dashboardPath === "/" ? "Home" : "Dashboard"}</Link></div>
                </div>

                <nav className="settings-tabs" aria-label="Settings sections">
                    <a href="#appearance">Appearance</a><a href="#notifications">Notifications</a><a href="#account">Account</a><a href="#general">General</a><a href="#security">Security</a><a href="#support">Help &amp; Support</a><a href="#privacy">Privacy</a>
                </nav>

                <div className="settings-layout">
                    <div className="settings-main-column">
                        <section className="dashboard-panel settings-section" id="appearance">
                            <div className="panel-heading"><div><span className="panel-eyebrow">Display</span><h2>Appearance</h2></div></div>
                            <p className="panel-note">Choose how Camillus looks across this device.</p>
                            <div className="theme-choice-row" role="group" aria-label="Appearance theme">
                                <button type="button" className={`theme-choice ${theme === "light" ? "theme-choice-selected" : ""}`} aria-pressed={theme === "light"} onClick={() => onThemeChange("light")}><span className="theme-preview theme-preview-light" aria-hidden="true" />Light mode</button>
                                <button type="button" className={`theme-choice ${theme === "dark" ? "theme-choice-selected" : ""}`} aria-pressed={theme === "dark"} onClick={() => onThemeChange("dark")}><span className="theme-preview theme-preview-dark" aria-hidden="true" />Dark mode</button>
                            </div>
                        </section>

                        <section className="dashboard-panel settings-section" id="notifications">
                            <div className="panel-heading"><div><span className="panel-eyebrow">Updates</span><h2>Notifications</h2></div></div>
                            <div className="settings-option-list">
                                <SettingToggle label="Appointment notifications" description="Updates when an appointment is booked or changed." checked={notifications.appointments} onChange={(checked) => toggleStoredSetting(notifications, "appointments", checked, "camillus-notifications", setNotifications)} />
                                <SettingToggle label="Reminder notifications" description="Upcoming visit and follow-up reminders." checked={notifications.reminders} onChange={(checked) => toggleStoredSetting(notifications, "reminders", checked, "camillus-notifications", setNotifications)} />
                                <SettingToggle label="General notifications" description="Clinic announcements and account updates." checked={notifications.general} onChange={(checked) => toggleStoredSetting(notifications, "general", checked, "camillus-notifications", setNotifications)} />
                            </div>
                        </section>

                        <section className="dashboard-panel settings-section" id="account">
                            <div className="panel-heading"><div><span className="panel-eyebrow">Personal details</span><h2>Account</h2></div><button type="button" className="dashboard-action-button" onClick={() => { setEditingProfile(!editingProfile); setProfileMessage("") }}>{editingProfile ? "Cancel editing" : "Edit profile"}</button></div>
                            {editingProfile ? <form className="settings-profile-form" onSubmit={saveProfile}>
                                <label>Full name<input required value={draftProfile.name} onChange={(event) => setDraftProfile({ ...draftProfile, name: event.target.value })} /></label>
                                <label>Email address<input required type="email" value={draftProfile.email} onChange={(event) => setDraftProfile({ ...draftProfile, email: event.target.value })} /></label>
                                <label>Phone number<input value={draftProfile.phone} onChange={(event) => setDraftProfile({ ...draftProfile, phone: event.target.value })} /></label>
                                <label>Role<input value={draftProfile.role} onChange={(event) => setDraftProfile({ ...draftProfile, role: event.target.value })} /></label>
                                <button type="submit" className="dashboard-button">Save profile</button>
                            </form> : <div className="settings-profile-summary"><div><span>Name</span><strong>{profile.name}</strong></div><div><span>Email</span><strong>{profile.email}</strong></div><div><span>Phone</span><strong>{profile.phone}</strong></div><div><span>Role</span><strong>{profile.role}</strong></div></div>}
                            {profileMessage && <p className="settings-feedback" role="status">{profileMessage}</p>}
                        </section>

                        <section className="dashboard-panel settings-section" id="general">
                            <div className="panel-heading"><div><span className="panel-eyebrow">Regional</span><h2>General</h2></div></div>
                            <div className="settings-select-grid">
                                <label>Language<select className="dashboard-select" value={language} onChange={(event) => { setLanguage(event.target.value); window.localStorage.setItem("camillus-language", event.target.value) }}><option>English</option><option>Español</option><option>Français</option><option>हिन्दी</option></select></label>
                                <label>Date format<select className="dashboard-select" value={dateFormat} onChange={(event) => { setDateFormat(event.target.value); window.localStorage.setItem("camillus-date-format", event.target.value) }}><option>DD MMM YYYY</option><option>MM/DD/YYYY</option><option>YYYY-MM-DD</option></select></label>
                                <label>Time format<select className="dashboard-select" value={timeFormat} onChange={(event) => { setTimeFormat(event.target.value); window.localStorage.setItem("camillus-time-format", event.target.value) }}><option>12-hour</option><option>24-hour</option></select></label>
                            </div>
                            <div className="settings-option-list settings-preference-list">
                                <SettingToggle label="Weekly summary" description="Show a weekly overview on your dashboard." checked={preferences.weeklySummary} onChange={(checked) => toggleStoredSetting(preferences, "weeklySummary", checked, "camillus-preferences", setPreferences)} />
                                <SettingToggle label="Compact lists" description="Use tighter spacing in appointment and patient lists." checked={preferences.compactLists} onChange={(checked) => toggleStoredSetting(preferences, "compactLists", checked, "camillus-preferences", setPreferences)} />
                            </div>
                        </section>

                        <section className="dashboard-panel settings-section" id="security">
                            <div className="panel-heading"><div><span className="panel-eyebrow">Access</span><h2>Security</h2></div></div>
                            <div className="session-summary"><span className="session-indicator" /> <div><strong>Current session</strong><span>This device · Active now</span></div></div>
                            <form className="settings-password-form" onSubmit={changePassword}>
                                <h3>Change password</h3>
                                <label>Current password<input type="password" autoComplete="current-password" value={passwordForm.current} onChange={(event) => setPasswordForm({ ...passwordForm, current: event.target.value })} /></label>
                                <label>New password<input type="password" autoComplete="new-password" value={passwordForm.next} onChange={(event) => setPasswordForm({ ...passwordForm, next: event.target.value })} /></label>
                                <label>Confirm new password<input type="password" autoComplete="new-password" value={passwordForm.confirm} onChange={(event) => setPasswordForm({ ...passwordForm, confirm: event.target.value })} /></label>
                                {passwordMessage && <p className="settings-feedback" role="status">{passwordMessage}</p>}
                                <button type="submit" className="dashboard-button">Update password</button>
                            </form>
                            <button type="button" className="settings-logout-button" onClick={logout}>Log out</button>
                        </section>
                    </div>

                    <aside className="settings-side-column">
                        <section className="dashboard-panel settings-section" id="privacy">
                            <div className="panel-heading"><div><span className="panel-eyebrow">Your data</span><h2>Privacy</h2></div></div>
                            <div className="settings-option-list">
                                <SettingToggle label="Profile visible to care team" description="Allow authorized clinic team members to view your details." checked={privacy.profileVisibility} onChange={(checked) => toggleStoredSetting(privacy, "profileVisibility", checked, "camillus-privacy", setPrivacy)} />
                                <SettingToggle label="Share anonymous usage data" description="Help improve the Camillus experience." checked={privacy.shareUsageData} onChange={(checked) => toggleStoredSetting(privacy, "shareUsageData", checked, "camillus-privacy", setPrivacy)} />
                            </div>
                            <div className="settings-legal-links"><a href="#terms">Terms and conditions</a><a href="#privacy-notice">Privacy notice</a></div>
                            <p className="settings-disclaimer" id="terms">Review the terms that apply to your Camillus account.</p>
                            <p className="settings-disclaimer" id="privacy-notice">Learn how your account information is handled and protected.</p>
                        </section>

                        <section className="dashboard-panel settings-section" id="support">
                            <div className="panel-heading"><div><span className="panel-eyebrow">We can help</span><h2>Help &amp; Support</h2></div></div>
                            <details className="settings-faq"><summary>How do I manage appointments?</summary><p>Open your role dashboard to review, confirm, or update appointment details.</p></details>
                            <details className="settings-faq"><summary>Where are my preferences saved?</summary><p>Your preferences are saved in this browser on this device.</p></details>
                            <details className="settings-faq"><summary>Can I change my account email?</summary><p>Edit your profile details in Account. Your changes are saved in this browser.</p></details>
                            <a className="settings-support-link" href="mailto:support@camillus.example?subject=Camillus%20support">Contact support</a>
                        </section>

                        <section className="dashboard-panel settings-section about-panel" id="about">
                            <span className="panel-eyebrow">About</span><h2>Camillus</h2>
                            <p>A clinic management experience designed to keep care teams and patients connected.</p>
                            <span className="about-version">Camillus · Version 1.0</span>
                        </section>
                    </aside>
                </div>
            </div>
        </main>
    )
}

function SettingToggle({ label, description, checked, onChange }) {
    return (
        <div className="settings-option">
            <div><strong>{label}</strong><span>{description}</span></div>
            <button type="button" className={`settings-switch ${checked ? "settings-switch-on" : ""}`} role="switch" aria-checked={checked} aria-label={label} onClick={() => onChange(!checked)}><span /></button>
        </div>
    )
}

export default Settings
