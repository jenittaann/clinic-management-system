import { useLocation, useParams } from "react-router-dom"
import { useState } from "react"
import BackButton from "../components/BackButton"

function BookAppointment() {
    const { name } = useParams()
    const location = useLocation()
    const bookingDetails = location.state?.bookingDetails

    const doctorNames = {
        "ananya-sharma": "Dr. Ananya Sharma",
        "rahul-menon": "Dr. Rahul Menon",
        "priya-nair": "Dr. Priya Nair",
    }

    const doctorName = bookingDetails?.doctor || doctorNames[name] || "Doctor"
    const appointmentTimes = ["10:00 AM", "11:30 AM", "2:00 PM", "4:30 PM"]
    if (bookingDetails?.time && !appointmentTimes.includes(bookingDetails.time)) {
        appointmentTimes.unshift(bookingDetails.time)
    }

    const [date, setDate] = useState(bookingDetails?.date || "")
    const [time, setTime] = useState(bookingDetails?.time || "")
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

            {bookingDetails && (
                <div className="booking-context">
                    <p><strong>Hospital:</strong> {bookingDetails.hospital}</p>
                    <p><strong>Department:</strong> {bookingDetails.department}</p>
                    <p><strong>Appointment time:</strong> {bookingDetails.day}, {bookingDetails.time}</p>
                </div>
            )}

            {booked ? (
                <div className="booking-success">
                    <h2>Appointment Confirmed! ✓</h2>
                    <p>
                        Your appointment with {doctorName} has been
                        successfully booked.
                    </p>
                    {bookingDetails && (
                        <p>
                            {bookingDetails.hospital} · {bookingDetails.department} · {bookingDetails.day}, {bookingDetails.time}
                        </p>
                    )}
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
                        {appointmentTimes.map((appointmentTime) => (
                            <option key={appointmentTime}>{appointmentTime}</option>
                        ))}
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