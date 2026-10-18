export async function fetchHospitals() {
    const response = await fetch("/api/hospitals")

    if (!response.ok) {
        throw new Error(`Hospital request failed (${response.status})`)
    }

    const hospitals = await response.json()
    if (!Array.isArray(hospitals)) {
        throw new Error("Hospital API returned an unexpected response")
    }

    return hospitals
}

export async function fetchDepartments(hospitalId) {
    const response = await fetch(`/api/hospitals/${encodeURIComponent(hospitalId)}/departments`)

    if (!response.ok) {
        const body = await response.json().catch(() => null)
        throw new Error(body?.error || `Department request failed (${response.status})`)
    }

    const departments = await response.json()
    if (!Array.isArray(departments)) {
        throw new Error("Department API returned an unexpected response")
    }

    return departments
}
