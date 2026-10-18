import { useEffect, useState } from "react"
import { Link, useLocation, useParams, useSearchParams } from "react-router-dom"
import BackButton from "../components/BackButton"
import { bookAppointment, getDoctorSchedule, getDoctors, rescheduleAppointment } from "../api/clinic"
import { explainIdentityError } from "../utils/authBoundary"
import AccountAccess from "../components/AccountAccess"

function slug(name) {
    return name.toLowerCase().replace("dr. ", "").replaceAll(" ", "-")
}

function BookAppointment() {
    const { name } = useParams()
    const location = useLocation()
    const [searchParams] = useSearchParams()
    const rescheduleId = searchParams.get("rescheduleId")
    const [bookingDetails, setBookingDetails] = useState(location.state?.bookingDetails || null)
    const [loading, setLoading] = useState(!location.state?.bookingDetails && !rescheduleId)
    const [scheduleResult, setScheduleResult] = useState(null)
    const [dateScheduleResult, setDateScheduleResult] = useState(null)
    const [slotSelection, setSlotSelection] = useState({
        doctorId: location.state?.bookingDetails?.doctorId || "",
        date: location.state?.selectedSlot?.date || "",
        time: location.state?.selectedSlot?.startTime?.slice(0, 5) || "",
    })
    const [reason, setReason] = useState("")
    const [saved, setSaved] = useState(false)
    const [saving, setSaving] = useState(false)
    const [error, setError] = useState("")

    useEffect(() => {
        if (bookingDetails || rescheduleId) return undefined
        let active = true
        getDoctors()
            .then((items) => {
                if (!active) return
                const doctor = items.find((item) => slug(item.name) === name)
                if (doctor) setBookingDetails({ doctorId: doctor.id, doctor: doctor.name, hospital: doctor.hospitalName, hospitalId: doctor.hospitalId, department: doctor.departmentName })
                else setError("Doctor not found. Return to the doctor list and choose an available doctor.")
            })
            .catch((requestError) => active && setError(requestError.message || "Unable to load doctor details."))
            .finally(() => active && setLoading(false))
        return () => { active = false }
    }, [bookingDetails, name, rescheduleId])

    useEffect(() => {
        if (rescheduleId || !bookingDetails?.doctorId) return undefined
        let active = true
        getDoctorSchedule(bookingDetails.doctorId)
            .then((schedule) => active && setScheduleResult({ doctorId: bookingDetails.doctorId, schedule }))
            .catch((requestError) => {
                if (!active) return
                setError(requestError.message || "Unable to load available appointments.")
                setScheduleResult({ doctorId: bookingDetails.doctorId, schedule: null })
            })
        return () => { active = false }
    }, [bookingDetails?.doctorId, rescheduleId])

    const currentScheduleResult = scheduleResult?.doctorId === bookingDetails?.doctorId ? scheduleResult : null
    const matchesCurrentDoctor = rescheduleId || slotSelection.doctorId === bookingDetails?.doctorId
    const date = matchesCurrentDoctor ? slotSelection.date : ""
    const time = matchesCurrentDoctor ? slotSelection.time : ""
    const schedule = currentScheduleResult?.schedule || null
    const scheduleLoading = Boolean(bookingDetails?.doctorId && !rescheduleId && !currentScheduleResult)
    const availableSlots = schedule?.availableSlots || []
    const availableDates = [...new Set(availableSlots.map((slot) => slot.date))]
    const selectedDate = availableDates.includes(date) ? date : availableDates[0] || ""
    const currentDateSchedule = dateScheduleResult?.doctorId === bookingDetails?.doctorId &&
        dateScheduleResult?.date === selectedDate ? dateScheduleResult : null

    useEffect(() => {
        if (rescheduleId || !schedule || !bookingDetails?.doctorId || !selectedDate) return undefined
        let active = true
        getDoctorSchedule(bookingDetails.doctorId, selectedDate)
            .then((daySchedule) => active && setDateScheduleResult({
                doctorId: bookingDetails.doctorId,
                date: selectedDate,
                schedule: daySchedule,
            }))
            .catch((requestError) => active && setDateScheduleResult({
                doctorId: bookingDetails.doctorId,
                date: selectedDate,
                error: requestError.message || "Unable to refresh available times for this date.",
            }))
        return () => { active = false }
    }, [bookingDetails?.doctorId, rescheduleId, schedule, selectedDate])

    const slotsForDate = currentDateSchedule?.schedule?.availableSlots || []
    const slotsLoading = Boolean(!rescheduleId && schedule && selectedDate && !currentDateSchedule)
    const selectedTime = slotsForDate.some((slot) => slot.startTime.slice(0, 5) === time) ? time : ""

    async function handleSubmit(event) {
        event.preventDefault()
        setError("")
        setSaving(true)
        try {
            if (rescheduleId) await rescheduleAppointment(rescheduleId, { startsAt: `${date}T${time}:00` })
            else {
                if (!bookingDetails?.doctorId) throw new Error("Select a doctor from the current hospital before booking.")
                const selectedSlot = slotsForDate.find((slot) => slot.date === selectedDate &&
                    slot.startTime.slice(0, 5) === selectedTime)
                if (!selectedSlot) throw new Error("That time is no longer available. Choose another slot.")
                await bookAppointment({
                    doctorId: bookingDetails.doctorId,
                    startsAt: `${selectedSlot.date}T${selectedSlot.startTime}`,
                    reason: reason.trim(),
                })
            }
            setSaved(true)
        } catch (requestError) {
            setError(explainIdentityError(requestError, rescheduleId ? "authorized account" : "patient", "Unable to save this appointment."))
        } finally {
            setSaving(false)
        }
    }

    if (loading) return <div className="appointment-page"><p role="status">Loading doctor details…</p></div>

    return (
        <div className="appointment-page">
            <BackButton className="back-button-booking" />
            <AccountAccess role="PATIENT" />
            <h1>{rescheduleId ? "Reschedule Appointment" : "Book an Appointment"}</h1>
            {!rescheduleId && bookingDetails && <>
                <p>Booking appointment with <strong>{bookingDetails.doctor}</strong></p>
                <div className="booking-context">
                    <p><strong>Hospital:</strong> {bookingDetails.hospital}</p>
                    <p><strong>Department:</strong> {bookingDetails.department}</p>
                </div>
            </>}
            {saved ? <div className="booking-success" role="status">
                <h2>{rescheduleId ? "Appointment Updated" : "Appointment Confirmed"}</h2>
                <p>{rescheduleId ? "Your appointment time was updated." : "Your appointment was saved successfully."}</p>
                <Link to="/patient-dashboard">Return to patient dashboard</Link>
            </div> : <form onSubmit={handleSubmit} className="appointment-form">
                {rescheduleId ? <>
                    <label htmlFor="appointment-date">Select Date</label>
                    <input id="appointment-date" type="date" value={date} onChange={(event) => setSlotSelection((current) => ({ ...current, date: event.target.value }))} required />
                    <label htmlFor="appointment-time">Select Time</label>
                    <input id="appointment-time" type="time" value={time} onChange={(event) => setSlotSelection((current) => ({ ...current, time: event.target.value }))} required />
                </> : <>
                    {scheduleLoading && <p role="status">Loading available appointments…</p>}
                    {!scheduleLoading && schedule && availableSlots.length === 0 &&
                        <p className="empty-state">No available appointment times in the next 7 days.</p>}
                    <label htmlFor="appointment-date">Select Date</label>
                    <select id="appointment-date" value={selectedDate} onChange={(event) => {
                        setSlotSelection({ doctorId: bookingDetails?.doctorId || "", date: event.target.value, time: "" })
                    }} required disabled={scheduleLoading || availableDates.length === 0}>
                        <option value="">Select a date</option>
                        {availableDates.map((availableDate) => <option key={availableDate} value={availableDate}>{availableDate}</option>)}
                    </select>
                    <label htmlFor="appointment-time">Select Time</label>
                    <select id="appointment-time" value={selectedTime} onChange={(event) => setSlotSelection({
                        doctorId: bookingDetails?.doctorId || "",
                        date: selectedDate,
                        time: event.target.value,
                    })}
                        required disabled={scheduleLoading || slotsLoading || !currentDateSchedule || slotsForDate.length === 0}>
                        <option value="">Select a time</option>
                        {slotsForDate.map((slot) => <option key={`${slot.date}-${slot.startTime}`} value={slot.startTime.slice(0, 5)}>
                            {slot.startTime.slice(0, 5)} – {slot.endTime.slice(0, 5)}
                        </option>)}
                    </select>
                    {slotsLoading && <p role="status">Refreshing available times…</p>}
                    {currentDateSchedule?.error && <p className="login-error" role="alert">{currentDateSchedule.error}</p>}
                    {currentDateSchedule && !currentDateSchedule.error && slotsForDate.length === 0 &&
                        <p className="empty-state">No appointment times remain for this date. Choose another date.</p>}
                </>}
                {!rescheduleId && <>
                    <label htmlFor="appointment-reason">Reason for Visit</label>
                    <textarea id="appointment-reason" maxLength={500} placeholder="Briefly describe your reason for the visit" value={reason} onChange={(event) => setReason(event.target.value)} required />
                </>}
                {error && <p className="login-error" role="alert">{error}</p>}
                <button type="submit" className="book-btn" disabled={saving || (!rescheduleId && (scheduleLoading || slotsLoading || !selectedTime))}>
                    {saving ? "Saving…" : rescheduleId ? "Save new time" : "Confirm Appointment"}
                </button>
            </form>}
        </div>
    )
}

export default BookAppointment
