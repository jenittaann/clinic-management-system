const STORAGE_KEY = "camillus-selected-hospital"

export function getSelectedHospital() {
    try {
        const stored = window.localStorage.getItem(STORAGE_KEY)
        if (!stored) return null
        const hospital = JSON.parse(stored)
        if (!hospital || typeof hospital.id !== "string" || !hospital.id.trim()) return null
        return hospital
    } catch {
        return null
    }
}

export function saveSelectedHospital(hospital) {
    if (hospital?.id === undefined || hospital?.id === null || String(hospital.id).trim() === "") return null
    const selected = {
        id: String(hospital.id),
        name: hospital.name?.trim() || "Unnamed hospital",
        location: hospital.area?.trim() || hospital.address?.trim() || "Location not listed",
    }
    try {
        window.localStorage.setItem(STORAGE_KEY, JSON.stringify(selected))
    } catch {
        // The selection ID is also carried in the login URL, so storage is best effort.
    }
    return selected
}

export function clearSelectedHospital() {
    try {
        window.localStorage.removeItem(STORAGE_KEY)
    } catch {
        // Ignore unavailable browser storage.
    }
}
