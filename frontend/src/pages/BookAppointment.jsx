import { useParams } from "react-router-dom"
import { useState } from "react"
import BackButton from "../components/BackButton"

function BookAppointment() {
    const { name } = useParams()

    const doctorNames = {
        "ananya-sharma": "Dr. Ananya Sharma",
        "rahul-menon": "Dr. Rahul Menon",
        "priya-nair": "Dr. Priya Nair",
    }

    const doctorName = doctorNames[name] || "Doctor"

    const [date, setDate] = useState("")
    const [time, setTime] = useState("")
    const [reason, setReason] = useState("")
    const [booked, setBooked] = useState(false)

    function handleSubmit(event) {
        event.preventDefault()
        setBooked(true)
    }

    return (
        <div className="appointment-page">
            <BackButton className="back-button-booking" />

            <h1>Book an Appointment</h1>

            <p>
                Booking appointment with <strong>{doctorName}</strong>
            </p>

            {booked ? (
                <div className="booking-success">
                    <h2>Appointment Confirmed! ✓</h2>
                    <p>
                        Your appointment with {doctorName} has been
                        successfully booked.
                    </p>
                </div>
            ) : (
                <form onSubmit={handleSubmit} className="appointment-form">

                    <label>
                        Select Date
                    </label>

                    <input
                        type="date"
                        value={date}
                        onChange={(e) => setDate(e.target.value)}
                        required
                    />

                    <label>
                        Select Time
                    </label>

                    <select
                        value={time}
                        onChange={(e) => setTime(e.target.value)}
                        required
                    >
                        <option value="">Choose a time</option>
                        <option>10:00 AM</option>
                        <option>11:30 AM</option>
                        <option>2:00 PM</option>
                        <option>4:30 PM</option>
                    </select>

                    <label>
                        Reason for Visit
                    </label>

                    <textarea
                        placeholder="Briefly describe your reason for the visit"
                        value={reason}
                        onChange={(e) => setReason(e.target.value)}
                        required
                    />

                    <button type="submit" className="book-btn">
                        Confirm Appointment
                    </button>

                </form>
            )}

        </div>
    )
}

export default BookAppointment