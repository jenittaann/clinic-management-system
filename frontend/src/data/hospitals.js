export const hospitals = [
    {
        id: "apollo-greams-road",
        name: "Apollo Hospitals",
        type: "Multi-specialty",
        area: "Greams Road, Chennai",
        address: "21, Greams Lane, Off Greams Road, Chennai",
        beds: 560,
        description: "A multi-specialty hospital providing advanced care across a broad range of medical disciplines.",
        departmentIds: ["apollo-cardiology", "apollo-neurology", "apollo-medicine", "apollo-orthopedics", "apollo-pediatrics", "apollo-dermatology"],
    },
    {
        id: "kauvery-alwarpet",
        name: "Kauvery Hospital",
        type: "Multi-specialty",
        area: "Alwarpet, Chennai",
        address: "No. 199, Luz Church Road, Mylapore, Chennai",
        beds: 250,
        description: "A Chennai-based multi-specialty hospital with teams across medical and surgical specialties.",
        departmentIds: ["kauvery-cardiology", "kauvery-neurology", "kauvery-medicine", "kauvery-orthopedics", "kauvery-pediatrics", "kauvery-dermatology"],
    },
    {
        id: "miot-manapakkam",
        name: "MIOT International",
        type: "Tertiary care",
        area: "Manapakkam, Chennai",
        address: "4/112, Mount Poonamallee Road, Manapakkam, Chennai",
        beds: 1000,
        description: "A tertiary-care centre offering specialist consultations and comprehensive hospital services.",
        departmentIds: ["miot-cardiology", "miot-neurology", "miot-medicine", "miot-orthopedics", "miot-pediatrics", "miot-dermatology"],
    },
    {
        id: "fortis-malar-adyar",
        name: "Fortis Malar Hospital",
        type: "Multi-specialty",
        area: "Adyar, Chennai",
        address: "No. 52, 1st Main Road, Gandhi Nagar, Adyar, Chennai",
        beds: 180,
        description: "A multi-specialty hospital serving patients in Adyar and surrounding parts of Chennai.",
        departmentIds: ["malar-cardiology", "malar-neurology", "malar-medicine", "malar-orthopedics", "malar-pediatrics", "malar-dermatology"],
    },
]

const departmentNames = [
    ["cardiology", "Cardiology", "Heart and cardiovascular care"],
    ["neurology", "Neurology", "Care for conditions of the brain and nervous system"],
    ["medicine", "General Medicine", "Primary and adult medical care"],
    ["orthopedics", "Orthopedics", "Bone, joint and musculoskeletal care"],
    ["pediatrics", "Pediatrics", "Medical care for infants, children and adolescents"],
    ["dermatology", "Dermatology", "Skin, hair and nail care"],
]

const hospitalPrefixes = ["apollo", "kauvery", "miot", "malar"]
const doctorProfiles = [
    ["Ananya Sharma", "Cardiologist", 14],
    ["Vikram Iyer", "Neurologist", 12],
    ["Meera Krishnan", "General Physician", 11],
    ["Arjun Reddy", "Orthopedic Surgeon", 16],
    ["Priya Nair", "Pediatrician", 10],
    ["Kavitha Subramanian", "Dermatologist", 9],
]

export const departments = hospitalPrefixes.flatMap((prefix) =>
    departmentNames.map(([key, name, description]) => ({
        id: `${prefix}-${key}`,
        hospitalId: hospitals.find((hospital) => hospital.departmentIds[0].startsWith(prefix)).id,
        name,
        description,
    })),
)

export const doctors = hospitals.flatMap((hospital, hospitalIndex) =>
    hospital.departmentIds.map((departmentId, departmentIndex) => {
        const [name, specialization, experienceYears] = doctorProfiles[departmentIndex]
        const suffix = hospitalIndex === 0 ? "" : ` ${["", "II", "III", "IV"][hospitalIndex]}`

        return {
            id: `${hospital.id}-doctor-${departmentIndex + 1}`,
            hospitalId: hospital.id,
            departmentId,
            name: `Dr. ${name}${suffix}`,
            specialization,
            experienceYears,
        }
    }),
)

export const availabilitySlots = doctors.flatMap((doctor) => [
    { id: `${doctor.id}-mon-1000`, doctorId: doctor.id, day: "Monday", dayOfWeek: 1, time: "10:00 AM" },
    { id: `${doctor.id}-mon-1030`, doctorId: doctor.id, day: "Monday", dayOfWeek: 1, time: "10:30 AM" },
    { id: `${doctor.id}-mon-1100`, doctorId: doctor.id, day: "Monday", dayOfWeek: 1, time: "11:00 AM" },
    { id: `${doctor.id}-tue-1400`, doctorId: doctor.id, day: "Tuesday", dayOfWeek: 2, time: "2:00 PM" },
    { id: `${doctor.id}-tue-1430`, doctorId: doctor.id, day: "Tuesday", dayOfWeek: 2, time: "2:30 PM" },
    { id: `${doctor.id}-tue-1500`, doctorId: doctor.id, day: "Tuesday", dayOfWeek: 2, time: "3:00 PM" },
])

export function getNextAppointmentDate(dayOfWeek) {
    const date = new Date()
    const daysUntil = (dayOfWeek - date.getDay() + 7) % 7 || 7
    date.setDate(date.getDate() + daysUntil)

    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, "0")
    const day = String(date.getDate()).padStart(2, "0")
    return `${year}-${month}-${day}`
}
