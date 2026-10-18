const STORAGE_KEY = "camillus-selected-role"
const ROLES = new Set(["PATIENT", "DOCTOR", "RECEPTIONIST", "ADMIN"])

export function getSelectedRole() {
    try {
        const role = window.localStorage.getItem(STORAGE_KEY)?.toUpperCase()
        return ROLES.has(role) ? role : null
    } catch {
        return null
    }
}

export function saveSelectedRole(role) {
    const selected = String(role || "").toUpperCase()
    if (!ROLES.has(selected)) return null
    try {
        window.localStorage.setItem(STORAGE_KEY, selected)
    } catch {
        // The role is also carried in the route query string.
    }
    return selected
}
