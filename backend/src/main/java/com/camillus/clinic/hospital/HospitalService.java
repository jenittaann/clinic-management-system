package com.camillus.clinic.hospital;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class HospitalService {
    private final HospitalRepository hospitals;
    private final DepartmentRepository departments;

    public HospitalService(HospitalRepository hospitals, DepartmentRepository departments) {
        this.hospitals = hospitals;
        this.departments = departments;
    }

    @Transactional(readOnly = true)
    public List<HospitalResponse> listHospitals() {
        return hospitals.findAllByOrderByNameAsc().stream().map(HospitalResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<DepartmentResponse> listDepartments(UUID hospitalPublicId) {
        String publicId = hospitalPublicId.toString();
        if (hospitals.findByPublicId(publicId).isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Hospital not found for public ID " + publicId
            );
        }

        return departments.findAllByHospital_PublicIdOrderByNameAsc(publicId)
                .stream()
                .map(DepartmentResponse::from)
                .toList();
    }
}
