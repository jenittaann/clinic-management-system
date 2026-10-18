let csrfToken

async function readResponse(response) {
    if (response.status === 204) return null
    const contentType = response.headers.get("content-type") || ""
    if (contentType.includes("application/json")) return response.json()
    return null
}

async function requestError(response) {
    const body = await readResponse(response)
    return new Error(body?.error || `Authentication request failed (${response.status})`)
}

export async function getCsrfToken() {
    const response = await fetch("/api/auth/csrf", { credentials: "include" })
    if (!response.ok) throw await requestError(response)
    const body = await response.json()
    if (!body?.token) throw new Error("Authentication service did not return a CSRF token")
    csrfToken = body.token
    return csrfToken
}

async function stateChangingRequest(url, body) {
    const token = await getCsrfToken()
    const response = await fetch(url, {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json", "X-XSRF-TOKEN": token },
        ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    })
    if (!response.ok) throw await requestError(response)
    return readResponse(response)
}

export function login(email, password) {
    return stateChangingRequest("/api/auth/login", { email, password })
}

export function emailSignIn({ email, role, hospitalId }) {
    return stateChangingRequest("/api/auth/email-sign-in", { email, role, hospitalId })
}

export function registerPatientAndSignIn({ email, hospitalId }) {
    return stateChangingRequest("/api/auth/patient-register", { email, hospitalId })
}

export function registerReceptionistAndSignIn({ email, hospitalId }) {
    return stateChangingRequest("/api/auth/receptionist-register", { email, hospitalId })
}

export function requestPasswordlessLink({ email, role, hospitalId, registration = false }) {
    return stateChangingRequest("/api/auth/passwordless/request", { email, role, hospitalId, registration })
}

export function verifyPasswordlessLink(token) {
    return stateChangingRequest("/api/auth/passwordless/verify", { token })
}

export async function getCurrentSession() {
    const response = await fetch("/api/auth/me", { credentials: "include" })
    if (response.status === 401) return null
    if (!response.ok) throw await requestError(response)
    return response.json()
}

export async function logout() {
    await stateChangingRequest("/api/auth/logout")
    csrfToken = undefined
}
