package com.camillus.clinic.hospital;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;

@Service
public class DoctorService {
    private final DoctorRepository doctors;
    private final HospitalRepository hospitals;

    public DoctorService(DoctorRepository doctors, HospitalRepository hospitals) {
        this.doctors = doctors;
        this.hospitals = hospitals;
    }

    @Transactional(readOnly = true)
    public List<DoctorResponse> listDoctors() {
        return doctors.findAllByOrderByNameAsc().stream()
                .map(DoctorResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DoctorResponse> listDoctorsForHospital(UUID hospitalPublicId) {
        String publicId = hospitalPublicId.toString();
        if (hospitals.findByPublicId(publicId).isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Hospital not found for public ID " + publicId
            );
        }

        return doctors.findAllByDepartment_Hospital_PublicIdOrderByNameAsc(publicId).stream()
                .map(DoctorResponse::from)
                .toList();
    }
}
