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
