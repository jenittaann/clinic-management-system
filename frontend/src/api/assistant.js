import { getCsrfToken } from "./auth"

export async function askAssistant(message) {
    const token = await getCsrfToken()
    const response = await fetch("/api/assistant/chat", {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json", "X-XSRF-TOKEN": token },
        body: JSON.stringify({ message }),
    })
    const body = await response.json().catch(() => null)
    if (!response.ok) {
        const message = [body?.error, body?.detail, body?.message]
            .find((value) => typeof value === "string" && value.trim())
        throw new Error(message || `Chat request failed (${response.status})`)
    }
    return body
}
