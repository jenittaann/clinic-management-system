import { useNavigate } from "react-router-dom"

function BackButton({ className = "" }) {
    const navigate = useNavigate()

    return (
        <button type="button" className={`back-button ${className}`.trim()} onClick={() => navigate(-1)}>
            <span aria-hidden="true">&larr;</span>
            Back
        </button>
    )
}

export default BackButton