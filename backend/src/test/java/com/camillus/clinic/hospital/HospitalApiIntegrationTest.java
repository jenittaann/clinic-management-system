package com.camillus.clinic.hospital;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HospitalApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired HospitalRepository hospitals;
    @Autowired DoctorRepository doctors;

    @Test
    void listsHospitalsWithDepartmentPublicIds() throws Exception {
        Hospital hospital = hospitals.save(new Hospital("Test Facility", "General", "Central Chennai", "Example address", 42, null));
        hospital.getDepartments().add(new Department(hospital, "Medicine", null));
        // Persist the child explicitly; the relationship is deliberately not configured to cascade.
        var department = hospital.getDepartments().getFirst();
        departments.save(department);

        mvc.perform(get("/api/hospitals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Test Facility"))
                .andExpect(jsonPath("$[0].beds").value(42))
                .andExpect(jsonPath("$[0].departmentIds.length()").value(1));
    }

    @Test
    void returnsAnEmptyListWithoutSeedData() throws Exception {
        mvc.perform(get("/api/hospitals"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void listsDepartmentsByHospitalPublicIdWithTheirPublicIdsAndNames() throws Exception {
        Hospital hospital = hospitals.save(
                new Hospital("Department API Facility", "General", "Central Chennai", "Example address", 42, null)
        );
        Department department = departments.save(
                new Department(hospital, "Cardiology", "Heart and cardiovascular care")
        );

        mvc.perform(get("/api/hospitals/{hospitalId}/departments", hospital.getPublicId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(department.getPublicId()))
                .andExpect(jsonPath("$[0].name").value("Cardiology"))
                .andExpect(jsonPath("$[0].description").value("Heart and cardiovascular care"));
    }

    @Test
    void returnsAnEmptyDepartmentListForHospitalWithoutDepartments() throws Exception {
        Hospital hospital = hospitals.save(
                new Hospital("No Departments Facility", "General", "Central Chennai", "Example address", null, null)
        );

        mvc.perform(get("/api/hospitals/{hospitalId}/departments", hospital.getPublicId()))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void returnsNotFoundForUnknownHospitalPublicId() throws Exception {
        String unknownPublicId = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee").toString();

        mvc.perform(get("/api/hospitals/{hospitalId}/departments", unknownPublicId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Hospital not found for public ID " + unknownPublicId));
    }

    @Test
    void returnsBadRequestForMalformedHospitalPublicId() throws Exception {
        mvc.perform(get("/api/hospitals/{hospitalId}/departments", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("hospitalId must be a valid UUID"));
    }

    @Test
    void listsDoctorsWithPublicDoctorDepartmentAndHospitalIds() throws Exception {
        Hospital hospital = hospitals.save(
                new Hospital("Doctor API Facility", "General", "Central Chennai", "Example address", 42, null)
        );
        Department department = departments.save(
                new Department(hospital, "Cardiology", "Heart care")
        );
        Doctor doctor = doctors.save(new Doctor(department, "Dr Example", "Cardiologist", 12));

        mvc.perform(get("/api/doctors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(doctor.getPublicId()))
                .andExpect(jsonPath("$[0].name").value("Dr Example"))
                .andExpect(jsonPath("$[0].specialization").value("Cardiologist"))
                .andExpect(jsonPath("$[0].experienceYears").value(12))
                .andExpect(jsonPath("$[0].departmentId").value(department.getPublicId()))
                .andExpect(jsonPath("$[0].departmentName").value("Cardiology"))
                .andExpect(jsonPath("$[0].hospitalId").value(hospital.getPublicId()))
                .andExpect(jsonPath("$[0].hospitalName").value("Doctor API Facility"));
    }

    @Test
    void listsDoctorsForHospitalPublicId() throws Exception {
        Hospital hospital = hospitals.save(
                new Hospital("Scoped Doctor Facility", "General", "Central Chennai", "Example address", null, null)
        );
        Department department = departments.save(new Department(hospital, "Medicine", null));
        Doctor doctor = doctors.save(new Doctor(department, "Dr Scoped", null, null));

        mvc.perform(get("/api/hospitals/{hospitalId}/doctors", hospital.getPublicId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(doctor.getPublicId()))
                .andExpect(jsonPath("$[0].name").value("Dr Scoped"))
                .andExpect(jsonPath("$[0].departmentId").value(department.getPublicId()))
                .andExpect(jsonPath("$[0].hospitalId").value(hospital.getPublicId()));
    }

    @Test
    void returnsAnEmptyDoctorListForHospitalWithoutDoctors() throws Exception {
        Hospital hospital = hospitals.save(
                new Hospital("No Doctors Facility", "General", "Central Chennai", "Example address", null, null)
        );

        mvc.perform(get("/api/hospitals/{hospitalId}/doctors", hospital.getPublicId()))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void returnsNotFoundForUnknownHospitalWhenListingDoctors() throws Exception {
        String unknownPublicId = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee";

        mvc.perform(get("/api/hospitals/{hospitalId}/doctors", unknownPublicId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Hospital not found for public ID " + unknownPublicId));
    }

    @Test
    void returnsBadRequestForMalformedHospitalIdWhenListingDoctors() throws Exception {
        mvc.perform(get("/api/hospitals/{hospitalId}/doctors", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("hospitalId must be a valid UUID"));
    }

    @Autowired DepartmentRepository departments;
}
