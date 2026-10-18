package com.camillus.clinic.hospital;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
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

    @Transactional
    public DepartmentResponse createDepartment(UUID hospitalPublicId, DepartmentCreateRequest request) {
        String publicId = hospitalPublicId.toString();
        Hospital hospital = hospitals.findByPublicId(publicId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Hospital not found for public ID " + publicId));
        String name = request.name().trim();
        if (departments.existsByHospital_PublicIdIgnoreCaseAndNameIgnoreCase(publicId, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Department already exists for this hospital");
        }

        try {
            Department saved = departments.saveAndFlush(new Department(hospital, name, request.description()));
            return DepartmentResponse.from(saved);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Department already exists for this hospital");
        }
    }
}
