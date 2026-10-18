import { getCsrfToken } from "./auth"

async function responseBody(response) {
    const body = await response.json().catch(() => null)
    if (!response.ok) throw new Error(body?.error || `Request failed (${response.status})`)
    return body
}

export async function getClinicData(path) {
    return responseBody(await fetch(path, { credentials: "include" }))
}

export async function changeClinicData(path, method, body) {
    const token = await getCsrfToken()
    return responseBody(await fetch(path, {
        method,
        credentials: "include",
        headers: { "Content-Type": "application/json", "X-XSRF-TOKEN": token },
        body: JSON.stringify(body),
    }))
}

export const getMyPatientProfile = () => getClinicData("/api/patients/me")
export const updateMyPatientProfile = (profile) => changeClinicData("/api/patients/me", "PUT", profile)
export const getMyAppointments = () => getClinicData("/api/appointments/me")
export const bookAppointment = (request) => changeClinicData("/api/appointments", "POST", request)
export const cancelAppointment = (id) => changeClinicData(`/api/appointments/${encodeURIComponent(id)}/cancel`, "POST", {})
export const rescheduleAppointment = (id, request) => changeClinicData(`/api/appointments/${encodeURIComponent(id)}/reschedule`, "POST", request)
export const getPatients = () => getClinicData("/api/patients?page=0&size=100")
export const getDoctorPatients = () => getClinicData("/api/patients/for-doctor?page=0&size=100")
export const getMyDoctor = () => getClinicData("/api/doctors/me")
export const updateMyDoctor = (profile) => changeClinicData("/api/doctors/me", "PUT", profile)
export const getDoctorSchedule = (doctorId, date) => getClinicData(
    `/api/doctors/${encodeURIComponent(doctorId)}/availability${date ? `?date=${encodeURIComponent(date)}` : ""}`,
)
export const setDoctorAppointmentDuration = (doctorId, durationMinutes) => changeClinicData(
    `/api/doctors/${encodeURIComponent(doctorId)}/appointment-duration`, "PUT", { durationMinutes },
)
export const createDoctorAvailability = (doctorId, rule) => changeClinicData(
    `/api/doctors/${encodeURIComponent(doctorId)}/availability`, "POST", rule,
)
export const updateDoctorAvailability = (doctorId, availabilityId, rule) => changeClinicData(
    `/api/doctors/${encodeURIComponent(doctorId)}/availability/${encodeURIComponent(availabilityId)}`, "PUT", rule,
)
export const deleteDoctorAvailability = (doctorId, availabilityId) => changeClinicData(
    `/api/doctors/${encodeURIComponent(doctorId)}/availability/${encodeURIComponent(availabilityId)}`, "DELETE", {},
)
export const getDoctors = (hospitalId) => getClinicData(hospitalId
    ? `/api/hospitals/${encodeURIComponent(hospitalId)}/doctors`
    : "/api/doctors")
export const createDoctor = (request) => changeClinicData("/api/admin/doctors", "POST", request)
export const createDepartment = (hospitalId, request) => changeClinicData(
    `/api/hospitals/${encodeURIComponent(hospitalId)}/departments`, "POST", request,
)
export const linkDoctorAccount = (doctorId, userId) => changeClinicData(
    `/api/admin/doctors/${encodeURIComponent(doctorId)}/account/${encodeURIComponent(userId)}`,
    "PUT",
    {},
)
export const createAndLinkDoctorAccount = (doctorId, email) => changeClinicData(
    `/api/admin/doctors/${encodeURIComponent(doctorId)}/account`,
    "POST",
    { email },
)
export const getUnlinkedDoctorAccounts = () => getClinicData("/api/admin/doctor-accounts/unlinked")
export const getDoctorAppointments = (doctorId, serviceDate) => getClinicData(
    `/api/doctors/${encodeURIComponent(doctorId)}/queue/appointments?serviceDate=${encodeURIComponent(serviceDate)}`,
)
