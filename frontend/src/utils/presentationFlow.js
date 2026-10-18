export const roleOptions = [
    { value: "PATIENT", label: "Patient", dashboard: "/patient-dashboard" },
    { value: "DOCTOR", label: "Doctor", dashboard: "/doctor-dashboard" },
    { value: "RECEPTIONIST", label: "Receptionist", dashboard: "/receptionist-dashboard" },
    { value: "ADMIN", label: "Admin", dashboard: "/admin-dashboard" },
]

export function roleOption(role) {
    return roleOptions.find((option) => option.value === String(role || "").toUpperCase()) || null
}

export function hospitalSelectionPath(role, hospitalId) {
    const option = roleOption(role)
    if (!option) return "/"
    const params = new URLSearchParams({ role: option.value })
    if (hospitalId) params.set("hospitalId", String(hospitalId))
    return `/select-hospital?${params.toString()}`
}

export function dashboardPath(role, hospitalId) {
    const option = roleOption(role)
    if (!option || !hospitalId) return "/"
    const params = new URLSearchParams({ role: option.value, hospitalId: String(hospitalId) })
    return `${option.dashboard}?${params.toString()}`
}
