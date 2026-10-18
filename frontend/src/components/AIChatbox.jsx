import { useEffect, useState } from "react"
import { getCurrentSession } from "../api/auth"
import { askAssistant } from "../api/assistant"
import "./AIChatbox.css"

const suggestions = {
    ADMIN: ["How do I manage doctor accounts?", "How do I manage hospital departments?"],
    DOCTOR: ["How do I update my schedule?", "Where can I review appointments?"],
    PATIENT: ["How do I book an appointment?", "Where can I check appointment status?"],
    RECEPTIONIST: ["How do I check in a patient?", "How can I view today's appointments?"],
}

function AIChatbox() {
    const [open, setOpen] = useState(false)
    const [role, setRole] = useState("")
    const [sessionChecked, setSessionChecked] = useState(false)
    const [messages, setMessages] = useState([])
    const [draft, setDraft] = useState("")
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState("")

    useEffect(() => {
        let active = true
        getCurrentSession()
            .then((session) => {
                if (active) setRole(session?.role || "")
            })
            .catch(() => {
                if (active) setError("Unable to verify your sign-in. Please refresh and try again.")
            })
            .finally(() => {
                if (active) setSessionChecked(true)
            })
        return () => { active = false }
    }, [])

    async function send(message = draft) {
        const text = message.trim()
        if (!text || loading) return
        setMessages((current) => [...current, { from: "you", text }])
        setDraft("")
        setError("")
        setLoading(true)
        try {
            const result = await askAssistant(text)
            setRole(result.role)
            setMessages((current) => [...current, { from: "assistant", text: result.reply }])
        } catch (requestError) {
            setError(requestError.message || "Unable to reach the assistant. Please try again.")
        } finally {
            setLoading(false)
        }
    }

    return (
        <div className="ai-chatbox">
            {open && <section className="ai-chat-panel" aria-label="CAMILLUS AI assistant">
                <header className="ai-chat-header">
                    <div><strong>CAMILLUS Assistant</strong><span>Clinic workflow help</span></div>
                    <button type="button" aria-label="Close chat" onClick={() => setOpen(false)}>×</button>
                </header>
                <div className="ai-chat-messages" aria-live="polite">
                    {messages.length === 0 && <p className="ai-chat-intro">Ask about using your dashboard. Please don&apos;t include patient or other private information.</p>}
                    {messages.map((message, index) => <p className={`ai-chat-message ai-chat-${message.from}`} key={`${index}-${message.from}`}>{message.text}</p>)}
                    {loading && <p className="ai-chat-message ai-chat-assistant" role="status">Thinking…</p>}
                    {error && <p className="ai-chat-error" role="alert">{error}</p>}
                    {!sessionChecked && <p className="ai-chat-intro" role="status">Checking sign-in…</p>}
                    {sessionChecked && !role && <p className="ai-chat-error" role="alert">Sign in to use the assistant.</p>}
                    {sessionChecked && role && messages.length === 0 && (suggestions[role] || []).map((question) =>
                        <button className="ai-chat-suggestion" type="button" key={question} onClick={() => send(question)}>{question}</button>,
                    )}
                </div>
                <form className="ai-chat-form" onSubmit={(event) => { event.preventDefault(); send() }}>
                    <input aria-label="Ask about clinic workflows" maxLength={1000} value={draft} onChange={(event) => setDraft(event.target.value)} placeholder="Ask about clinic workflows…" disabled={!role || loading} />
                    <button type="submit" disabled={!draft.trim() || !role || loading}>Send</button>
                </form>
            </section>}
            <button className="ai-chat-toggle" type="button" aria-expanded={open} onClick={() => setOpen((value) => !value)}>
                {open ? "Close assistant" : "AI Assistant"}
            </button>
        </div>
    )
}

export default AIChatbox
